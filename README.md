# 💖 SANA AI V3 — Full Phone Control Android Voice Assistant

**SANA AI** is a production-grade, mobile-first Android AI voice assistant powered by **Google Gemini**. Designed as a true Android companion and phone controller, SANA provides natural spoken interaction, real phone control capabilities, background operation, voice emotion intelligence, and privacy-respecting memory.

---

## 🌟 Core Capabilities

### 1. 🧠 Core Gemini Brain
- **Natural Spoken Language**: Understands natural voice commands, conversational context, and intent.
- **Modular Tool Execution**: Decides dynamically which native Android tool to invoke and executes it in real time.
- **Truthful & Safe**: Never fakes successful execution. If an action requires user intervention (e.g., tapping Send in WhatsApp or granting permission), SANA reports honestly.
- **Multimodal Vision**: Understands photos, screenshots, and visual objects using Gemini Multimodal reasoning.
- **Image Generation**: Creates custom artwork on demand via `gemini-2.5-flash-image`.

### 2. 📱 Android Phone Control Tools
- **`openApp(app)`**: Launches any installed application (WhatsApp, YouTube, Camera, Maps, Spotify, etc.) or opens the Play Store if not installed.
- **`findContact(contact)`**: Searches device contacts via Android Contacts Provider.
- **`makeCall(contact)`**: Places direct phone calls (`CALL_PHONE`) or opens the system dialer (`ACTION_DIAL`).
- **`answerCall()` & `endCall()`**: Supports telecom operations via Android `TelecomManager`.
- **`openWhatsApp()` & `openConversation(contact)`**: Launches WhatsApp and opens chats via deep links.
- **`composeMessage(contact, message)`**: Prepares messages in WhatsApp and honestly guides: *"Boss, I've opened the conversation and prepared the message. Android requires you to tap Send."*
- **`readSupportedNotifications()`**: Reads and summarizes incoming notifications via `SanaNotificationListenerService`.
- **`openCamera()` & `openGallery()`**: Launches camera and media gallery.
- **`openMaps(destination)` & `startNavigation(destination)`**: Starts turn-by-turn navigation.
- **`mediaPlay()`, `mediaPause()`, `mediaNext()`, `mediaPrevious()`**: Controls audio and media playback.
- **`getBatteryStatus()`**: Reads live battery level, charging state, and power saver.
- **`getDeviceInformation()`**: Reports device model, manufacturer, Android OS version, and available storage.
- **`setSupportedDeviceSetting(setting, value)`**: Controls device flashlight/torch and media volume.
- **`launchUrl(url)`**: Launches links and websites in the browser.

### 3. 🛡️ Permission Center & Confirmation System
- **Transparent Permission Center**: Dedicated screen showing status (Granted / Not Granted) for Microphone, Contacts, Phone Calls, Notification Access, Camera, and Location with direct grant/enable triggers.
- **Safety Levels**:
  - **SAFE**: Auto-executes safe read-only queries (battery, apps, maps).
  - **CONFIRM**: Asks *"Boss, should I call Ahmed?"* before placing calls.
  - **STRICT**: Requests confirmation before any external action.

### 4. 🎙️ Real-Time Voice & Voice Modes
- **Expressive Gemini Native Voice**: Warm, feminine, cute, natural, and expressive tone (never robotic or monotone).
- **Voice Modes**:
  - **CUTE**: Soft, sweet, cheerful, and charming (Pitch 1.25x, Speed 1.05x).
  - **WARM**: Gentle, comforting, and reassuring.
  - **CALM**: Serene, tranquil, and relaxed.
  - **PLAYFUL**: Bouncy, witty, and lively.
  - **ROMANTIC**: Soft, sweet, and intimate (naturally uses "Boss", "Love", "Babe" respectfully).
  - **PROFESSIONAL**: Crisp, articulate, and efficient.
- **Voice Testing**: Dedicated *"TEST SANA VOICE"* button for instant real-time sound check.
- **Resilient Fallback**: Automatic failover to local Android Text-to-Speech (TTS) if network audio is unavailable.

### 5. 💖 Personality & Emotion Engine
- Addresses the user as **"Boss"** (or affectionately in Romantic mode).
- Real-time sentiment detection: `Happy`, `Sad`, `Excited`, `Stressed`, `Angry`, `Confused`, `Serious`, `Casual`.
- Modulates UI avatars, visualizer colors, and vocal delivery based on emotion.

### 6. 🧠 Opt-in Memory System
- Local Room Database persistence.
- Stores user preferences (preferred nickname, voice mode, language, custom notes).
- Full user control: **Memory ON/OFF**, **View Memory**, and **Clear Memory**.

### 7. 🌐 Multilingual
- Full native support for **English**, **Bahasa Melayu (Malay)**, **Urdu (اردو)**, and **Hindi (हिन्दी)**.

### 8. 🔄 Background Service & Wake Word
- `SanaForegroundService`: Maintains an ongoing Android notification with microphone listening status.
- `SanaWakeWordManager`: Continuous speech recognition with push-to-talk fallback.

---

## 🏗️ Architecture

```
com.example/
├── MainActivity.kt
├── audio/
│   ├── CentralSanaAudioManager.kt     # Gemini Audio playback & Android TTS engine
│   └── SanaWakeWordManager.kt         # Speech recognizer & wake word listener
├── brain/
│   └── SanaBrain.kt                   # Gemini reasoning, tools, vision & emotions
├── data/
│   ├── api/GeminiApiClient.kt         # OkHttp client for Gemini REST API
│   ├── database/                      # Room entities, DAOs & SanaMemoryManager
│   └── model/SanaModels.kt            # VoiceMode, GeminiVoice, EmotionTone, etc.
├── permission/
│   └── SanaPermissionManager.kt       # Device permissions coordinator
├── service/
│   ├── SanaForegroundService.kt       # Foreground assistant service
│   └── SanaNotificationListenerService.kt # Notification listener assistant
├── session/
│   └── SanaSessionManager.kt          # Central ViewModel coordinator
├── tools/
│   ├── SanaToolRouter.kt              # Intent router with confirmation system
│   ├── SanaPhoneTools.kt              # Battery, apps, camera, maps, flashlight
│   ├── SanaCallTools.kt               # Contacts lookup, dialer, call answering
│   ├── SanaWhatsAppTools.kt           # WhatsApp deep-link & message composer
│   ├── SanaNotificationTools.kt       # Notification reader & summarizer
│   └── SanaMediaTools.kt              # Media playback key dispatcher
└── ui/
    ├── components/                    # Dialogs, visualizer, sheets & confirmation
    ├── screens/SanaMainScreen.kt      # Material 3 UI layout
    └── theme/                         # Centralized M3 theming & typography
```

---

## 🚀 Setup & Build Instructions

### 1. Requirements
- Android Studio Ladybug / Meerkat or newer
- JDK 17 or JDK 21
- Android SDK 36 (minSdk 24)

### 2. Configure Gemini API Key
In Google AI Studio or your local environment:
1. Open `.env` (or copy `.env.example` to `.env`).
2. Add your Gemini API key:
   ```properties
   GEMINI_API_KEY=your_actual_gemini_api_key_here
   ```

### 3. Build APK
Run Gradle from the project root:
```bash
./gradlew assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📥 Direct APK Download
- **Pre-compiled APK**: Stored in `release/SANA_AI.apk`
- **GitHub Actions**: Automated release builds are triggered on every push.
