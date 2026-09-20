# SpeechFlow (speechFlow)

**SpeechFlow** is an advanced, high-performance Android text-to-speech (TTS) engine that brings the beloved **ETI Eloquence / IBM ViaVoice** speech synthesizer to modern Android devices and Wear OS. 

Based on the open-source [trypsynth/evvdroid](https://github.com/trypsynth/evvdroid) project and the [openevv](https://github.com/Mudb0y/openevv) engine, SpeechFlow has been extensively updated with modern speech features, updated ECI (Embedded Command Interface) capabilities, high-fidelity Hindi pronunciation, intelligent text normalisation, community dictionaries, and an automatic update system.

**Modder & Maintainer:** Syed Sharib Ali (Cyber Tech)

---

## What Makes SpeechFlow Special?

* **Classic Voices, Modern Reliability**: All eight classic Eloquence voices (**Reed, Shelley, Bobby, Rocko, Glen, Sandy, Grandma, Grandpa**) with authentic acoustics and full parameter control.
* **Updated ECI Architecture**: Upgraded IBM Eloquence Command Interface with improved buffer handling, voice tags support, and smoother synthesis dispatch.
* **First-Class Hindi Language Support**: Ground-up Devanagari acoustic modeling with authentic dental vs. retroflex distinction, continuous decimals, Indian numbering, and Devanagari English loanword processing.
* **Automatic GitHub Updates**: Built-in update detector that checks for new releases on startup and downloads the exact APK matching your device's architecture.
* **Screen Reader Optimised**: Built specifically for blind and visually impaired users relying on TalkBack, Jieshuo, and third-party tools like Auto TTS.

---

## Key Features

### 1. Automatic & Manual Update System
* **Automatic Update Checking**: When enabled, SpeechFlow checks GitHub for new releases each time the app starts. If an update is available, you will receive an update dialog with version details, file size, and the full changelog.
* **Manual "Check for Updates" Button**: An always-available button in settings allows you to check for the latest releases on demand.
* **Architecture-Aware Downloads**: 
  - **64-bit devices (`arm64-v8a`)**: Automatically downloads the optimized 64-bit APK (~10.8 MB).
  - **32-bit devices (`armeabi-v7a`)**: Automatically downloads the 32-bit APK (~10.6 MB).
  - **Universal Fallback**: If an architecture-specific file is unavailable, it seamlessly falls back to the Universal APK.
* **In-App Download & Installation**: Real-time progress indicator with percentage and megabytes downloaded, background download option, direct package installer launch, and an option to save the APK file to your storage.

### 2. Comprehensive Hindi Language Support
* **Authentic Hindi Phonetics**: Custom Devanagari acoustic translation engine integrated directly into the synthesis pipeline.
* **Pure Dental 'त' (`/t̪/`) vs Retroflex 'ट' (`/ʈ/`) Precision**: Clear phonetic separation between dental consonants (*तरबूज, तितली, तोता, ताला, तूफान, तापमान*) and retroflex consonants (*टमाटर, टोपी, ट्रैक्टर, टिकट, टेस्ट*).
* **Dental 'त' in Hindi Numbers**: Authentic dental pronunciation across numbers containing 'त' (such as 3, 7, 13, 17, 23, 27, 29, 30, 33, 47, 53, 57, 70).
* **Indian Numbering Format**: Large values like `1,51,519` are read naturally as *"एक लाख एकावन हज़ार पाँच सौ उन्नीस"*, and `61,312` as *"इकसठ हज़ार तीन सौ बारह"*.
* **Continuous Decimal & Percentage Flow**: Decimals such as `40.46%`, `9.9%`, and `22.78%` speak fluently without awkward hesitations.
* **Clean Loanword Processing**: English words written in Devanagari (like *यूनिवर्सिटी, स्टूडेंट्स, कैंडिडेट्स, एम्बुलेंस, आईसीयू, रोबोटिक्स, आर्टिफिशियल इंटेलिजेंस*) speak cleanly without exposed tokens or delimiter leaks.

### 3. Dictionaries & Pronunciation Customisation
* **User Dictionary Management**: Full in-app dictionary manager allowing you to search, add, edit, and delete custom pronunciations for any word.
* **Case-Sensitivity & Sensitive Word Support**: Customize whether a dictionary entry matches strictly with exact case sensitivity (e.g. distinguishing the abbreviation `IT` from the pronoun `it`) or matches case-insensitively.
* **Built-in Community Dictionary**: Pre-configured community dictionary (`community_main.dic` and `community_root.dic`) packed with thousands of common corrections that can be enabled or disabled with a single switch.
* **Language-Specific Dictionaries**: Separate custom user dictionaries for each supported language module, keeping pronunciation rules organized.

### 4. Speech, Pitch & Rate Controls
* **Force Speech Rate**: Lock the speaking speed so external applications and screen readers cannot unintentionally change your preferred pace.
* **Force Pitch**: Lock your preferred pitch across all apps and notifications.
* **Measured Non-Linear Speed Scaling**: Speech rate is calibrated across a realistic curve rather than raw multiplication, keeping voices natural at lower speeds and crisp and intelligible at very high speeds.
* **Sampling Rate Selection**: Choose between standard 11,025 Hz and classic 8,000 Hz sampling rates with real-time audio pipeline adjustment.
* **Pause Trimming (JAWS-style)**:
  - *Do not shorten*: Original engine pauses.
  - *Shorten at end of text*: Removes silence at the end of spoken items.
  - *Shorten all pauses*: Reduces comma and punctuation delays by ~35% for maximum screen-reading responsiveness.
* **Phrase Prediction**: Toggle the engine's intonation prediction on or off.

### 5. Language Selection & Force Language
* **Force Language**: Override incoming application locale requests and lock speech synthesis to your chosen language:
  - US English (`en-US`)
  - UK English (`en-GB`)
  - Spanish - Spain (`es-ES`) & Latin America (`es-US`)
  - French - France (`fr-FR`) & Canada (`fr-CA`)
  - German (`de-DE`)
  - Italian (`it-IT`)
  - Polish (`pl-PL`)
  - Japanese (`ja-JP`)
  - Hindi (`hi-IN`)

### 6. Intelligent Text Processing & Symbol Controls
* **Emoji 17.0 Support**: Full spoken descriptions for emojis, including the newest Unicode Emoji 17 specifications.
* **Intelligent Number Reading Modes**:
  - *Normal*: Standard numbers.
  - *Digits*: Reads each digit individually (e.g., "1-2-3").
  - *Pairs*: Groups digits in twos.
  - *Triplets*: Groups digits in threes.
  - *Words*: Speaks numbers as full words.
  - *Time Formatting*: Smooth 12-hour and 24-hour time reading.
* **Roman Numeral Pronunciation**: Automatically identifies and pronounces Roman numerals in titles, chapters, and names (e.g. *"Chapter IV"*, *"King Henry VIII"*).
* **Context-Sensitive Hashtag Pronunciation**: Correctly pronounces hashtags (`#SpeechFlow` speaks as *"hashtag SpeechFlow"*) without symbol stutter.
* **Context-Sensitive Heteronym Handling**: Smarter context rules for ambiguous English words (such as *read*, *lead*, *live*, *record*).
* **Skip Symbols**: Configure specific punctuation marks or symbols to be completely silenced during speech.
* **Symbol Reading Levels**: Easily toggle punctuation verbosity between *None*, *Some*, *Most*, and *All*.
* **CamelCase Normalisation**: Automatically splits concatenated phrases (e.g., `SpeechFlow`, `ActionBar`) into distinct words.
* **Repeated Characters Counter**: Announces repeated symbols concisely (e.g., *"5 dots"* instead of repeating *"dot dot dot dot dot"*).
* **Typing Echo Filter (Touch Typing)**: Filters duplicate keyboard echo events, ensuring clean, cut-free speech feedback when typing rapidly.

### 7. Organised Settings & Diagnostics
* Settings are logically grouped into dedicated screens:
  - **Speech Settings** (voice, speech rate, pitch, pauses, phrase prediction, sample rate, force language).
  - **Text Processing Settings** (numbers, emoji, punctuation, skip symbols, CamelCase, typing echo).
  - **Dictionary Settings** (community dictionary, user dictionaries, language-specific dictionaries).
* **Diagnostics & Logging**: Built-in debug logging with an easy "Share Log" action to export diagnostic logs for troubleshooting.

---

## How to Enable SpeechFlow

1. Open your device's **Settings**.
2. Navigate to **Accessibility** &rarr; **Text-to-speech output** (or search for *"Text-to-speech"* in Settings).
3. Under **Preferred engine**, select **SpeechFlow** (or *Eloquence (openevv)*).
4. Tap the **Settings (gear icon)** beside SpeechFlow to configure your voice, speed, dictionaries, and update preferences.

---

## Third-Party Compatibility

SpeechFlow adheres to the standard Android `TextToSpeechService` API and is fully compatible with:
* **Google TalkBack**
* **Jieshuo+ (Commentary Screen Reader)**
* **Auto TTS**
* **EBook and document readers** (Moon+ Reader, Voice Dream, @Voice Aloud Reader)

> **Tip for Auto TTS:** If you experience any voice loading issues after updating, clear Auto TTS's cache or re-select SpeechFlow in its engine selection menu.

---

## Wear OS Support

SpeechFlow runs smoothly on Wear OS smartwatches (tested on Pixel Watch and Wear OS devices with `armeabi-v7a` and `arm64-v8a` architectures).

To install on a watch:
1. Enable **Developer Options** & **Wireless Debugging** on the watch.
2. Connect your computer to the watch using `adb connect <watch-ip>:<port>`.
3. Install the APK:
   ```bash
   adb install SpeechFlow-armeabi-v7a-release.apk
   ```

---

## Credits & Acknowledgements

* **Original Project**: Built upon [trypsynth/evvdroid](https://github.com/trypsynth/evvdroid). Immense gratitude to the original developer for creating the foundational Android port.
* **Engine**: [openevv](https://github.com/Mudb0y/openevv) by Mudb0y, the reverse-engineered IBM ViaVoice engine.
* **Hindi Acoustics**: Hindi phoneme integration influenced by the open-source **eSpeak** project.
* **Special Thanks**: Sachin Bariya, Rosendo Hubilla, and Syed Yusuf Ali for continuous testing and feedback.

---

## Community & Support

* **GitHub Repository**: [Sharib20006/speechflow---android-text-to-speech-app](https://github.com/Sharib20006/speechflow---android-text-to-speech-app)
* **Telegram Channel**: [@cybertech_3](https://t.me/cybertech_3)
* **Original Upstream Project**: [trypsynth/evvdroid](https://github.com/trypsynth/evvdroid)

---

## License

* **SpeechFlow Application & Kotlin Code**: Licensed under the **MIT License**.
* **openevv Engine**: Licensed under its respective open-source license.
* **Language Data**: Embedded ViaVoice language data belongs to IBM / Cerence.
