package org.evvdroid

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import android.view.accessibility.AccessibilityManager
import java.util.Locale

/**
 * The engine Android talks to.
 *
 * onSynthesizeText runs on the framework's own synthesis thread and is the
 * only place an utterance is driven; onStop arrives from another thread and
 * signals the engine to abort long utterances.
 */
class EvvTtsService : TextToSpeechService() {

	private var engine: EvvEngine? = null
	private var settings: Settings? = null
	private var accessibilityManager: AccessibilityManager? = null

	@Volatile
	private var stopped = false

	@Volatile
	private var isKeySynthesisInProgress = false

	@Volatile
	private var lastAnnouncedKey: String? = null
	@Volatile
	private var lastAnnouncedKeyTime: Long = 0L

	private var appliedRevision = -1
	private var appliedVoicePreset = -1
	private var appliedVoiceShape: Map<Int, Int>? = null

	private var loadedDictionaries: Map<Int, String> = emptyMap()
	private var loadedLangDictPath: String? = null
	private var loadedDictRevision: Int = -1
	@Volatile
	private var activeCaseSensitiveEntries: List<Dictionaries.CachedCaseSensitiveEntry> = emptyList()

	private val dictLock = Any()
	private val dictExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
	private val prefChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
		if (key != null && (key.contains("dict") || key.contains("community"))) {
			schedulePreloadDictionaries()
		}
	}

	private fun schedulePreloadDictionaries() {
		val eng = engine ?: return
		val s = settings ?: return
		dictExecutor.execute {
			try {
				synchronized(dictLock) {
					loadDictionaries(eng, s)
				}
			} catch (e: Exception) {
				Log.e(TAG, "Background dictionary preload failed", e)
			}
		}
	}

	private val stopLock = Object()
	private val stateLock = Any()

	@Volatile
	private var activeUtteranceId: Long = 0L
	@Volatile
	private var activeUtteranceStartTime: Long = 0L
	@Volatile
	private var activePhase: String = "idle"

	private fun markUtteranceActive(uId: Long, t0: Long, initialPhase: String = "matching_language") {
		synchronized(stateLock) {
			stopped = false
			activeUtteranceId = uId
			activeUtteranceStartTime = t0
			activePhase = initialPhase
		}
	}

	private fun markUtteranceIdle() {
		synchronized(stateLock) {
			activePhase = "idle"
			activeUtteranceId = 0L
			activeUtteranceStartTime = 0L
		}
	}

	private fun updatePhase(phase: String) {
		synchronized(stateLock) {
			if (activePhase != "idle") {
				activePhase = phase
			}
		}
	}

	/* ---- rate stability guard ----
	 * When a screen reader (e.g. Commentary/JieShuo) crashes and restarts it
	 * reconnects to this TTS service and may send its default (lower) speech
	 * rate rather than the rate the user had been listening at. The listener
	 * perceives a sudden slowdown. This guard detects such drops and holds the
	 * last known stable rate until the screen reader sends the lower rate for
	 * several consecutive utterances, which indicates a deliberate change. */
	private var stableRate: Int = -1
	private var consecutiveLowCount: Int = 0

	private var keyAudioTrack: AudioTrack? = null

	@Synchronized
	private fun ensureKeyAudioTrack(sampleRateHz: Int): AudioTrack? {
		var track = keyAudioTrack
		if (track == null || track.sampleRate != sampleRateHz) {
			try {
				track?.release()
			} catch (ignored: Exception) {}
			val minBuf = AudioTrack.getMinBufferSize(
				sampleRateHz,
				AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT
			)
			val bufferSize = maxOf(minBuf * 2, 2048)
			val builder = AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
				.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
				builder.setFlags(AudioAttributes.FLAG_LOW_LATENCY)
			}
			track = AudioTrack(
				builder.build(),
				AudioFormat.Builder()
					.setSampleRate(sampleRateHz)
					.setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
					.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
					.build(),
				bufferSize,
				AudioTrack.MODE_STREAM,
				AudioManager.AUDIO_SESSION_ID_GENERATE
			)
			track.play()
			keyAudioTrack = track
		}
		return track
	}

	@Synchronized
	private fun prepareKeyAudio(sampleRateHz: Int) {
		try {
			val track = ensureKeyAudioTrack(sampleRateHz) ?: return
			track.pause()
			track.flush()
			track.play()
		} catch (e: Exception) {
			Log.e(TAG, "Error preparing key audio", e)
		}
	}

	@Synchronized
	private fun writeKeyAudioChunk(pcmData: ByteArray, length: Int) {
		if (length <= 0) return
		try {
			val track = keyAudioTrack ?: return
			track.write(pcmData, 0, length)
		} catch (e: Exception) {
			Log.e(TAG, "Error writing key audio chunk", e)
		}
	}

	@Synchronized
	private fun stopKeyAudio() {
		try {
			val track = keyAudioTrack ?: return
			track.pause()
			track.flush()
		} catch (ignored: Exception) {}
	}

	override fun onCreate() {
		AppLogger.init(this)
		settings = Settings(this)
		Thread({
			Emoji.init(applicationContext)
			try {
				Dictionaries.getCommunityMainDictionaryFile(applicationContext)
			} catch (ignored: Exception) {}
		}, "evv-init").start()
		accessibilityManager = getSystemService(ACCESSIBILITY_SERVICE) as? AccessibilityManager
		super.onCreate()
		try {
			getSharedPreferences("${packageName}_preferences", MODE_PRIVATE)
				.registerOnSharedPreferenceChangeListener(prefChangeListener)
		} catch (ignored: Exception) {}
		AppLogger.i(TAG, "EvvTtsService started and ready")
	}

	override fun onDestroy() {
		super.onDestroy()
		try {
			getSharedPreferences("${packageName}_preferences", MODE_PRIVATE)
				.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
		} catch (ignored: Exception) {}
		dictExecutor.shutdown()
		stopKeyAudio()
		try {
			keyAudioTrack?.release()
		} catch (ignored: Exception) {}
		keyAudioTrack = null
		engine?.close()
		engine = null
	}

	private fun resolveCallerPackage(uid: Int): String {
		if (uid <= 0) return "system"
		return try {
			val names = packageManager.getPackagesForUid(uid)
			val primary = names?.firstOrNull() ?: packageManager.getNameForUid(uid)
			if (primary != null) {
				if (primary.contains("autotts", ignoreCase = true)) "$primary (Auto TTS)"
				else if (primary.contains("talkback", ignoreCase = true)) "$primary (TalkBack)"
				else primary
			} else {
				"uid:$uid"
			}
		} catch (e: Exception) {
			"uid:$uid"
		}
	}

	// ---- languages -------------------------------------------------------

	/**
	 * Matches an incoming language request (which can be a 2-letter ISO 639-1 code,
	 * 3-letter ISO 639-2/3 code, BCP-47 language tag like "en-US", "es-419", "zh-CN",
	 * or underscore format like "en_US") to the best available Eloquence engine language.
	 */
	private fun matchLanguage(rawLang: String?, rawCountry: String?): Pair<Int, Int>? {
		if (rawLang.isNullOrBlank()) {
			val sys = Locale.getDefault()
			if (sys.language.isNotBlank()) {
				val langMatch = resolveDialect(sys.language.lowercase(Locale.ROOT), sys.country.uppercase(Locale.ROOT))
				if (langMatch != null && EvvEngine.available.contains(langMatch)) {
					return langMatch to TextToSpeech.LANG_AVAILABLE
				}
			}
			val fallback = EvvEngine.available.firstOrNull() ?: 0x00010000
			return fallback to TextToSpeech.LANG_AVAILABLE
		}

		var langToken = rawLang.trim()
		var countryToken = rawCountry?.trim().orEmpty()

		if (langToken.contains('-') || langToken.contains('_')) {
			val delimiter = if (langToken.contains('-')) '-' else '_'
			val parts = langToken.split(delimiter)
			langToken = parts.firstOrNull().orEmpty()
			if (countryToken.isEmpty() && parts.size > 1) {
				for (part in parts.drop(1).reversed()) {
					if (part.length in 2..4 && part.all { it.isLetterOrDigit() }) {
						countryToken = part
						break
					}
				}
			}
		}

		val normLang = langToken.lowercase(Locale.ROOT)
		val normCountry = countryToken.uppercase(Locale.ROOT)

		// 1. Direct exact match (Language + Country)
		if (normCountry.isNotEmpty()) {
			for (candidate in EvvEngine.available) {
				val info = Eci.languageLocaleOf(candidate) ?: continue
				val langMatches = normLang == info.iso2Lang || normLang == info.iso3Lang || matchesAltLang(normLang, info.iso3Lang)
				val countryMatches = normCountry == info.iso2Country || normCountry == info.iso3Country || matchesAltCountry(normCountry, info.iso3Country)
				if (langMatches && countryMatches) {
					return candidate to TextToSpeech.LANG_COUNTRY_AVAILABLE
				}
			}
		}

		// 2. Language match with dialect intelligence
		val dialectMatch = resolveDialect(normLang, normCountry)
		if (dialectMatch != null && EvvEngine.available.contains(dialectMatch)) {
			val isCountryMatch = normCountry.isNotEmpty() && isCloseCountryMatch(dialectMatch, normCountry)
			val score = if (isCountryMatch) TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_AVAILABLE
			return dialectMatch to score
		}

		// 3. Fallback: first available engine language matching language code
		for (candidate in EvvEngine.available) {
			val info = Eci.languageLocaleOf(candidate) ?: continue
			if (normLang == info.iso2Lang || normLang == info.iso3Lang || matchesAltLang(normLang, info.iso3Lang)) {
				return candidate to TextToSpeech.LANG_AVAILABLE
			}
		}

		return null
	}

	private fun matchesAltLang(input: String, iso3: String): Boolean = when (iso3) {
		"fra" -> input in setOf("fre", "fra", "fr", "frc")
		"deu" -> input in setOf("ger", "deu", "de")
		"cmn" -> input in setOf("zho", "chi", "zh", "chs", "zht", "cmn")
		"yue" -> input in setOf("zho", "chi", "zh", "yue", "ctt")
		"nld" -> input in setOf("dut", "nld", "nl")
		"nor" -> input in setOf("nob", "nn", "nno", "no", "nor", "nb")
		"spa" -> input in setOf("spa", "es", "esp", "esm")
		"por" -> input in setOf("por", "pt", "ptb")
		"jpn" -> input in setOf("jpn", "ja", "jp")
		"fin" -> input in setOf("fin", "fi")
		"kor" -> input in setOf("kor", "ko")
		"swe" -> input in setOf("swe", "sv")
		"dan" -> input in setOf("dan", "da")
		"ita" -> input in setOf("ita", "it")
		"pol" -> input in setOf("pol", "pl")
		"hin" -> input in setOf("hin", "hi", "hindi")
		else -> false
	}

	private fun matchesAltCountry(input: String, iso3: String): Boolean = when (iso3) {
		"GBR" -> input in setOf("UK", "GB", "GBR")
		"USA" -> input in setOf("US", "USA")
		"MEX" -> input in setOf("MX", "MEX", "419", "CO", "AR", "CL", "PE")
		"ESP" -> input in setOf("ES", "ESP")
		"FRA" -> input in setOf("FR", "FRA")
		"CAN" -> input in setOf("CA", "CAN")
		"DEU" -> input in setOf("DE", "DEU")
		"ITA" -> input in setOf("IT", "ITA")
		"BRA" -> input in setOf("BR", "BRA")
		"CHN" -> input in setOf("CN", "CHN")
		"TWN" -> input in setOf("TW", "TWN")
		"HKG" -> input in setOf("HK", "HKG")
		"JPN" -> input in setOf("JP", "JPN")
		"FIN" -> input in setOf("FI", "FIN")
		"KOR" -> input in setOf("KR", "KOR")
		"NLD" -> input in setOf("NL", "NLD")
		"NOR" -> input in setOf("NO", "NOR")
		"SWE" -> input in setOf("SE", "SWE")
		"DNK" -> input in setOf("DK", "DNK")
		"POL" -> input in setOf("PL", "POL")
		"IND" -> input in setOf("IN", "IND")
		else -> false
	}

	private fun isCloseCountryMatch(candidate: Int, country: String): Boolean {
		val info = Eci.languageLocaleOf(candidate) ?: return false
		return country == info.iso2Country || country == info.iso3Country || matchesAltCountry(country, info.iso3Country)
	}

	private fun resolveDialect(normLang: String, normCountry: String): Int? {
		return when (normLang) {
			"en", "eng", "enu" -> {
				when (normCountry) {
					"GB", "GBR", "UK", "AU", "NZ", "ZA", "IN", "IE" -> 0x00010001
					else -> 0x00010000
				}
			}
			"es", "spa", "esp", "esm" -> {
				when (normCountry) {
					"ES", "ESP" -> 0x00020000
					else -> 0x00020001
				}
			}
			"fr", "fra", "fre", "frc" -> {
				when (normCountry) {
					"CA", "CAN" -> 0x00030001
					else -> 0x00030000
				}
			}
			"zh", "cmn", "zho", "chi", "chs", "zht" -> {
				when (normCountry) {
					"TW", "TWN", "HANT" -> 0x00060001
					"HK", "HKG" -> 0x000b0001
					else -> 0x00060000
				}
			}
			"yue", "ctt" -> {
				when (normCountry) {
					"HK", "HKG" -> 0x000b0001
					else -> 0x000b0000
				}
			}
			"pt", "por", "ptb" -> 0x00070000
			"de", "deu", "ger" -> 0x00040000
			"it", "ita" -> 0x00050000
			"ja", "jpn", "jp" -> 0x00080000
			"fi", "fin" -> 0x00090000
			"ko", "kor" -> 0x000a0000
			"nl", "nld", "dut" -> 0x000c0000
			"no", "nor", "nb", "nob", "nn", "nno" -> 0x000d0000
			"sv", "swe" -> 0x000e0000
			"da", "dan" -> 0x000f0000
			"pl", "pol" -> 0x00110000
			"th", "tha" -> 0x00110000
			"hi", "hin", "hindi" -> {
				if (EvvEngine.available.contains(0x00120000)) 0x00120000 else 0x00010000
			}
			else -> null
		}
	}

	override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
		val caller = resolveCallerPackage(android.os.Binder.getCallingUid())
		val found = matchLanguage(lang, country)
		val res = found?.second ?: TextToSpeech.LANG_NOT_SUPPORTED
		AppLogger.logAutoTtsQuery("onIsLanguageAvailable", lang, country, "code=$res (engine=${found?.first?.let { "0x%08x".format(it) } ?: "none"}) [caller=$caller]")
		return res
	}

	override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
		val caller = resolveCallerPackage(android.os.Binder.getCallingUid())
		val found = matchLanguage(lang, country)
		if (found == null) {
			AppLogger.logAutoTtsQuery("onLoadLanguage", lang, country, "NOT_SUPPORTED [caller=$caller]")
			return TextToSpeech.LANG_NOT_SUPPORTED
		}
		ensureEngine(found.first) ?: run {
			AppLogger.logAutoTtsQuery("onLoadLanguage", lang, country, "ENGINE_OPEN_FAILED [caller=$caller]")
			return TextToSpeech.LANG_NOT_SUPPORTED
		}
		AppLogger.logAutoTtsQuery("onLoadLanguage", lang, country, "LOADED 0x%08x (score=${found.second}) [caller=$caller]".format(found.first))
		return found.second
	}

	override fun onGetLanguage(): Array<String> {
		val caller = resolveCallerPackage(android.os.Binder.getCallingUid())
		val s = settings
		val language = if (s?.forceLanguage == true) {
			s.forcedLanguage
		} else {
			engine?.language ?: run {
				val sys = Locale.getDefault()
				val matched = matchLanguage(sys.language, sys.country)?.first
				matched ?: EvvEngine.available.firstOrNull() ?: 0x00010000
			}
		}
		val loc = Eci.languageLocaleOf(language) ?: return arrayOf("eng", "USA", "")
		AppLogger.d("AUTO_TTS", "onGetLanguage returning [${loc.iso3Lang}, ${loc.iso3Country}] (forced=${s?.forceLanguage}) [caller=$caller]")
		return arrayOf(loc.iso3Lang, loc.iso3Country, "")
	}

	// ---- voices ----------------------------------------------------------

	private fun voiceName(language: Int, preset: Int): String {
		val loc = Eci.languageLocaleOf(language) ?: return "evv-$preset"
		return "${loc.iso3Lang}-${loc.iso3Country}-${Eci.PRESET_NAMES[preset]}"
	}

	override fun onGetVoices(): MutableList<Voice> {
		val caller = resolveCallerPackage(android.os.Binder.getCallingUid())
		AppLogger.d("AUTO_TTS", "onGetVoices queried by $caller")
		val out = ArrayList<Voice>()
		for (language in EvvEngine.available) {
			val loc = Eci.languageLocaleOf(language) ?: continue
			for (preset in Eci.PRESET_NAMES.indices) {
				out.add(
					Voice(
						voiceName(language, preset),
						loc.javaLocale,
						Voice.QUALITY_NORMAL,
						Voice.LATENCY_VERY_LOW,
						false,
						emptySet()
					)
				)
			}
		}
		return out
	}

	private fun findVoice(name: String?): Pair<Int, Int>? {
		if (name.isNullOrBlank()) return null
		val targetName = name.trim()

		// 1. Exact match with canonical voiceName
		for (language in EvvEngine.available) {
			for (preset in Eci.PRESET_NAMES.indices) {
				if (voiceName(language, preset).equals(targetName, ignoreCase = true)) {
					return language to preset
				}
			}
		}

		// 2. Match BCP-47 tag format: e.g. "en-US-Reed", "es-ES-Shelley", "pt-BR-Bobby"
		for (language in EvvEngine.available) {
			val loc = Eci.languageLocaleOf(language) ?: continue
			for (preset in Eci.PRESET_NAMES.indices) {
				val bcpName = "${loc.iso2Lang}-${loc.iso2Country}-${Eci.PRESET_NAMES[preset]}"
				if (bcpName.equals(targetName, ignoreCase = true)) {
					return language to preset
				}
			}
		}

		// 3. Match format with preset at the end (e.g. "en-Reed", "eng-Reed", "Reed")
		for (preset in Eci.PRESET_NAMES.indices) {
			val pName = Eci.PRESET_NAMES[preset]
			if (targetName.equals(pName, ignoreCase = true) || targetName.endsWith("-$pName", ignoreCase = true) || targetName.endsWith("_$pName", ignoreCase = true)) {
				val prefix = targetName.substringBeforeLast("-$pName", "").ifEmpty {
					targetName.substringBeforeLast("_$pName", "")
				}
				val lang = if (prefix.isNotEmpty()) matchLanguage(prefix, null)?.first else null
				val resolvedLang = lang ?: (engine?.language ?: EvvEngine.available.firstOrNull() ?: 0x00010000)
				return resolvedLang to preset
			}
		}

		return null
	}

	override fun onIsValidVoiceName(name: String?): Int =
		if (findVoice(name) != null) TextToSpeech.SUCCESS else TextToSpeech.ERROR

	override fun onLoadVoice(name: String?): Int {
		val found = findVoice(name) ?: return TextToSpeech.ERROR
		val target = ensureEngine(found.first) ?: return TextToSpeech.ERROR
		val s = settings
		if (s != null) {
			applyVoiceConfig(target, found.second, s)
		}
		return TextToSpeech.SUCCESS
	}

	override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
		val s = settings
		val targetLang = if (s?.forceLanguage == true) {
			s.forcedLanguage
		} else {
			matchLanguage(lang, country)?.first ?: return null
		}
		val preset = s?.voice ?: 0
		return voiceName(targetLang, preset)
	}

	// ---- speaking --------------------------------------------------------

	/**
	 * Detects sudden, suspicious speech‐rate drops that happen when a screen
	 * reader crashes and reconnects with its default (lower) rate. If the
	 * incoming rate is more than [RATE_GUARD_DROP_THRESHOLD] percent below the
	 * last stable rate, the old rate is kept for up to [RATE_GUARD_GRACE]
	 * consecutive utterances.  After that the new rate is accepted as
	 * intentional.  Small adjustments (≤ 30 % drop) pass through immediately.
	 */
	private fun guardRate(incoming: Int, utteranceId: Long): Int {
		// First utterance ever — consult persistent storage before falling back to incoming.
		if (stableRate < 0) {
			val persisted = settings?.lastStableRate ?: -1
			if (persisted > 0) {
				stableRate = persisted
			} else {
				stableRate = incoming
				settings?.lastStableRate = incoming
				consecutiveLowCount = 0
				return incoming
			}
		}

		// Within tolerance, or going UP — accept, persist if changed, and reset.
		val threshold = (stableRate * RATE_GUARD_DROP_THRESHOLD / 100.0).toInt()
		if (incoming >= threshold) {
			if (stableRate != incoming) {
				stableRate = incoming
				settings?.lastStableRate = incoming
			}
			consecutiveLowCount = 0
			return incoming
		}

		// Sudden large drop.
		consecutiveLowCount++
		val grace = if (incoming == 100) RATE_GUARD_UNINITIALIZED_GRACE else RATE_GUARD_GRACE
		if (consecutiveLowCount > grace) {
			// The screen reader has been sending this rate long enough that it
			// looks deliberate. Accept it.
			AppLogger.logRateGuard(utteranceId, incoming, incoming, stableRate, consecutiveLowCount)
			stableRate = incoming
			settings?.lastStableRate = incoming
			consecutiveLowCount = 0
			return incoming
		}

		// Hold the old stable rate.
		AppLogger.logRateGuard(utteranceId, incoming, stableRate, stableRate, consecutiveLowCount)
		return stableRate
	}

	private fun ensureEngine(language: Int): EvvEngine? {
		val held = engine
		if (held != null && held.language == language) return held
		held?.close()
		val made = EvvEngine.open(language)
		engine = made
		if (made != null) {
			loadedDictionaries = emptyMap()
			loadedLangDictPath = null
			loadedDictRevision = -1
			activeCaseSensitiveEntries = emptyList()
			appliedVoicePreset = -1
			appliedVoiceShape = null
			applySettings(made)
		}
		return made
	}

	private fun applyVoiceConfig(target: EvvEngine, preset: Int, s: Settings) {
		val voiceShape = s.shape(preset).toMutableMap()
		if (!s.forceSpeechRate) {
			voiceShape.remove(Eci.VOICE_SPEED)
		}
		if (!s.forcePitch) {
			voiceShape.remove(Eci.VOICE_PITCH_BASELINE)
		}
		target.applyVoice(preset, voiceShape)
		appliedVoicePreset = preset
		appliedVoiceShape = voiceShape
	}

	private fun applySettings(target: EvvEngine) {
		val s = settings ?: return
		target.setSampleRate(s.sampleRateHz)
		target.setAbbreviations(s.abbreviations)
		applyVoiceConfig(target, s.voice, s)
		ensureKeyAudioTrack(s.sampleRateHz)
		appliedRevision = s.revision
		schedulePreloadDictionaries()
	}

	/** Teaching words happens asynchronously in the background rather than blocking the binder or synthesis thread. */
	private fun loadDictionaries(target: EvvEngine, s: Settings) = synchronized(dictLock) {
		val want = s.dictionaryPaths().toMutableMap()
		if (s.useCommunityDictionary) {
			if (!want.containsKey(Eci.DICT_MAIN)) {
				val mainFile = Dictionaries.getCommunityMainDictionaryFile(this)
				if (mainFile.exists() && mainFile.length() > 0L) {
					want[Eci.DICT_MAIN] = mainFile.absolutePath
				}
			}
		}
		val langPath = if (s.useLanguageDictionaries) s.langDictPath(target.language) else null
		if (want == loadedDictionaries && langPath == loadedLangDictPath && s.revision == loadedDictRevision) return@synchronized
		target.forgetDictionaries()
		val activeCaseSensitive = mutableListOf<Dictionaries.CachedCaseSensitiveEntry>()
		for ((volume, path) in want) {
			val file = java.io.File(path)
			if (!file.exists()) continue
			val (taught, csEntries) = Dictionaries.loadWithCaseSensitive(target, volume, file)
			Log.i(TAG, "volume $volume: $taught entries from ${file.name}")
			activeCaseSensitive.addAll(csEntries)
		}
		if (langPath != null) {
			val file = java.io.File(langPath)
			if (file.exists()) {
				val (taught, csEntries) = Dictionaries.loadWithCaseSensitive(target, Eci.DICT_MAIN, file)
				Log.i(TAG, "language dictionary for 0x%08x: $taught entries from ${file.name}".format(target.language))
				activeCaseSensitive.addAll(csEntries)
			}
		}
		activeCaseSensitiveEntries = activeCaseSensitive
		loadedDictionaries = want
		loadedLangDictPath = langPath
		loadedDictRevision = s.revision
	}


	override fun onStop() {
		val caller = resolveCallerPackage(android.os.Binder.getCallingUid())
		var shouldStopEngine = false
		synchronized(stateLock) {
			val uId = activeUtteranceId
			val phase = activePhase
			val elapsed = if (activeUtteranceStartTime > 0) SystemClock.uptimeMillis() - activeUtteranceStartTime else 0L
			AppLogger.logSwipeStop(uId, caller, phase, elapsed)
			// If engine is idle or no active utterance is running, this stop signal
			// is stale (e.g. from an utterance that already finished).
			// Do NOT set stopped = true or stop the engine, as doing so would
			// poison the NEXT utterance starting on SynthThread!
			if (phase == "idle" || uId == 0L) {
				return
			}
			stopped = true
			shouldStopEngine = !isKeySynthesisInProgress
		}
		synchronized(stopLock) {
			stopLock.notifyAll()
		}
		if (shouldStopEngine) {
			engine?.stop()
		}
	}

	override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
		if (request == null || callback == null) return
		val uId = AppLogger.nextUtteranceId()
		val t0 = SystemClock.uptimeMillis()
		markUtteranceActive(uId, t0, "matching_language")

		val caller = resolveCallerPackage(request.callerUid)
		val rawText = request.charSequenceText?.toString().orEmpty()
		val s = settings
		val isKey = (s?.debounceKeyEcho != false) && KeyEcho.isKeyText(rawText)

		AppLogger.logUtteranceStart(
			uId,
			caller,
			rawText,
			request.language,
			request.country,
			request.voiceName,
			request.speechRate,
			request.pitch,
			isKey
		)

		if (rawText.isEmpty()) {
			val sampleRate = engine?.sampleRateHz ?: s?.sampleRateHz ?: 11025
			callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
			callback.done()
			markUtteranceIdle()
			AppLogger.logUtteranceComplete(uId, SystemClock.uptimeMillis() - t0, 0, 0)
			return
		}

		val wanted = findVoice(request.voiceName)
		val found = matchLanguage(request.language, request.country)
		val language = if (s?.forceLanguage == true) {
			s.forcedLanguage
		} else {
			wanted?.first ?: found?.first ?: engine?.language ?: EvvEngine.available.firstOrNull()
		}
		if (language == null) {
			AppLogger.e(TAG, "Unsupported language in utterance #$uId: ${request.language}_${request.country} (voice=${request.voiceName})")
			callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET)
			markUtteranceIdle()
			return
		}
		val target = ensureEngine(language)
		if (target == null) {
			AppLogger.e(TAG, "Failed to get engine for utterance #$uId (lang=0x%08x)".format(language))
			callback.error(TextToSpeech.ERROR_SERVICE)
			markUtteranceIdle()
			return
		}
		if (stopped) {
			callback.error(TextToSpeech.STOPPED)
			markUtteranceIdle()
			AppLogger.logUtteranceComplete(uId, SystemClock.uptimeMillis() - t0, 0, 0)
			return
		}
		target.clearAbort()

		val voicePreset = s?.voice ?: wanted?.second ?: 0
		val currentShape = s?.let {
			val vs = it.shape(voicePreset).toMutableMap()
			if (!it.forceSpeechRate) vs.remove(Eci.VOICE_SPEED)
			if (!it.forcePitch) vs.remove(Eci.VOICE_PITCH_BASELINE)
			vs
		}
		val needSettingsUpdate = s != null && (
			s.revision != appliedRevision ||
			voicePreset != appliedVoicePreset ||
			currentShape != appliedVoiceShape ||
			target.sampleRateHz != s.sampleRateHz
		)
		if (needSettingsUpdate && s != null) {
			if (s.revision != appliedRevision || target.sampleRateHz != s.sampleRateHz) {
				applySettings(target)
			}
			applyVoiceConfig(target, voicePreset, s)
			appliedRevision = s.revision
		}
		AppLogger.setEnabled(s?.enableLogging == true)

		s?.let { target.setAbbreviations(it.abbreviations) }
		val rawRate = if (s?.forceSpeechRate == true) 100 else request.speechRate
		val pitchPercent = if (s?.forcePitch == true) 100 else request.pitch
		val ratePercent = guardRate(rawRate, uId)
		target.setRatePercent(ratePercent)
		target.setPitchPercent(pitchPercent)

		if (isKey) {
			updatePhase("key_echo")
			if (s?.skipSymbols == true && s.skipSymbolsList.isNotEmpty()) {
				val trimmedKey = rawText.trim()
				if (s.skipSymbolsList.contains(trimmedKey) || SkipSymbols.isAllSkipped(rawText, s.skipSymbolsList)) {
					callback.start(target.sampleRateHz, AudioFormat.ENCODING_PCM_16BIT, 1)
					callback.done()
					markUtteranceIdle()
					AppLogger.logTiming(uId, "key_echo_skip_symbol", SystemClock.uptimeMillis() - t0, "Skipped key symbol: $rawText")
					return
				}
			}
			val now = SystemClock.uptimeMillis()
			val lastKey = lastAnnouncedKey
			if (lastKey != null && (now - lastAnnouncedKeyTime) <= KeyEcho.DIRECT_TOUCH_DEDUP_MS && KeyEcho.isSameKey(lastKey, rawText)) {
				callback.start(target.sampleRateHz, AudioFormat.ENCODING_PCM_16BIT, 1)
				callback.done()
				markUtteranceIdle()
				AppLogger.logTiming(uId, "key_echo_dedup", SystemClock.uptimeMillis() - t0, "Consumed duplicate key")
				return
			}

			isKeySynthesisInProgress = true
			try {
				var textToSpeak = KeyEcho.resolveKeySpeechText(rawText)
				val cs = activeCaseSensitiveEntries
				if (cs.isNotEmpty()) {
					textToSpeak = Dictionaries.applyCaseSensitiveDict(textToSpeak, cs)
				}
				val pauses = s?.pauses ?: Pauses.KEEP
				val isHindi = target.language == 0x00120000 || HindiPhonetics.containsDevanagari(textToSpeak)
				val prosody = if (isHindi) "" else Prosody.prefix(s?.phrasePrediction ?: false)
				val paused = Pauses.apply(textToSpeak, pauses, true)
				val piece = prosody + paused
				if (!target.speak(piece)) {
					AppLogger.e(TAG, "Key speak failed for utterance #$uId")
					callback.error(TextToSpeech.ERROR_SYNTHESIS)
					markUtteranceIdle()
					return
				}
				val buf = ByteArray(2048)
				var prepared = false
				while (true) {
					val n = target.read(buf)
					if (n <= 0) break
					if (!prepared) {
						prepareKeyAudio(target.sampleRateHz)
						prepared = true
					}
					writeKeyAudioChunk(buf, n)
				}
				lastAnnouncedKey = rawText
				lastAnnouncedKeyTime = SystemClock.uptimeMillis()
				callback.start(target.sampleRateHz, AudioFormat.ENCODING_PCM_16BIT, 1)
				callback.done()
				markUtteranceIdle()
				AppLogger.logUtteranceComplete(uId, SystemClock.uptimeMillis() - t0, 0, 1)
				return
			} finally {
				isKeySynthesisInProgress = false
				markUtteranceIdle()
			}
		} else {
			stopKeyAudio()
			lastAnnouncedKey = null
			lastAnnouncedKeyTime = 0L
		}

		if (stopped) {
			callback.error(TextToSpeech.STOPPED)
			markUtteranceIdle()
			AppLogger.logUtteranceComplete(uId, SystemClock.uptimeMillis() - t0, 0, 0)
			return
		}

		updatePhase("preprocessing")
		val tPreStart = SystemClock.uptimeMillis()
		val isPunctNoneOrSome = (s?.readPunctuation != true) ||
			(s.punctuationLevel == Punctuation.LEVEL_NONE || s.punctuationLevel == Punctuation.LEVEL_SOME)
		var textToProcess = rawText
		val cs = activeCaseSensitiveEntries
		if (cs.isNotEmpty()) {
			textToProcess = Dictionaries.applyCaseSensitiveDict(textToProcess, cs)
		}
		if (s?.skipSymbols == true && s.skipSymbolsList.isNotEmpty()) {
			textToProcess = SkipSymbols.apply(textToProcess, s.skipSymbolsList)
			if (textToProcess.isEmpty()) {
				callback.start(target.sampleRateHz, AudioFormat.ENCODING_PCM_16BIT, 1)
				callback.done()
				markUtteranceIdle()
				AppLogger.logUtteranceComplete(uId, SystemClock.uptimeMillis() - t0, 0, 0)
				return
			}
		}
		if (s?.countRepeatedChars == true) {
			textToProcess = RepeatedCharacters.apply(textToProcess, target.language)
		}
		if (isPunctNoneOrSome && textToProcess.contains(':')) {
			textToProcess = TIME_WITH_NEWLINE_REGEX.replace(textToProcess, "$1, \n")
		}
		val emojiText = if (s?.readEmoji != false) Emoji.apply(textToProcess) else textToProcess
		val camelCased = if (s?.splitCamelCase == true) CamelCase.apply(emojiText) else emojiText
		val numText = if (s?.processNumbers == true) Numbers.apply(camelCased, s.numberMode) else camelCased
		val punctProcessed = if (s?.readPunctuation == true) {
			Punctuation.apply(numText, s.punctuationLevel, target.language)
		} else {
			Punctuation.apply(numText, Punctuation.LEVEL_NONE, target.language)
		}
		val filteredText = if (s?.enableBackquoteVoiceTags == true) punctProcessed else punctProcessed.replace('`', ' ')
		val text = TextFixes.apply(filteredText, target.language)
		val preDuration = SystemClock.uptimeMillis() - tPreStart
		AppLogger.logTiming(uId, "preprocessing", preDuration, "textLen=${text.length}")

		if (stopped) {
			callback.error(TextToSpeech.STOPPED)
			markUtteranceIdle()
			AppLogger.logUtteranceComplete(uId, SystemClock.uptimeMillis() - t0, 0, 0)
			return
		}

		if (text.isEmpty()) {
			callback.start(target.sampleRateHz, AudioFormat.ENCODING_PCM_16BIT, 1)
			callback.done()
			markUtteranceIdle()
			AppLogger.logUtteranceComplete(uId, SystemClock.uptimeMillis() - t0, 0, 0)
			return
		}

		// Standard synthesis path for non-key utterances
		updatePhase("synthesizing")
		val opening = Opening(callback, target.sampleRateHz)
		val pace = Pace(target.sampleRateHz * BYTES_PER_SAMPLE)
		val pauses = s?.pauses ?: Pauses.KEEP
		val isHindi = target.language == 0x00120000 || HindiPhonetics.containsDevanagari(text)
		val prosody = if (isHindi) "" else Prosody.prefix(s?.phrasePrediction ?: false)
		val pieces = TextPieces.splitPieces(text)
		if (stopped) {
			callback.error(TextToSpeech.STOPPED)
			markUtteranceIdle()
			AppLogger.logUtteranceComplete(uId, SystemClock.uptimeMillis() - t0, 0, 0)
			return
		}
		opening.open()
		var totalChunks = 0
		for ((at, pieceObj) in pieces.withIndex()) {
			if (stopped) break
			val isLast = (at == pieces.lastIndex)
			val paused = Pauses.apply(pieceObj.text, pauses, isLast, pieceObj.isSentenceEnd, pieceObj.isClauseEnd)
			val piece = prosody + paused
			if (stopped) break
			val tPieceSpeak = SystemClock.uptimeMillis()
			if (!target.speak(piece)) {
				if (stopped) {
					// Preempted by user swipe while wait_until_idle was settling; not an engine error
					break
				}
				AppLogger.e(TAG, "Engine refused piece length ${piece.length} for utterance #$uId")
				callback.error(TextToSpeech.ERROR_SYNTHESIS)
				markUtteranceIdle()
				return
			}
			AppLogger.logTiming(uId, "target_speak", SystemClock.uptimeMillis() - tPieceSpeak, "len=${piece.length}")
			if (stopped) {
				target.stop()
				break
			}
			updatePhase("pumping_audio")
			val isContinuation = (!isLast && !pieceObj.isSentenceEnd)
			val chunks = pump(target, callback, pace, opening, uId, trimTrailingSilence = isContinuation)
			totalChunks += chunks
			if (stopped) {
				break
			}
		}
		remember(pace)
		val totalDuration = SystemClock.uptimeMillis() - t0
		if (stopped) {
			callback.error(TextToSpeech.STOPPED)
		} else if (opening.began) {
			callback.done()
		} else {
			callback.error(TextToSpeech.STOPPED)
		}
		markUtteranceIdle()
		AppLogger.logUtteranceComplete(uId, totalDuration, pace.handedMs(), totalChunks)
	}

	/** Hands one piece's samples on as they arrive, and answers whether the
	 *  caller may go on to the next. */
	private fun pump(
		target: EvvEngine,
		callback: SynthesisCallback,
		pace: Pace,
		opening: Opening,
		uId: Long,
		trimTrailingSilence: Boolean = false
	): Int {
		val size = callback.maxBufferSize.coerceIn(MIN_CHUNK, MAX_CHUNK)
		val buffer = ByteArray(size)
		var chunks = 0
		val tPumpStart = SystemClock.uptimeMillis()
		var pendingBuffer: ByteArray? = null
		var pendingLength = 0

		while (!stopped) {
			val n = target.read(buffer)
			if (n <= 0) break
			chunks++
			if (!opening.open()) {
				target.stop()
				return chunks
			}
			if (chunks == 1) {
				AppLogger.logTiming(uId, "TTFB (Time To First Byte)", SystemClock.uptimeMillis() - tPumpStart)
			}
			if (trimTrailingSilence) {
				if (pendingBuffer != null && pendingLength > 0) {
					if (callback.audioAvailable(pendingBuffer, 0, pendingLength) != TextToSpeech.SUCCESS) {
						target.stop()
						return chunks
					}
					pace.handed(pendingLength)
					hold(pace)
				}
				if (pendingBuffer == null || pendingBuffer.size < size) {
					pendingBuffer = ByteArray(size)
				}
				System.arraycopy(buffer, 0, pendingBuffer, 0, n)
				pendingLength = n
			} else {
				if (callback.audioAvailable(buffer, 0, n) != TextToSpeech.SUCCESS) {
					target.stop()
					return chunks
				}
				pace.handed(n)
				hold(pace)
			}
		}

		if (trimTrailingSilence && pendingBuffer != null && pendingLength > 0 && !stopped) {
			var validBytes = pendingLength
			while (validBytes >= 2) {
				val low = pendingBuffer[validBytes - 2].toInt() and 0xFF
				val high = pendingBuffer[validBytes - 1].toInt()
				val sample = (high shl 8) or low
				if (Math.abs(sample) > 64) {
					break
				}
				validBytes -= 2
			}
			if (validBytes > 0) {
				if (callback.audioAvailable(pendingBuffer, 0, validBytes) != TextToSpeech.SUCCESS) {
					target.stop()
					return chunks
				}
				pace.handed(validBytes)
				hold(pace)
			}
		}

		if (stopped) {
			target.stop()
		}
		return chunks
	}

	private fun hold(pace: Pace) {
		while (!stopped) {
			val ahead = pace.ahead() - LEAD_MS
			if (ahead <= 0) return
			val waitMs = minOf(ahead, SLICE_MS)
			synchronized(stopLock) {
				if (stopped) return
				try {
					stopLock.wait(waitMs)
				} catch (stopping: InterruptedException) {
					Thread.currentThread().interrupt()
					return
				}
			}
		}
	}

	private fun remember(pace: Pace) {
		if (pace.handedMs() > mostAudioHandedMs) mostAudioHandedMs = pace.handedMs()
	}

	/** Tells the framework an utterance has begun, once, and not before there
	 *  is something to hear. [began] answers whether it was ever told, which is
	 *  what decides whether it is owed a finish. */
	private class Opening(private val callback: SynthesisCallback, private val rateHz: Int) {
		var began = false
			private set

		fun open(): Boolean {
			if (began) return true
			began = callback.start(rateHz, AudioFormat.ENCODING_PCM_16BIT, 1) == TextToSpeech.SUCCESS
			return began
		}
	}

	/** How far the audio handed over has run ahead of the time it takes to
	 *  play it. */
	private class Pace(private val bytesPerSecond: Int) {
		private val from = System.nanoTime()
		private var bytes = 0L

		fun handed(more: Int) {
			bytes += more
		}

		fun handedMs(): Long = if (bytesPerSecond <= 0) 0 else bytes * 1000L / bytesPerSecond

		fun ahead(): Long = handedMs() - (System.nanoTime() - from) / 1_000_000L
	}

	internal companion object {
		private val DIGIT_COLON_REGEX = Regex("""(?<=\d)\s*:\s*(?=\d)""")
		private val PARENS_PAUSE_REGEX = Regex("""\s*[()\[\]{}]\s*""")
		val TIME_WITH_NEWLINE_REGEX = Regex("""\b(\d{1,2}:\d{2}(?::\d{2})?(?:\s*[\u202F\u00A0]?\s*[AaPp]\.?[Mm]\.?)?)\s*[\r\n]+\s*(?=\S)""")

		@Volatile
		var mostAudioHandedMs: Long = 0

		const val TAG = "evvdroid"
		const val MIN_CHUNK = 256
		const val MAX_CHUNK = 8192
		const val BYTES_PER_SAMPLE = 2
		const val LEAD_MS = 500L
		const val SLICE_MS = 10L

		/** Rate guard: incoming rate must be at least this percentage of the
		 *  stable rate to pass without intervention (70 = tolerate up to 30% drop). */
		const val RATE_GUARD_DROP_THRESHOLD = 70

		/** Rate guard: how many consecutive utterances at the lower rate before
		 *  the guard accepts it as a deliberate change. */
		const val RATE_GUARD_GRACE = 3

		/** Rate guard: extended grace period when incoming rate is Android's
		 *  uninitialized default rate (100) following a screen-reader crash/reconnect. */
		const val RATE_GUARD_UNINITIALIZED_GRACE = 10
	}
}

