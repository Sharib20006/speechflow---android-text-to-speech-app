package org.evvdroid

import android.util.Log
import java.io.File

/**
 * The pronunciation dictionaries people write for this engine.
 *
 * The engine has a loader of its own, and it does not take these: eciLoadDict
 * answers eciDictAccessError for every one of them, because what it reads is
 * IBM's own saved form and what people share is text. So the text is read here
 * and the engine is taught a word at a time, which is the same interface the
 * loader would have used and does not depend on agreeing about a file format.
 *
 * A line is a key, a tab, and what to say instead. What to say is either a
 * respelling ("dee oe jeigh") or a pronunciation in the engine's own alphabet
 * ("`[Ekspoz1e]"), and the engine takes both. The bytes are the engine's own
 * code set already -- an e-acute is one byte -- so the file is read as Latin-1
 * rather than as UTF-8, which is what the files in the wild actually are.
 */
/**
 * A dictionary entry mapping a [key] to its spoken pronunciation/replacement [say],
 * with an optional [caseSensitive] flag.
 */
data class DictEntry(
	val key: String,
	val say: String,
	val caseSensitive: Boolean = false
)

object Dictionaries {

	/** Teaches the engine every entry in [file]. Answers how many went in. */
	fun load(engine: EvvEngine, volume: Int, file: File): Int {
		return loadWithCaseSensitive(engine, volume, file).first
	}

	/**
	 * Single-pass dictionary loader:
	 * Teaches case-insensitive words to native [engine] and extracts
	 * cached case-sensitive regex entries in a single pass over [file].
	 */
	fun loadWithCaseSensitive(
		engine: EvvEngine,
		volume: Int,
		file: File
	): Pair<Int, List<CachedCaseSensitiveEntry>> {
		val csList = mutableListOf<CachedCaseSensitiveEntry>()
		if (!file.exists() || file.length() == 0L) {
			return Pair(0, csList)
		}
		var taught = 0
		var refused = 0
		try {
			file.forEachLine(Charsets.ISO_8859_1) { line ->
				val split = line.indexOf('\t')
				if (split > 0) {
					val key = line.substring(0, split).trim()
					val rest = line.substring(split + 1).trim()
					val tab2 = rest.indexOf('\t')
					val say = (if (tab2 >= 0) rest.substring(0, tab2) else rest).trim()
					val flag = if (tab2 >= 0) rest.substring(tab2 + 1).trim() else ""
					val isCaseSensitive = flag == "1" || flag.equals("true", ignoreCase = true) || flag.equals("cs", ignoreCase = true)
					if (key.isNotEmpty() && say.isNotEmpty()) {
						if (!isCaseSensitive) {
							if (engine.teachWord(volume, key, say) == 0) taught++ else refused++
						} else {
							val cached = createCachedCaseSensitiveEntry(DictEntry(key, say, true))
							if (cached != null) {
								csList.add(cached)
							}
							taught++
						}
					}
				}
			}
		} catch (unreadable: Exception) {
			Log.e(TAG, "cannot read ${file.name}", unreadable)
			return Pair(taught, csList)
		}
		if (refused > 0) Log.e(TAG, "${file.name}: $refused of ${taught + refused} entries refused")
		return Pair(taught, csList)
	}

	data class CachedCaseSensitiveEntry(val key: String, val regex: Regex, val replacement: String)

	fun createCachedCaseSensitiveEntry(entry: DictEntry): CachedCaseSensitiveEntry? {
		if (entry.key.isEmpty() || entry.say.isEmpty() || !entry.caseSensitive) return null
		val prefix = if (entry.key.first().isLetterOrDigit() || entry.key.first() == '_') "(?<!\\w)" else ""
		val suffix = if (entry.key.last().isLetterOrDigit() || entry.key.last() == '_') "(?!\\w)" else ""
		val regex = Regex("$prefix${Regex.escape(entry.key)}$suffix")
		return CachedCaseSensitiveEntry(entry.key, regex, entry.say)
	}

	fun applyCaseSensitiveDict(text: String, entries: List<CachedCaseSensitiveEntry>): String {
		if (text.isEmpty() || entries.isEmpty()) return text
		var result = text
		for (entry in entries) {
			if (result.contains(entry.key)) {
				result = entry.regex.replace(result) { entry.replacement }
			}
		}
		return result
	}

	private val countCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Int>>()

	/** How many entries a file offers, for saying so on the settings screen
	 *  without an engine to hand. */
	fun count(file: File): Int {
		if (!file.exists()) return 0
		val key = file.absolutePath
		val lastMod = file.lastModified()
		val cached = countCache[key]
		if (cached != null && cached.first == lastMod) {
			return cached.second
		}
		val c = try {
			file.useLines(Charsets.ISO_8859_1) { lines -> lines.count { it.indexOf('\t') > 0 } }
		} catch (unreadable: Exception) {
			0
		}
		countCache[key] = lastMod to c
		return c
	}

	/** Reads all dictionary entries from [file], preserving case sensitivity. */
	fun readDictEntries(file: File): List<DictEntry> {
		if (!file.exists()) return emptyList()
		val entries = mutableListOf<DictEntry>()
		try {
			file.forEachLine(Charsets.ISO_8859_1) { line ->
				val parts = line.split('\t')
				if (parts.size >= 2) {
					val key = parts[0].trim()
					val say = parts[1].trim()
					val cs = if (parts.size >= 3) {
						val flag = parts[2].trim()
						flag == "1" || flag.equals("true", ignoreCase = true) || flag.equals("cs", ignoreCase = true)
					} else {
						false
					}
					if (key.isNotEmpty()) {
						entries.add(DictEntry(key, say, cs))
					}
				}
			}
		} catch (unreadable: Exception) {
			Log.e(TAG, "cannot read entries from ${file.name}", unreadable)
		}
		return entries
	}

	/** Reads all key-value entries from [file] in order (backward compatible). */
	fun readEntries(file: File): List<Pair<String, String>> {
		return readDictEntries(file).map { it.key to it.say }
	}

	/** Writes list of dictionary entries to [file] encoded as ISO-8859-1. */
	fun writeDictEntries(file: File, entries: List<DictEntry>) {
		try {
			file.parentFile?.mkdirs()
			file.bufferedWriter(Charsets.ISO_8859_1).use { writer ->
				for (entry in entries) {
					if (entry.caseSensitive) {
						writer.write("${entry.key}\t${entry.say}\t1\r\n")
					} else {
						writer.write("${entry.key}\t${entry.say}\r\n")
					}
				}
			}
		} catch (unwritable: Exception) {
			Log.e(TAG, "cannot write entries to ${file.name}", unwritable)
		}
	}

	/** Writes list of key-value entries to [file] encoded as ISO-8859-1 (backward compatible). */
	fun writeEntries(file: File, entries: List<Pair<String, String>>) {
		writeDictEntries(file, entries.map { DictEntry(it.first, it.second, false) })
	}

	/** Adds or updates an entry in [file], returning the updated list of DictEntry. */
	fun addOrUpdateDictEntry(file: File, key: String, say: String, caseSensitive: Boolean = false): List<DictEntry> {
		val list = readDictEntries(file).toMutableList()
		val index = list.indexOfFirst {
			if (caseSensitive || it.caseSensitive) {
				it.key == key
			} else {
				it.key.equals(key, ignoreCase = true)
			}
		}
		val newEntry = DictEntry(key, say, caseSensitive)
		if (index >= 0) {
			list[index] = newEntry
		} else {
			list.add(0, newEntry) // Add at top for easy visibility
		}
		writeDictEntries(file, list)
		return list
	}

	/** Adds or updates an entry in [file] (backward compatible overload). */
	fun addOrUpdateEntry(file: File, key: String, say: String, caseSensitive: Boolean = false): List<Pair<String, String>> {
		return addOrUpdateDictEntry(file, key, say, caseSensitive).map { it.key to it.say }
	}

	/** Deletes an entry matching [key] (and optionally [caseSensitive]) from [file]. */
	fun deleteDictEntry(file: File, key: String, caseSensitive: Boolean? = null): List<DictEntry> {
		val list = readDictEntries(file).filterNot {
			if (caseSensitive != null) {
				it.key == key && it.caseSensitive == caseSensitive
			} else if (it.caseSensitive) {
				it.key == key
			} else {
				it.key.equals(key, ignoreCase = true)
			}
		}
		writeDictEntries(file, list)
		return list
	}

	/** Deletes an entry matching [key] from [file] (backward compatible overload). */
	fun deleteEntry(file: File, key: String): List<Pair<String, String>> {
		return deleteDictEntry(file, key).map { it.key to it.say }
	}

	private fun extractAssetDictionary(context: android.content.Context, baseName: String): File {
		val dir = File(context.applicationContext.filesDir, "dictionaries").apply { mkdirs() }
		val file = File(dir, baseName)
		if (!file.exists() || file.length() == 0L) {
			try {
				val gzAsset = "dictionaries/$baseName.gz"
				val rawAsset = "dictionaries/$baseName"
				val stream: java.io.InputStream = try {
					java.util.zip.GZIPInputStream(context.assets.open(gzAsset))
				} catch (_: Exception) {
					context.assets.open(rawAsset)
				}
				stream.use { input ->
					file.outputStream().use { output -> input.copyTo(output) }
				}
			} catch (e: Exception) {
				Log.e(TAG, "Failed to extract dictionary asset: $baseName", e)
			}
		}
		return file
	}

	/** Extracts the bundled community main dictionary asset to disk if needed and returns the File. */
	fun getCommunityMainDictionaryFile(context: android.content.Context): File {
		return extractAssetDictionary(context, "community_main.dic")
	}

	/** Extracts the bundled community root dictionary asset to disk if needed and returns the File. */
	fun getCommunityRootDictionaryFile(context: android.content.Context): File {
		return extractAssetDictionary(context, "community_root.dic")
	}

	private const val TAG = "evvdroid"
}

