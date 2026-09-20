package org.evvdroid

import android.content.Context
import android.content.SharedPreferences

/**
 * What the settings screen wrote, read back for the engine.
 *
 * Voice settings are kept per voice. Reed at pitch 55 is still at pitch 55
 * after a trip through Glen and back, and stays that way until Reed is reset.
 * Speed is the exception and is kept once for everybody: six of the eight
 * presets ship at engine speed 50 and Glen and Sandy ship at 70, so a speed
 * that followed the voice would change the pace every time the voice changed.
 *
 * Nothing is stored until someone chooses it. That is the whole point rather
 * than a detail: every preset carries its own head size, inflection and volume,
 * so a default of our own written over the top would make every voice wrong in
 * the same way, which is what it did once.
 *
 * A revision goes up whenever anything changes, so the service can tell whether
 * the instance it is holding is still configured the way the user left it
 * without comparing every value.
 */
class Settings(context: Context) {

	// The file androidx.preference used, kept under that name so a build with
	// the old screen and a build with this one read the same settings.
	private val prefs: SharedPreferences = context.applicationContext
		.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

	@Volatile
	var revision: Int = 0
		private set

	private val watcher = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }

	init {
		carryOverTheOldKeys()
		prefs.registerOnSharedPreferenceChangeListener(watcher)
	}

	var voice: Int
		get() = prefs.getInt(KEY_VOICE, 0).coerceIn(0, Eci.PRESET_NAMES.lastIndex)
		set(value) = prefs.edit().putInt(KEY_VOICE, value).apply()

	var sampleRateHz: Int
		get() {
			val hz = prefs.getInt(KEY_SAMPLE_RATE, DEFAULT_SAMPLE_RATE)
			return if (hz > 11025 || !ALLOWED_SAMPLE_RATES.contains(hz)) DEFAULT_SAMPLE_RATE else hz
		}
		set(value) {
			val valid = if (value > 11025 || !ALLOWED_SAMPLE_RATES.contains(value)) DEFAULT_SAMPLE_RATE else value
			prefs.edit().putInt(KEY_SAMPLE_RATE, valid).apply()
		}

	/** The abbreviation dictionary. Off by default: it expands what it takes
	 *  for an abbreviation whether that was wanted or not. */
	var abbreviations: Boolean
		get() = prefs.getBoolean(KEY_ABBREVIATIONS, false)
		set(value) = prefs.edit().putBoolean(KEY_ABBREVIATIONS, value).apply()

	/** How much of the engine's own pausing to keep. One of the Pauses
	 *  constants, and not a voice's business. Default is Pauses.KEEP (do not shorten). */
	var pauses: Int
		get() = prefs.getInt(KEY_PAUSES, Pauses.KEEP).coerceIn(Pauses.KEEP, Pauses.ALL)
		set(value) = prefs.edit().putInt(KEY_PAUSES, value).apply()

	/** Whether the engine guesses at phrase boundaries and shapes the
	 *  intonation to match. Off: a screen reader's line is usually a fragment
	 *  and the guess is wrong. */
	var phrasePrediction: Boolean
		get() = prefs.getBoolean(KEY_PHRASE_PREDICTION, false)
		set(value) = prefs.edit().putBoolean(KEY_PHRASE_PREDICTION, value).apply()

	/** Whether to filter out duplicate keystroke announcements caused by direct
	 *  touch typing when explore by touch is suspended or off. On by default. */
	var debounceKeyEcho: Boolean
		get() = prefs.getBoolean(KEY_DEBOUNCE_KEY_ECHO, true)
		set(value) = prefs.edit().putBoolean(KEY_DEBOUNCE_KEY_ECHO, value).apply()

	/** The app display theme: System (0), Dark (1), Light (2). */
	var themeMode: Int
		get() = prefs.getInt(KEY_THEME_MODE, ThemeMode.SYSTEM).coerceIn(ThemeMode.SYSTEM, ThemeMode.LIGHT)
		set(value) = prefs.edit().putInt(KEY_THEME_MODE, value).apply()

	/** Whether to split CamelCase words (e.g. "helloWorld" -> "hello World"). */
	var splitCamelCase: Boolean
		get() = prefs.getBoolean(KEY_SPLIT_CAMEL_CASE, false)
		set(value) = prefs.edit().putBoolean(KEY_SPLIT_CAMEL_CASE, value).apply()

	/** Whether to announce repeated characters (e.g. "////" -> "4 slash"). Off by default. */
	var countRepeatedChars: Boolean
		get() = prefs.getBoolean(KEY_COUNT_REPEATED_CHARS, false)
		set(value) = prefs.edit().putBoolean(KEY_COUNT_REPEATED_CHARS, value).apply()

	/** Whether embedded backquote voice tags (e.g. `v1, `vb50, `p100) are enabled. Off by default. */
	var enableBackquoteVoiceTags: Boolean
		get() = prefs.getBoolean(KEY_BACKQUOTE_VOICE_TAGS, false)
		set(value) = prefs.edit().putBoolean(KEY_BACKQUOTE_VOICE_TAGS, value).apply()

	/** Whether to read Unicode 17 emojis with spoken descriptions. On by default. */
	var readEmoji: Boolean
		get() = prefs.getBoolean(KEY_READ_EMOJI, true)
		set(value) = prefs.edit().putBoolean(KEY_READ_EMOJI, value).apply()

	/** Whether to read punctuation symbols. On by default. */
	var readPunctuation: Boolean
		get() = prefs.getBoolean(KEY_READ_PUNCTUATION, true)
		set(value) = prefs.edit().putBoolean(KEY_READ_PUNCTUATION, value).apply()

	/** Punctuation verbosity level: Some (0), Most (1), All (2), None (3). Default is Some (0). */
	var punctuationLevel: Int
		get() = prefs.getInt(KEY_PUNCTUATION_LEVEL, Punctuation.LEVEL_SOME).coerceIn(Punctuation.LEVEL_SOME, Punctuation.LEVEL_NONE)
		set(value) = prefs.edit().putInt(KEY_PUNCTUATION_LEVEL, value).apply()

	/** Whether to process numbers into custom spoken groups. Off by default. */
	var processNumbers: Boolean
		get() = prefs.getBoolean(KEY_PROCESS_NUMBERS, false)
		set(value) = prefs.edit().putBoolean(KEY_PROCESS_NUMBERS, value).apply()

	/** Number processing mode: Digits (0), Pairs (1), Triplets (2), Words (3). Default is Digits (0). */
	var numberMode: Int
		get() = prefs.getInt(KEY_NUMBER_MODE, Numbers.MODE_DIGITS).coerceIn(Numbers.MODE_DIGITS, Numbers.MODE_WORDS)
		set(value) = prefs.edit().putInt(KEY_NUMBER_MODE, value).apply()

	/** Whether to force speech rate from within the app. Off by default (system controls rate). */
	var forceSpeechRate: Boolean
		get() = prefs.getBoolean(KEY_FORCE_SPEECH_RATE, false)
		set(value) = prefs.edit().putBoolean(KEY_FORCE_SPEECH_RATE, value).apply()

	/** Whether to force pitch from within the app. Off by default (system controls pitch). */
	var forcePitch: Boolean
		get() = prefs.getBoolean(KEY_FORCE_PITCH, false)
		set(value) = prefs.edit().putBoolean(KEY_FORCE_PITCH, value).apply()

	/** Whether to force speech language from within the app. Off by default. */
	var forceLanguage: Boolean
		get() = prefs.getBoolean(KEY_FORCE_LANGUAGE, false)
		set(value) = prefs.edit().putBoolean(KEY_FORCE_LANGUAGE, value).apply()

	/** The language forced when forceLanguage is on. Default 0x00010000 (US English). */
	var forcedLanguage: Int
		get() = prefs.getInt(KEY_FORCED_LANGUAGE, 0x00010000)
		set(value) = prefs.edit().putInt(KEY_FORCED_LANGUAGE, value).apply()

	/** Whether to skip user-specified symbols and characters during speech. Off by default. */
	var skipSymbols: Boolean
		get() = prefs.getBoolean(KEY_SKIP_SYMBOLS, false)
		set(value) = prefs.edit().putBoolean(KEY_SKIP_SYMBOLS, value).apply()

	/** The set of symbols, characters, or text elements to skip. */
	var skipSymbolsList: Set<String>
		get() = prefs.getStringSet(KEY_SKIP_SYMBOLS_SET, emptySet()) ?: emptySet()
		set(value) = prefs.edit().putStringSet(KEY_SKIP_SYMBOLS_SET, HashSet(value)).apply()

	fun addSkipSymbol(symbol: String) {
		val trimmed = symbol.trim()
		if (trimmed.isEmpty()) return
		val current = HashSet(skipSymbolsList)
		current.add(trimmed)
		skipSymbolsList = current
	}

	fun removeSkipSymbol(symbol: String) {
		val current = HashSet(skipSymbolsList)
		current.remove(symbol)
		skipSymbolsList = current
	}

	/** Whether the first run welcome dialog has been dismissed. False by default. */
	var firstRunDismissed: Boolean
		get() = prefs.getBoolean(KEY_FIRST_RUN_DISMISSED, false)
		set(value) = prefs.edit().putBoolean(KEY_FIRST_RUN_DISMISSED, value).apply()

	/** How many times the application has been launched. */
	var appLaunchCount: Int
		get() = prefs.getInt(KEY_APP_LAUNCH_COUNT, 0)
		set(value) = prefs.edit().putInt(KEY_APP_LAUNCH_COUNT, value).apply()

	/** Whether the user checked "Don't show again" on the default TTS dialog. */
	var dontShowDefaultTtsDialog: Boolean
		get() = prefs.getBoolean(KEY_DONT_SHOW_DEFAULT_TTS_DIALOG, false)
		set(value) = prefs.edit().putBoolean(KEY_DONT_SHOW_DEFAULT_TTS_DIALOG, value).apply()

	/** Whether diagnostic logging is enabled. Off by default. */
	var enableLogging: Boolean
		get() = prefs.getBoolean(KEY_ENABLE_LOGGING, false)
		set(value) = prefs.edit().putBoolean(KEY_ENABLE_LOGGING, value).apply()

	/** The last known stable speech rate across restarts, used by the rate stability guard. */
	var lastStableRate: Int
		get() = prefs.getInt(KEY_LAST_STABLE_RATE, -1)
		set(value) = prefs.edit().putInt(KEY_LAST_STABLE_RATE, value).apply()

	/** One speed for every voice. */
	var speed: Int
		get() = Eci.clampVoice(Eci.VOICE_SPEED, prefs.getInt(KEY_SPEED, Eci.DEFAULT_SPEED))
		set(value) = prefs.edit().putInt(KEY_SPEED, Eci.clampVoice(Eci.VOICE_SPEED, value)).apply()

	// ---- one voice's own settings ----------------------------------------

	fun shapeValue(voice: Int, param: Int): Int? {
		if (param == Eci.VOICE_SPEED) return speed
		val key = keyOf(voice, param)
		return if (prefs.contains(key)) Eci.clampVoice(param, prefs.getInt(key, 0)) else null
	}

	fun setShapeValue(voice: Int, param: Int, value: Int) {
		if (param == Eci.VOICE_SPEED) {
			speed = value
			return
		}
		prefs.edit().putInt(keyOf(voice, param), Eci.clampVoice(param, value)).apply()
	}

	/** What has been chosen for [voice], by the number eciSetVoiceParam takes.
	 *  What is not customized falls back to the clean canonical [VoiceProfiles] default. */
	fun shape(voice: Int): Map<Int, Int> {
		val out = LinkedHashMap<Int, Int>()
		val defaults = VoiceProfiles.defaultShape(voice)
		for (param in PER_VOICE) {
			val key = keyOf(voice, param)
			if (prefs.contains(key)) {
				out[param] = Eci.clampVoice(param, prefs.getInt(key, 0))
			} else {
				defaults[param]?.let { out[param] = it }
			}
		}
		out[Eci.VOICE_SPEED] = speed
		return out
	}

	fun writeShape(voice: Int, values: Map<Int, Int>) {
		val edit = prefs.edit()
		for (param in PER_VOICE) values[param]?.let { edit.putInt(keyOf(voice, param), it) }
		edit.apply()
		values[Eci.VOICE_SPEED]?.let { speed = it }
	}

	/** Whether [voice] has been changed from what it came with. */
	fun shapeIsCustom(voice: Int): Boolean = PER_VOICE.any { prefs.contains(keyOf(voice, it)) }

	/** Forgets one voice's changes. The speed is everybody's and stays. */
	fun clearShape(voice: Int) {
		val edit = prefs.edit()
		for (param in PER_VOICE) edit.remove(keyOf(voice, param))
		edit.apply()
	}

	private fun keyOf(voice: Int, param: Int) = "voice${voice}_param$param"

	/** Settings written when there was one set of them for every voice. They go
	 *  to whichever voice was in force at the time, which is where they were
	 *  heard. */
	private fun carryOverTheOldKeys() {
		val old = Eci.NUM_VOICE_PARAMS.let { 0 until it }.map { "voice_param_$it" }
		if (old.none { prefs.contains(it) }) return
		val edit = prefs.edit()
		val was = prefs.getInt(KEY_VOICE, 0).coerceIn(0, Eci.PRESET_NAMES.lastIndex)
		for (param in 0 until Eci.NUM_VOICE_PARAMS) {
			val from = "voice_param_$param"
			if (!prefs.contains(from)) continue
			val value = prefs.getInt(from, 0)
			if (param == Eci.VOICE_SPEED) {
				edit.putInt(KEY_SPEED, Eci.clampVoice(param, value))
			} else {
				edit.putInt(keyOf(was, param), Eci.clampVoice(param, value))
			}
			edit.remove(from)
		}
		edit.apply()
	}

	// ---- dictionaries ----------------------------------------------------

	fun dictionaryPath(volume: Int): String? = prefs.getString("dict_path_$volume", null)

	fun dictionaryName(volume: Int): String? = prefs.getString("dict_name_$volume", null)

	fun setDictionary(volume: Int, path: String?, name: String?) {
		val edit = prefs.edit()
		if (path == null) {
			edit.remove("dict_path_$volume").remove("dict_name_$volume")
		} else {
			edit.putString("dict_path_$volume", path).putString("dict_name_$volume", name)
		}
		edit.apply()
	}

	/** Whether to use the built-in community dictionary as main dictionary. Default false. */
	var useCommunityDictionary: Boolean
		get() = prefs.getBoolean(KEY_USE_COMMUNITY_DICT, false)
		set(value) = prefs.edit().putBoolean(KEY_USE_COMMUNITY_DICT, value).apply()

	fun dictionaryPaths(): Map<Int, String> {
		val out = LinkedHashMap<Int, String>()
		for (volume in Eci.DICT_VOLUMES) dictionaryPath(volume)?.let { out[volume] = it }
		return out
	}

	/** Whether to use language-specific dictionaries alongside global ones. */
	var useLanguageDictionaries: Boolean
		get() = prefs.getBoolean(KEY_USE_LANGUAGE_DICTS, false)
		set(value) = prefs.edit().putBoolean(KEY_USE_LANGUAGE_DICTS, value).apply()

	fun langDictPath(eciLang: Int): String? = prefs.getString("lang_dict_path_%08x".format(eciLang), null)

	fun langDictName(eciLang: Int): String? = prefs.getString("lang_dict_name_%08x".format(eciLang), null)

	fun setLangDictionary(eciLang: Int, path: String?, name: String?) {
		val edit = prefs.edit()
		val pathKey = "lang_dict_path_%08x".format(eciLang)
		val nameKey = "lang_dict_name_%08x".format(eciLang)
		if (path == null) {
			edit.remove(pathKey).remove(nameKey)
		} else {
			edit.putString(pathKey, path).putString(nameKey, name)
		}
		edit.apply()
	}

	fun langDictionaryPaths(): Map<Int, String> {
		val out = LinkedHashMap<Int, String>()
		for (lang in EvvEngine.available) {
			langDictPath(lang)?.let { out[lang] = it }
		}
		return out
	}

	/** Whether to automatically check for updates on startup. Default true. */
	var checkForUpdatesOnStartup: Boolean
		get() = prefs.getBoolean(KEY_CHECK_FOR_UPDATES_ON_STARTUP, true)
		set(value) = prefs.edit().putBoolean(KEY_CHECK_FOR_UPDATES_ON_STARTUP, value).apply()

	companion object {
		const val KEY_VOICE = "voice_preset"
		const val KEY_SAMPLE_RATE = "sample_rate_hz"
		const val KEY_ABBREVIATIONS = "abbreviations"
		const val KEY_SPEED = "speed"
		const val KEY_PAUSES = "pauses"
		const val KEY_PHRASE_PREDICTION = "phrase_prediction"
		const val KEY_DEBOUNCE_KEY_ECHO = "debounce_key_echo"
		const val KEY_THEME_MODE = "theme_mode"
		const val KEY_SPLIT_CAMEL_CASE = "split_camel_case"
		const val KEY_COUNT_REPEATED_CHARS = "count_repeated_chars"
		const val KEY_BACKQUOTE_VOICE_TAGS = "backquote_voice_tags"
		const val KEY_READ_EMOJI = "read_emoji"
		const val KEY_READ_PUNCTUATION = "read_punctuation"
		const val KEY_PUNCTUATION_LEVEL = "punctuation_level"
		const val KEY_PROCESS_NUMBERS = "process_numbers"
		const val KEY_NUMBER_MODE = "number_mode"
		const val KEY_FORCE_SPEECH_RATE = "force_speech_rate"
		const val KEY_FORCE_PITCH = "force_pitch"
		const val KEY_FORCE_LANGUAGE = "force_language"
		const val KEY_FORCED_LANGUAGE = "forced_language"
		const val KEY_SKIP_SYMBOLS = "skip_symbols"
		const val KEY_SKIP_SYMBOLS_SET = "skip_symbols_set"
		const val KEY_FIRST_RUN_DISMISSED = "first_run_dialog_shown"
		const val KEY_APP_LAUNCH_COUNT = "app_launch_count"
		const val KEY_DONT_SHOW_DEFAULT_TTS_DIALOG = "dont_show_default_tts_dialog"
		const val KEY_USE_COMMUNITY_DICT = "use_community_dict"
		const val KEY_USE_LANGUAGE_DICTS = "use_language_dicts"
		const val KEY_ENABLE_LOGGING = "enable_logging"
		const val KEY_LAST_STABLE_RATE = "last_stable_rate"
		const val KEY_CHECK_FOR_UPDATES_ON_STARTUP = "check_updates_startup"
		const val DEFAULT_SAMPLE_RATE = 11025
		val ALLOWED_SAMPLE_RATES = listOf(8000, 11025)
		const val DEFAULT_VOLUME = 100

		/** The eight, in the order the settings screen shows them. */
		val SHAPE = listOf(
			Eci.VOICE_GENDER,
			Eci.VOICE_SPEED,
			Eci.VOICE_PITCH_BASELINE,
			Eci.VOICE_PITCH_FLUCTUATION,
			Eci.VOICE_HEAD_SIZE,
			Eci.VOICE_ROUGHNESS,
			Eci.VOICE_BREATHINESS,
			Eci.VOICE_VOLUME
		)

		/** The seven that belong to a voice. Speed belongs to the listener. */
		val PER_VOICE = SHAPE.filter { it != Eci.VOICE_SPEED }

		/** The five sliders that are always shown for voice modulation. */
		val OTHER_SLIDERS = listOf(
			Eci.VOICE_PITCH_FLUCTUATION,
			Eci.VOICE_HEAD_SIZE,
			Eci.VOICE_ROUGHNESS,
			Eci.VOICE_BREATHINESS,
			Eci.VOICE_VOLUME
		)

		/** The seven that are a slider. Gender is a choice of two. */
		val SLIDERS = SHAPE.filter { it != Eci.VOICE_GENDER }
	}
}
