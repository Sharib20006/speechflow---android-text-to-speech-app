# speechFlow

**speechFlow** is an Android text-to-speech (TTS) engine that provides seamless access to the popular **ETI Eloquence / IBM TTS voice**, based on the **Evdroid** project.

It includes several enhancements focused on improving pronunciation, reducing speech latency, simplifying settings, and providing a more comfortable, customizable experience for everyday users and screen reader users.

**Modder:** Syed Sharib Ali

---

## Features

### 1. Hindi Language Support

- It can read the Hindi language clearly and efficiently with authentic Hindi sounds in the authentic, classic Eloquence voice.
- Speaks everyday Hindi text, Indian numbering formats, continuous decimals, percentages, and English loanwords written in Devanagari naturally.
- Hindi pronunciation improvements are influenced by the open-source **eSpeak** project.
- Designed to provide clearer, pleasant, and more natural Hindi speech for everyday communication.

### 2. Updated ECI Engine, Speed and Reduced Latency

- Features an updated **ECI (Embedded Command Interface)** engine with enhanced buffer handling and smoother speech dispatch.
- Optimised speech processing to reduce delays caused by library limitations.
- Improved startup performance so speech starts immediately with minimal initial delay.

### 3. Immediate Response for Screen Readers (Zero Click & Swipe Delay)

- Delivers an immediate, snappy speech response whenever clicking, tapping, or swiping across items with screen readers such as TalkBack and Commentary (Jieshuo).
- Eliminates the previous speech start delays and sluggishness, ensuring words are spoken the instant an element is touched or focused.
- Provides a significantly more responsive and seamless navigation experience throughout the entire Android interface.

### 4. Automatic & Manual Update System

- **Check for Updates on Startup**: Automatically checks for new releases on GitHub when opening the app, presenting version details and changelog.
- **Manual "Check for Updates" Button**: An always-visible button in settings to check for new updates on demand.
- **Architecture-Aware Downloads**: Automatically detects whether your device is 64-bit or 32-bit:
  - 64-bit devices (`arm64-v8a`): Downloads the optimized 64-bit APK.
  - 32-bit devices (`armeabi-v7a`): Downloads the 32-bit APK.
  - Universal fallback: If a device-specific build is unavailable, it automatically downloads the Universal APK.
- **In-App Download & Installation**: Real-time download progress with options to download in the background, install directly, or save the APK to your files.

### 5. Force Language & Speech Controls

- **Force Language**: Lock speech output to your preferred language (US English, UK English, Spanish, French, German, Italian, Polish, Japanese, Hindi) so other apps cannot unexpectedly change the language.
- **Force Speech Rate & Force Pitch**: Lock your preferred talking speed and pitch so third-party applications and screen readers cannot alter your settings.
- **Sample Rate Selection**: Choose between standard 11,025 Hz and classic 8,000 Hz audio output.
- **Pause Shortening**: JAWS-style pause trimming to remove unnecessary pauses at the end of sentences and shorten punctuation delays for a snappier screen-reading experience.

### 6. Dictionaries, Community Dictionary & Case-Sensitivity

- **User Dictionary Management**: Easily add, edit, search, and delete custom word pronunciations right from the settings.
- **Dictionary Case-Sensitivity (Sensitive Word Support)**: Choose whether a custom dictionary entry requires an exact case match (for example, reading capitalized `IT` differently from `it`) or applies case-insensitively.
- **Community Dictionary Support**: Includes a built-in community dictionary that can be enabled or disabled with a single switch.
- **Language-Specific Dictionaries**: Create and manage separate pronunciation dictionaries for each individual language.

### 7. Filter Typing Echo

- Added support for filtering typing echo.
- Useful for users who rely on direct-touch typing.
- Enable this option to avoid duplicate announcements of typed keys and prevent voice stuttering when typing quickly.

### 8. Intelligent Number & Roman Numeral Processing

- **Number Reading Modes**: Customize how numbers are announced:
  - Normal
  - Digits (reads numbers digit by digit)
  - Pairs (groups numbers in pairs)
  - Triplets (groups numbers in threes)
  - Words (reads numbers as full spoken words)
- **Smooth Time Reading**: Natural 12-hour and 24-hour time formatting.
- **Roman Numeral Reading**: Automatically recognizes and speaks Roman numerals in titles, chapters, and dates (e.g. *"Chapter IV"*, *"King Henry VIII"*).

### 9. Emoji 17.0 Reading Support

- Comprehensive emoji reading with spoken descriptive feedback for emojis, including full support for modern **Emoji 17.0** emojis.

### 10. Punctuation, Skip Symbols & Hashtag Reading

- **Punctuation Verbosity**: Adjust punctuation reading levels between *None*, *Some*, *Most*, and *All*.
- **Skip Symbols**: Configure specific symbols or characters to be completely silenced and ignored during speech.
- **Context-Sensitive Hashtags**: Pronounces hashtags naturally (e.g. `#speechFlow` is read as *"hashtag speechFlow"*).
- **Context-Sensitive Pronunciation Handling**: Smarter context rules for common ambiguous words and heteronyms.

### 11. Camel Case Normalisation

- Provides an option for camel case normalisation.
- Useful for users who frequently encounter camel case text (e.g., `ThisIsCamelCaseText` is read as separate, natural words).

### 12. Repeating Character Support

- Supports counting repeated characters instead of announcing the same character repeatedly (e.g. announces *"5 dots"* instead of repeating *"dot dot dot dot dot"*).
- Helps make repeated symbols and characters easier to understand.

### 13. Original Speech Options Retained

- Retains all classic voices (**Reed, Shelley, Bobby, Rocko, Glen, Sandy, Grandma, Grandpa**) and parameters (pitch, speed, inflection, head size, roughness, breathiness, volume, gender).
- Existing users can continue using the familiar speech controls without changes.

### 14. Organised Settings

The settings layout has been organised into separate sections for easier access and better clarity:

- Speech Settings
- Text Processing Settings
- Dictionary Settings

### 15. Compatibility with Other TTS Apps

speechFlow is designed to work smoothly with applications such as:

- Google TalkBack
- Jieshuo+ (Commentary Screen Reader)
- Auto TTS
- Other Android applications that support external TTS engines

If speechFlow does not work correctly with another application, try clearing that application's data and configuring the TTS engine again.

### 16. Additional Improvements

- Various other fixes and optimisations for smoother performance.
- General improvements to speech processing, accessibility, and usability.

---

## Credits and Acknowledgements

A huge thank you and credit goes to the respected developer of the **[Evdroid](https://github.com/trypsynth/evvdroid)** project for bringing the popular ETI Eloquence / IBM TTS voice to the Android community.

Credits also go to the **[openevv](https://github.com/Mudb0y/openevv)** project for the reverse-engineered engine.

Special thanks also go to the open-source **eSpeak** project for helping make Hindi phoneme and pronunciation support possible, as well as to Sachin Bariya, Rosendo Hubilla, and Syed Yusuf Ali for their support.

---

## About This Project

speechFlow is a modified and enhanced version based on the Evdroid project. Its goal is to provide a smoother, more accessible, and more customisable speech experience while retaining the original voice and speech options.

The project focuses on:

- Authentic, clear Hindi speech
- Immediate, delay-free response when swiping or clicking with screen readers
- Updated ECI engine integration
- Reduced speech delay
- Comprehensive update and dictionary system
- Improved text processing and typing echo filtering
- Flexible dictionary support
- Easier settings navigation
- Better accessibility and everyday usability

---

## Modder

**Syed Sharib Ali**

Thank you for using speechFlow and supporting the project.

**Regards,**  
**Sharib**
