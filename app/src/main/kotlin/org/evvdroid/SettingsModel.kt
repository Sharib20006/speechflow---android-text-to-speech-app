package org.evvdroid

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.evvdroid.update.DownloadProgress
import org.evvdroid.update.UpdateCheckResult
import org.evvdroid.update.UpdateManager
import java.io.File

/**
 * What the settings screen is showing, and the engine it can hear itself on.
 *
 * The screen edits engine numbers and shows percentages, and this is where the
 * two meet. Everything written here goes straight into [Settings], which is
 * what the speech service reads, so a change is in force before the sample
 * that demonstrates it has finished.
 */
class SettingsModel(context: Context) {

	private val app = context.applicationContext
	private val settings = Settings(app)
	private val mainHandler = Handler(Looper.getMainLooper())
	private val preview: EvvPreview? =
		EvvEngine.available.firstOrNull()?.let { EvvPreview(it) }

	var voice by mutableStateOf(settings.voice)
		private set

	var abbreviations by mutableStateOf(settings.abbreviations)
		private set

	var sampleRateHz by mutableStateOf(settings.sampleRateHz)
		private set

	var pauses by mutableStateOf(settings.pauses)
		private set

	var phrasePrediction by mutableStateOf(settings.phrasePrediction)
		private set

	var debounceKeyEcho by mutableStateOf(settings.debounceKeyEcho)
		private set

	var themeMode by mutableStateOf(settings.themeMode)
		private set

	var splitCamelCase by mutableStateOf(settings.splitCamelCase)
		private set

	var countRepeatedChars by mutableStateOf(settings.countRepeatedChars)
		private set

	var enableBackquoteVoiceTags by mutableStateOf(settings.enableBackquoteVoiceTags)
		private set

	var readEmoji by mutableStateOf(settings.readEmoji)
		private set

	var readPunctuation by mutableStateOf(settings.readPunctuation)
		private set

	var punctuationLevel by mutableStateOf(settings.punctuationLevel)
		private set

	var processNumbers by mutableStateOf(settings.processNumbers)
		private set

	var numberMode by mutableStateOf(settings.numberMode)
		private set

	var forceSpeechRate by mutableStateOf(settings.forceSpeechRate)
		private set

	var forcePitch by mutableStateOf(settings.forcePitch)
		private set

	var forceLanguage by mutableStateOf(settings.forceLanguage)
		private set

	var forcedLanguage by mutableStateOf(settings.forcedLanguage)
		private set

	var skipSymbols by mutableStateOf(settings.skipSymbols)
		private set

	var skipSymbolsList by mutableStateOf(settings.skipSymbolsList)
		private set

	var useCommunityDictionary by mutableStateOf(settings.useCommunityDictionary)
		private set

	var isProcessingCommunityDictionary by mutableStateOf(false)

	var useLanguageDictionaries by mutableStateOf(settings.useLanguageDictionaries)
		private set

	var enableLogging by mutableStateOf(settings.enableLogging)
		private set

	var managingVolume: Int? by mutableStateOf(null)

	var addingWordVolume: Int? by mutableStateOf(null)

	var managingLangDict: Int? by mutableStateOf(null)

	var addingLangWord: Int? by mutableStateOf(null)

	var firstRunDismissed by mutableStateOf(settings.firstRunDismissed)
		private set

	var showAboutDialog by mutableStateOf(false)

	var showDictionarySettings by mutableStateOf(false)

	var showSpeechSettings by mutableStateOf(false)

	var showTextProcessingSettings by mutableStateOf(false)

	var showDefaultTtsDialog by mutableStateOf(false)
	var showAddSkipSymbolDialog by mutableStateOf(false)
	var showManageSkipSymbolsDialog by mutableStateOf(false)

	var checkForUpdatesOnStartup by mutableStateOf(settings.checkForUpdatesOnStartup)
		private set

	var isCheckingForUpdates by mutableStateOf(false)
	var availableUpdate: UpdateCheckResult? by mutableStateOf(null)
	var updateCheckMessage: String? by mutableStateOf(null)
	var isUpdateDialogOpen by mutableStateOf(false)
	var downloadProgress: DownloadProgress? by mutableStateOf(null)
	var isDownloadDialogOpen by mutableStateOf(false)
	var isPostDownloadDialogOpen by mutableStateOf(false)
	var downloadedApkFile: File? by mutableStateOf(null)
	var showUnknownSourcesPrompt by mutableStateOf(false)

	private val shape = mutableStateMapOf<Int, Int>().apply { putAll(settings.shape(settings.voice)) }

	private var dictionaries: Map<Int, String> by mutableStateOf(
		Eci.DICT_VOLUMES.mapNotNull { volume ->
			settings.dictionaryName(volume)?.let { volume to it }
		}.toMap()
	)

	private var langDictionaries: Map<Int, String> by mutableStateOf(
		EvvEngine.available.toList().mapNotNull { lang ->
			settings.langDictName(lang)?.let { lang to it }
		}.toMap()
	)

	val gender: Int get() = shape[Eci.VOICE_GENDER] ?: 0

	init {
		preview?.open {
			shape.clear()
			shape.putAll(settings.shape(settings.voice))
		}
	}

	fun stopSpeaking() {
		preview?.stopSpeaking()
	}

	fun close() {
		preview?.close()
	}

	fun percentOf(param: Int): Int = Eci.toPercent(param, shape[param] ?: 0)

	// ---- what the screen changes -----------------------------------------

	fun chooseVoice(which: Int) {
		voice = which
		settings.voice = which
		shape.clear()
		shape.putAll(settings.shape(which))
	}

	fun setPercent(param: Int, percent: Int) = setShape(param, Eci.fromPercent(param, percent))

	fun setShape(param: Int, value: Int) {
		val settled = Eci.clampVoice(param, value)
		if (shape[param] == settled) return
		shape[param] = settled
		settings.setShapeValue(voice, param, settled)
	}

	/** Forgets this voice's changes. Every other voice is left alone, and so is
	 *  the speed, which belongs to the listener rather than to any of them. */
	fun resetVoice() {
		settings.clearShape(voice)
		shape.clear()
		shape.putAll(settings.shape(voice))
	}

	fun chooseAbbreviations(on: Boolean) {
		abbreviations = on
		settings.abbreviations = on
	}

	/** What the row for [volume] says: the file picked for it, or default/nothing. */
	fun dictionaryName(volume: Int): String {
		val custom = dictionaries[volume]
		if (custom != null) return custom
		if (volume == Eci.DICT_MAIN && !useCommunityDictionary) {
			return app.getString(R.string.default_dictionary)
		}
		return app.getString(R.string.dictionary_none)
	}

	fun toggleCommunityDictionary(on: Boolean) {
		if (isProcessingCommunityDictionary) return
		isProcessingCommunityDictionary = true
		Thread({
			try {
				if (on) {
					val mainFile = Dictionaries.getCommunityMainDictionaryFile(app)
					val countMain = Dictionaries.count(mainFile)
					val labelMain = app.getString(R.string.community_dictionary_entries, countMain)
					settings.setDictionary(Eci.DICT_MAIN, mainFile.absolutePath, labelMain)
					val currentRootPath = settings.dictionaryPath(Eci.DICT_ROOT)
					var newDicts = dictionaries
					if (currentRootPath != null && currentRootPath.contains("community_root.dic")) {
						settings.setDictionary(Eci.DICT_ROOT, null, null)
						newDicts = newDicts - Eci.DICT_ROOT
					}
					settings.useCommunityDictionary = true
					android.os.Handler(android.os.Looper.getMainLooper()).post {
						useCommunityDictionary = true
						dictionaries = newDicts + (Eci.DICT_MAIN to labelMain)
						isProcessingCommunityDictionary = false
					}
				} else {
					val currentMainPath = settings.dictionaryPath(Eci.DICT_MAIN)
					var newDicts = dictionaries
					if (currentMainPath != null && currentMainPath.contains("community_main.dic")) {
						settings.setDictionary(Eci.DICT_MAIN, null, null)
						newDicts = newDicts - Eci.DICT_MAIN
					}
					val currentRootPath = settings.dictionaryPath(Eci.DICT_ROOT)
					if (currentRootPath != null && currentRootPath.contains("community_root.dic")) {
						settings.setDictionary(Eci.DICT_ROOT, null, null)
						newDicts = newDicts - Eci.DICT_ROOT
					}
					settings.useCommunityDictionary = false
					android.os.Handler(android.os.Looper.getMainLooper()).post {
						useCommunityDictionary = false
						dictionaries = newDicts
						isProcessingCommunityDictionary = false
					}
				}
			} catch (e: Exception) {
				android.util.Log.e("evvdroid", "Error toggling community dictionary", e)
				android.os.Handler(android.os.Looper.getMainLooper()).post {
					isProcessingCommunityDictionary = false
				}
			}
		}, "community-dict-toggle").start()
	}

	fun dictionaryFile(volume: Int): java.io.File {
		val existing = settings.dictionaryPath(volume)
		if (existing != null) return java.io.File(existing)
		val dir = java.io.File(app.filesDir, "dictionaries").apply { mkdirs() }
		val defaultName = when (volume) {
			Eci.DICT_MAIN -> "volume-0-main.dic"
			Eci.DICT_ROOT -> "volume-1-root.dic"
			else -> "volume-2-abbr.dic"
		}
		return java.io.File(dir, defaultName)
	}

	fun getDictWords(volume: Int): List<DictEntry> {
		val file = dictionaryFile(volume)
		return Dictionaries.readDictEntries(file)
	}

	fun getWords(volume: Int): List<Pair<String, String>> {
		val file = dictionaryFile(volume)
		return Dictionaries.readEntries(file)
	}

	fun addOrUpdateWord(volume: Int, key: String, say: String, caseSensitive: Boolean = false): List<DictEntry> {
		val file = dictionaryFile(volume)
		val updated = Dictionaries.addOrUpdateDictEntry(file, key, say, caseSensitive)
		val name = settings.dictionaryName(volume)?.substringBefore(',') ?: when (volume) {
			Eci.DICT_MAIN -> app.getString(R.string.dictionary_main)
			Eci.DICT_ROOT -> app.getString(R.string.dictionary_root)
			else -> app.getString(R.string.dictionary_abbreviation)
		}
		val label = app.getString(R.string.dictionary_entries, name, updated.size)
		settings.setDictionary(volume, file.absolutePath, label)
		dictionaries = dictionaries + (volume to label)
		return updated
	}

	fun deleteWord(volume: Int, key: String, caseSensitive: Boolean? = null): List<DictEntry> {
		val file = dictionaryFile(volume)
		val updated = Dictionaries.deleteDictEntry(file, key, caseSensitive)
		val name = settings.dictionaryName(volume)?.substringBefore(',') ?: when (volume) {
			Eci.DICT_MAIN -> app.getString(R.string.dictionary_main)
			Eci.DICT_ROOT -> app.getString(R.string.dictionary_root)
			else -> app.getString(R.string.dictionary_abbreviation)
		}
		val label = app.getString(R.string.dictionary_entries, name, updated.size)
		settings.setDictionary(volume, file.absolutePath, label)
		dictionaries = dictionaries + (volume to label)
		return updated
	}

	fun hasDictionary(volume: Int): Boolean {
		val path = settings.dictionaryPath(volume) ?: return false
		return java.io.File(path).exists()
	}

	/**
	 * Takes a copy of the file the picker handed back.
	 *
	 * A picked document is a content URI belonging to whichever app supplied
	 * it, readable now and quite possibly not tomorrow, and the speech service
	 * is a different process that reads these when it starts. So what is stored
	 * is a copy of our own rather than a reference to somebody else's.
	 */
	fun chooseDictionary(volume: Int, uri: android.net.Uri) {
		val into = java.io.File(app.filesDir, "dictionaries").apply { mkdirs() }
		val file = java.io.File(into, "volume-$volume.dic")
		val name = nameOf(uri) ?: file.name
		try {
			app.contentResolver.openInputStream(uri).use { source ->
				if (source == null) return
				file.outputStream().use { sink -> source.copyTo(sink) }
			}
		} catch (unreadable: Exception) {
			android.util.Log.e("evvdroid", "cannot read the dictionary picked", unreadable)
			return
		}
		val entries = Dictionaries.count(file)
		if (entries == 0) {
			file.delete()
			dictionaries = dictionaries - volume
			settings.setDictionary(volume, null, null)
			return
		}
		val said = app.getString(R.string.dictionary_entries, name, entries)
		settings.setDictionary(volume, file.absolutePath, said)
		dictionaries = dictionaries + (volume to said)
	}

	fun removeDictionaries() {
		for (volume in Eci.DICT_VOLUMES) {
			settings.dictionaryPath(volume)?.let { java.io.File(it).delete() }
			settings.setDictionary(volume, null, null)
		}
		dictionaries = emptyMap()
		useCommunityDictionary = false
		settings.useCommunityDictionary = false
	}

	fun toggleLanguageDictionaries(on: Boolean) {
		useLanguageDictionaries = on
		settings.useLanguageDictionaries = on
	}

	fun langDictionaryName(eciLang: Int): String =
		langDictionaries[eciLang] ?: app.getString(R.string.dictionary_none)

	fun hasLangDictionary(eciLang: Int): Boolean {
		val path = settings.langDictPath(eciLang) ?: return false
		return java.io.File(path).exists()
	}

	fun langDictionaryFile(eciLang: Int): java.io.File {
		val existing = settings.langDictPath(eciLang)
		if (existing != null) return java.io.File(existing)
		val dir = java.io.File(app.filesDir, "dictionaries").apply { mkdirs() }
		return java.io.File(dir, "lang-%08x.dic".format(eciLang))
	}

	fun getLangDictWords(eciLang: Int): List<DictEntry> {
		val file = langDictionaryFile(eciLang)
		return Dictionaries.readDictEntries(file)
	}

	fun getLangWords(eciLang: Int): List<Pair<String, String>> {
		val file = langDictionaryFile(eciLang)
		return Dictionaries.readEntries(file)
	}

	fun addOrUpdateLangWord(eciLang: Int, key: String, say: String, caseSensitive: Boolean = false): List<DictEntry> {
		val file = langDictionaryFile(eciLang)
		val updated = Dictionaries.addOrUpdateDictEntry(file, key, say, caseSensitive)
		val langName = Eci.displayName(eciLang)
		val label = app.getString(R.string.dictionary_entries, langName, updated.size)
		settings.setLangDictionary(eciLang, file.absolutePath, label)
		langDictionaries = langDictionaries + (eciLang to label)
		return updated
	}

	fun deleteLangWord(eciLang: Int, key: String, caseSensitive: Boolean? = null): List<DictEntry> {
		val file = langDictionaryFile(eciLang)
		val updated = Dictionaries.deleteDictEntry(file, key, caseSensitive)
		val langName = Eci.displayName(eciLang)
		val label = app.getString(R.string.dictionary_entries, langName, updated.size)
		settings.setLangDictionary(eciLang, file.absolutePath, label)
		langDictionaries = langDictionaries + (eciLang to label)
		return updated
	}

	fun chooseLangDictionary(eciLang: Int, uri: android.net.Uri) {
		val into = java.io.File(app.filesDir, "dictionaries").apply { mkdirs() }
		val file = java.io.File(into, "lang-%08x.dic".format(eciLang))
		val name = nameOf(uri) ?: file.name
		try {
			app.contentResolver.openInputStream(uri).use { source ->
				if (source == null) return
				file.outputStream().use { sink -> source.copyTo(sink) }
			}
		} catch (unreadable: Exception) {
			android.util.Log.e("evvdroid", "cannot read the language dictionary picked", unreadable)
			return
		}
		val entries = Dictionaries.count(file)
		if (entries == 0) {
			file.delete()
			langDictionaries = langDictionaries - eciLang
			settings.setLangDictionary(eciLang, null, null)
			return
		}
		val said = app.getString(R.string.dictionary_entries, name, entries)
		settings.setLangDictionary(eciLang, file.absolutePath, said)
		langDictionaries = langDictionaries + (eciLang to said)
	}

	fun removeLangDictionary(eciLang: Int) {
		settings.langDictPath(eciLang)?.let { java.io.File(it).delete() }
		settings.setLangDictionary(eciLang, null, null)
		langDictionaries = langDictionaries - eciLang
	}

	private fun nameOf(uri: android.net.Uri): String? = runCatching {
		app.contentResolver.query(uri, null, null, null, null)?.use { row ->
			val at = row.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
			if (at >= 0 && row.moveToFirst()) row.getString(at) else null
		}
	}.getOrNull()

	fun choosePhrasePrediction(on: Boolean) {
		phrasePrediction = on
		settings.phrasePrediction = on
	}

	fun chooseDebounceKeyEcho(on: Boolean) {
		debounceKeyEcho = on
		settings.debounceKeyEcho = on
	}

	fun chooseThemeMode(mode: Int) {
		themeMode = mode
		settings.themeMode = mode
	}

	fun chooseSplitCamelCase(on: Boolean) {
		splitCamelCase = on
		settings.splitCamelCase = on
	}

	fun chooseCountRepeatedChars(on: Boolean) {
		countRepeatedChars = on
		settings.countRepeatedChars = on
	}

	fun chooseEnableBackquoteVoiceTags(on: Boolean) {
		enableBackquoteVoiceTags = on
		settings.enableBackquoteVoiceTags = on
	}

	fun chooseReadEmoji(on: Boolean) {
		readEmoji = on
		settings.readEmoji = on
	}

	fun chooseReadPunctuation(on: Boolean) {
		readPunctuation = on
		settings.readPunctuation = on
	}

	fun choosePunctuationLevel(level: Int) {
		punctuationLevel = level
		settings.punctuationLevel = level
	}

	fun chooseProcessNumbers(on: Boolean) {
		processNumbers = on
		settings.processNumbers = on
	}

	fun chooseNumberMode(mode: Int) {
		numberMode = mode
		settings.numberMode = mode
	}

	fun chooseForceSpeechRate(on: Boolean) {
		forceSpeechRate = on
		settings.forceSpeechRate = on
	}

	fun chooseForcePitch(on: Boolean) {
		forcePitch = on
		settings.forcePitch = on
	}

	fun chooseForceLanguage(on: Boolean) {
		forceLanguage = on
		settings.forceLanguage = on
		if (on && forcedLanguage == 0) {
			forcedLanguage = 0x00010000
			settings.forcedLanguage = 0x00010000
		}
	}

	fun chooseForcedLanguage(language: Int) {
		forcedLanguage = language
		settings.forcedLanguage = language
	}

	fun chooseSkipSymbols(on: Boolean) {
		skipSymbols = on
		settings.skipSymbols = on
	}

	fun addSkipSymbol(sym: String) {
		settings.addSkipSymbol(sym)
		skipSymbolsList = settings.skipSymbolsList
	}

	fun removeSkipSymbol(sym: String) {
		settings.removeSkipSymbol(sym)
		skipSymbolsList = settings.skipSymbolsList
	}

	fun choosePauses(mode: Int) {
		pauses = mode
		settings.pauses = mode
	}

	fun dismissFirstRun(dontShowAgain: Boolean) {
		firstRunDismissed = true
		if (dontShowAgain) {
			settings.firstRunDismissed = true
		}
	}

	fun chooseEnableLogging(on: Boolean) {
		enableLogging = on
		settings.enableLogging = on
		AppLogger.setEnabled(on)
	}

	fun checkDefaultTtsDialog(context: Context) {
		val count = settings.appLaunchCount
		if (count == 0) {
			// First run complete: do not show dialog on the first run
			settings.appLaunchCount = 1
			return
		}
		settings.appLaunchCount = count + 1
		if (settings.dontShowDefaultTtsDialog) return
		if (!isDefaultTtsEngine(context)) {
			showDefaultTtsDialog = true
		}
	}

	fun dismissDefaultTtsDialog(dontShowAgain: Boolean) {
		showDefaultTtsDialog = false
		if (dontShowAgain) {
			settings.dontShowDefaultTtsDialog = true
		}
	}

	fun chooseSampleRate(hz: Int) {
		sampleRateHz = hz
		settings.sampleRateHz = hz
	}

	// ---- hearing it ------------------------------------------------------

	/** Nothing here speaks by itself. Every setting is in force the moment it
	 *  is written down, and this is how it gets heard. */
	fun say() {
		preview?.say(app.getString(R.string.preview_text), voice, settings.shape(voice), sampleRateHz)
	}

	private fun adoptVoice() {
		val own = preview?.presetShape(settings.voice) ?: return
		if (own.isEmpty()) return
		// Everything but the speed, which stays where the listener put it.
		// Glen and Sandy ship at 70 where the rest are 50, so taking the
		// voice's own would move the slider and the pace on every change.
		// Volume defaults to 100 (from 90+ engine preset default).
		val customized = own.toMutableMap()
		customized[Eci.VOICE_VOLUME] = Settings.DEFAULT_VOLUME
		settings.writeShape(settings.voice, customized - Eci.VOICE_SPEED)
	}

	fun shareLog(context: Context) {
		AppLogger.shareLog(context)
	}

	fun chooseCheckForUpdatesOnStartup(enabled: Boolean) {
		settings.checkForUpdatesOnStartup = enabled
		checkForUpdatesOnStartup = enabled
	}

	fun checkForUpdates(manual: Boolean = false) {
		isCheckingForUpdates = true
		if (manual) updateCheckMessage = null
		UpdateManager.checkForUpdates(app) { result ->
			mainHandler.post {
				isCheckingForUpdates = false
				result.onSuccess { checkResult ->
					if (checkResult.isUpdateAvailable) {
						availableUpdate = checkResult
						isUpdateDialogOpen = true
					} else if (manual) {
						updateCheckMessage = app.getString(R.string.no_updates_available)
					}
				}.onFailure { error ->
					if (manual) {
						updateCheckMessage = app.getString(R.string.update_error)
					}
				}
			}
		}
	}

	fun startUpdateDownload() {
		val update = availableUpdate ?: return
		val release = update.latestRelease
		val asset = update.selectedAsset ?: release?.assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
		if (asset == null) {
			if (release != null && release.htmlUrl.isNotBlank()) {
				try {
					val browserIntent = android.content.Intent(
						android.content.Intent.ACTION_VIEW,
						android.net.Uri.parse(release.htmlUrl)
					).apply {
						addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
					}
					app.startActivity(browserIntent)
					isUpdateDialogOpen = false
				} catch (e: Exception) {
					updateCheckMessage = "No APK available to download."
				}
			}
			return
		}

		isUpdateDialogOpen = false
		isDownloadDialogOpen = true
		downloadProgress = DownloadProgress(totalBytes = asset.size)
		UpdateManager.downloadAsset(app, asset) { progress ->
			mainHandler.post {
				downloadProgress = progress
				if (progress.isFailed) {
					isDownloadDialogOpen = false
					updateCheckMessage = progress.errorMessage ?: app.getString(R.string.download_failed)
				} else if (progress.isComplete && progress.downloadedFile != null) {
					downloadedApkFile = progress.downloadedFile
					isDownloadDialogOpen = false
					isPostDownloadDialogOpen = true
				}
			}
		}
	}

	fun putDownloadInBackground() {
		isDownloadDialogOpen = false
	}

	fun installDownloadedApk(activity: Context) {
		val file = downloadedApkFile ?: return
		if (!UpdateManager.canInstallPackages(activity)) {
			showUnknownSourcesPrompt = true
		} else {
			UpdateManager.installApk(activity, file)
		}
	}

	fun openUnknownSourcesSettings(context: Context) {
		showUnknownSourcesPrompt = false
		UpdateManager.openUnknownSourcesSettings(context)
	}

	fun saveDownloadedApk(targetUri: Uri): Boolean {
		val file = downloadedApkFile ?: return false
		val success = UpdateManager.saveApkToUri(app, file, targetUri)
		if (success) {
			isPostDownloadDialogOpen = false
		}
		return success
	}

	companion object {
		val RATES: List<Int> = Eci.SAMPLE_RATES.filter { it <= 11025 }.sorted()

		fun isDefaultTtsEngine(context: Context): Boolean {
			return try {
				val defaultSynth = android.provider.Settings.Secure.getString(
					context.contentResolver,
					android.provider.Settings.Secure.TTS_DEFAULT_SYNTH
				)
				defaultSynth == context.packageName
			} catch (e: Exception) {
				false
			}
		}
	}
}
