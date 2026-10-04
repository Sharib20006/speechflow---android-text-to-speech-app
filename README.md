# speechFlow

**speechFlow** is an Android text-to-speech (TTS) engine that provides seamless access to the popular **ETI Eloquence / IBM TTS voice**, based on the **Evdroid** project.

It includes several major enhancements focused on adding authentic **Hindi along with Urdu** language support, upgrading the core **ECI engine to v6.1** (the exact same engine used by Eloquence synthesizer on NVDA and JAWS), delivering authentic **16 kHz audio**, reducing speech latency, and providing a comfortable, highly customizable experience for screen reader users and everyday communication.

**Modder:** Syed Sharib Ali

---

## Key Features & Highlights

### 1. Hindi along with Urdu Support (Hindi + Urdu)

- **Urdu Language Support**: Urdu is now officially part of speechFlow. Speaks Urdu naturally, clearly, and expressively with authentic Urdu phonetic sounds (including voiceless velar fricatives [kh / خ], voiced velar fricatives [gh / غ], uvular stops [q / ق], retroflex flaps [ṛ / ڑ], and nasalization [Noon Ghunna / ں]).
- **Hindi Language Support**: Clear, authentic Hindi pronunciation in the classic Eloquence voice, speaking everyday Hindi text, Indian numbering formats, continuous decimals, percentages, and English loanwords written in Devanagari naturally.
- **Poetic & Prose Cadence**: Natural prosodic phrasing and cadence for both Hindi and Urdu literature, poetry (Ghazal/Nazm couplet pauses), and conversational text.
- **Diacritics & Punctuation Support**: Full support for Urdu punctuation (Khatma, Sakta, Waqfa, Sawaliya) and Devanagari/Urdu diacritics.

### 2. Romanized Hindi & Urdu Support

- Major clarity and naturalness improvements for Hindi and Urdu written in Roman script (Latin alphabet).
- Romanized Hindi and Romanized Urdu now sound clearer, more natural, and significantly better articulated with authentic pronunciation rules instead of sounding like disjointed English words.

### 3. Updated ECI Engine (v6.1) & Authentic 16 kHz Audio

- **ECI Engine v6.1**: Features an updated **ECI (Embedded Command Interface) v6.1** engine — the exact same engine used by the Eloquence synthesizer on NVDA and JAWS desktop screen readers.
- **Authentic 16 kHz Sampling**: True 16 kHz audio sampling is now fully supported alongside classic 11,025 Hz and 8,000 Hz outputs, delivering crisper, high-fidelity audio quality.
- **Zero Click & Swipe Delay**: Immediate, snappy speech response with zero delay when clicking, tapping, or swiping across items with screen readers such as TalkBack and Commentary Screen Reader (Jieshuo).
- **Buffer Optimization**: Enhanced audio buffer dispatch and adaptive power-down when idle, providing maximum battery efficiency.

### 4. Dedicated Hindi + Urdu Settings

- **Separate Speech Rate**: Set Hindi and Urdu speech speed independently from the default English/Western speech rate.
- **Hindi/Urdu Detection**: Automatically detect and switch to Hindi and Urdu speech when encountering Devanagari or Urdu script, with the option to disable auto-detection for manual control.
- **Roman Numerals Toggle**: Enable or disable Roman numeral processing directly from Settings.

### 5. Automatic & Manual Update System

- **Check for Updates on Startup**: Automatically checks for new releases on GitHub when opening the app, presenting version details and changelog.
- **Manual "Check for Updates" Button**: An always-visible button in settings to check for new updates on demand.
- **Architecture-Aware Downloads**: Automatically detects whether your device is 64-bit or 32-bit:
  - 64-bit devices (`arm64-v8a`): Downloads the optimized 64-bit APK.
  - 32-bit devices (`armeabi-v7a`): Downloads the 32-bit APK.
  - Universal fallback: Automatically downloads the Universal APK if needed.
- **In-App Download & Installation**: Real-time download progress with options to download in the background, install directly, or save the APK to your files.

### 6. Force Language & Speech Controls

- **Force Language**: Lock speech output to your preferred language (US English, UK English, Spanish, French, German, Italian, Polish, Japanese, Hindi, Urdu) so other apps cannot unexpectedly change the language.
- **Force Speech Rate & Force Pitch**: Lock your preferred talking speed and pitch so third-party applications and screen readers cannot alter your settings.
- **Sample Rate Selection**: Choose between authentic 16,000 Hz, standard 11,025 Hz, and classic 8,000 Hz audio output.
- **Pause Shortening**: JAWS-style pause trimming to remove unnecessary pauses at the end of sentences and shorten punctuation delays for a snappier screen-reading experience.

### 7. Dictionaries, Community Dictionary & Case-Sensitivity

- **User Dictionary Management**: Easily add, edit, search, and delete custom word pronunciations right from the settings.
- **Dictionary Case-Sensitivity (Sensitive Word Support)**: Choose whether a custom dictionary entry requires an exact case match (for example, reading capitalized `IT` differently from `it`) or applies case-insensitively.
- **Community Dictionary Support**: Includes a built-in community dictionary that can be enabled or disabled with a single switch.
- **Language-Specific Dictionaries**: Create and manage separate pronunciation dictionaries for each individual language.

### 8. Filter Typing Echo

- Support for filtering typing echo for users who rely on direct-touch typing.
- Eliminates duplicate announcements of typed keys and prevents voice stuttering when typing quickly.

### 9. Intelligent Number & Roman Numeral Processing

- **Number Reading Modes**: Customize how numbers are announced:
  - Normal
  - Digits (reads numbers digit by digit)
  - Pairs (groups numbers in pairs)
  - Triplets (groups numbers in threes)
  - Words (reads numbers as full spoken words)
- **Smooth Time Reading**: Natural 12-hour and 24-hour time formatting.
- **Roman Numeral Reading**: Automatically recognizes and speaks Roman numerals in titles, chapters, and dates (e.g. *"Chapter IV"*, *"King Henry VIII"*).

### 10. Emoji 17.0 Reading Support

- Comprehensive emoji reading with spoken descriptive feedback for emojis, including full support for modern **Emoji 17.0** emojis.

### 11. Punctuation, Skip Symbols & Hashtag Reading

- **Punctuation Verbosity**: Adjust punctuation reading levels between *None*, *Some*, *Most*, and *All*.
- **Skip Symbols**: Configure specific symbols or characters to be completely silenced and ignored during speech.
- **Context-Sensitive Hashtags**: Pronounces hashtags naturally (e.g. `#speechFlow` is read as *"hashtag speechFlow"*).
- **Context-Sensitive Pronunciation Handling**: Smarter context rules for common ambiguous words and heteronyms.

### 12. Camel Case Normalisation

- Provides an option for camel case normalisation.
- Useful for users who frequently encounter camel case text (e.g., `ThisIsCamelCaseText` is read as separate, natural words).

### 13. Repeating Character Support

- Supports counting repeated characters instead of announcing the same character repeatedly (e.g. announces *"5 dots"* instead of repeating *"dot dot dot dot dot"*).

### 14. Original Speech Options Retained

- Retains all classic voices (**Reed, Shelley, Bobby, Rocko, Glen, Sandy, Grandma, Grandpa**) and parameters (pitch, speed, inflection, head size, roughness, breathiness, volume, gender).

### 15. Dual-Layer Security & Stability

- Advanced dual-layer cryptographic signature verification (Kotlin application layer + native C library) ensuring APK integrity.
- Major stability pass fixing Backquote Voice Tag parameter leakage, rapid swiping latency, and speech queue stability.

---

## Credits and Acknowledgements

A huge thank you and credit goes to the respected developer of the **[Evdroid](https://github.com/trypsynth/evvdroid)** project for bringing the popular ETI Eloquence / IBM TTS voice to the Android community.

Credits also go to the **[openevv](https://github.com/Mudb0y/openevv)** project for the reverse-engineered engine.

Special thanks also go to the open-source **eSpeak** and language research communities for helping make Hindi along with Urdu phoneme and pronunciation support possible.

**Huge thanks to:**
- **Sachin Bariya**
- **Rosendo Hubilla**
- **Syed Yusuf Ali**

---

## About This Project

speechFlow is a modified and enhanced version based on the Evdroid project. Its goal is to provide a smoother, more accessible, and more customisable speech experience while retaining the original voice and speech options.

The project focuses on:
- Authentic, clear **Hindi along with Urdu** speech synthesis
- Updated **ECI engine v6.1** matching desktop NVDA/JAWS
- Authentic **16 kHz** high-quality sampling rate
- Immediate, delay-free response when swiping or clicking with screen readers
- Comprehensive update and dictionary system
- Improved text processing and typing echo filtering
- Flexible dictionary support and organized settings

---

## Modder

**Syed Sharib Ali**

Thank you for using speechFlow and supporting the project.

**Regards,**  
**Sharib**
