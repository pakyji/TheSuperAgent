# 🤖 The Super Agent

![Build](https://github.com/OWNER_PLACEHOLDER/super-agent/actions/workflows/release.yml/badge.svg)
![Version](https://img.shields.io/badge/version-5.0-00E5FF)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF)
![License](https://img.shields.io/badge/License-MIT-yellow)

**The Super Agent** — ultimate Android AI super agent for **pakyji**, built for **Redmi 15C (HyperOS)**.
Pure Super Agent identity. No JARVIS. No backend. No root. Phone-only development (Termux).

---

## 📥 Download Latest APK

👉 **[Releases → TheSuperAgent-Latest.apk](../../releases/latest)**

Every push to `main` auto-builds and replaces the release (tag `v<run>-<sha>`).

---

## ✨ 50+ Features

### 🧠 Standalone Intelligence
- 🔑 Direct OpenRouter API calls from device — key in **EncryptedSharedPreferences (AES256-GCM)**, with plain-prefs fallback for Redmi 15C keystore bug
- 🎛️ Model picker (default `openai/gpt-4o-mini`)
- 📴 Offline fallback: device control, automation, app building work without internet
- 💾 Persistent memory (Room DB) + configurable personality
- 🌐 Auto language detection: Urdu (اردو) / Italian / English with matching STT/TTS

### 🎙️ Voice Super Agent
- 🛡️ ForegroundService `type=microphone`, channel `super_agent_voice_v4`
- 🔔 Persistent notification with Pause / Resume / Stop
- 🗣️ Wake word **"Hey Super Agent"**, STT `ur-PK` / `it-IT` / `en-US`, TTS TextToSpeech
- 🔋 Battery-aware, screen-off support where permitted, push-to-talk fallback
- 🔴 Mic privacy indicator

### 🎮 Device Control Hub (40+ tools)
- 🚀 Launch apps · 🔗 Deep links · 🌍 Open URLs · 🔎 Web search · ▶️ YouTube search
- 🗺️ Maps navigation · 🔔 Notification reader/summarizer/speak-aloud
- 🎵 Media control (MediaSession) · 🔊 Volume · 🔆 Brightness · 🔦 Flashlight
- 🌙 DND · 📋 Clipboard history · 📁 Sandbox file manager · 👤 Contacts search/call/SMS (with confirmation)
- 📅 Calendar read/create · ⏰ Alarms (AlarmManager) · 🏷️ QR scanner · 📷 Camera capture
- 🔋 Battery/storage monitor · 📊 App usage list

### 💬 WhatsApp Auto-Reply (official API only)
- NotificationListenerService + RemoteInput direct reply
- `com.whatsapp` / `com.whatsapp.w4b` respected, template `{sender}` `{time}`, onlyWhen Always/ScreenOff/Driving, allowed contacts, keyword filter, full logging

### 🎧 Spotify Super Control
- MediaSession controller for `com.spotify.music`
- play/pause/next/prev/seek/playPlaylist, TrackInfo flow, album-art downsample (OOM-safe)

### ⚙️ Automation Engine V2
- Natural-language creation ("At 8 PM remind me…")
- Triggers: time · notification · app-open · battery · charging · headphones · location
- States: RUNNING / WAITING_PERMISSION / NEEDS_CONFIRMATION / COMPLETED / FAILED
- Templates: Morning Routine, Night Mode, Focus Mode, Drive Mode, Study Timer, Battery Saver
- Visual flow editor (Compose Canvas), retry/timeout/cancel/recovery

### 🏗️ App Builder Pro
- Kotlin Compose + React + Node generation from natural language
- Workspace `/files/super_agent_workspace/`, explorer, editor, build log, auto-fix suggestions
- ZIP export + GitHub push after auth

### 🚀 12 Super Features (v4–v5)
SmartBattery · AppLock · FileVault (AES file encryption) · AINotes · ScreenTimeDashboard · CallScreener · LiveTranslate · GestureControl · DriveMode · SleepMode · SmartGallery · PrivacyShield (kill-switch)

### 🖥️ Premium UI — 10 tabs
Chat · Voice · Automations · App Builder · Device · Files · Tools · Memory · Logs · Settings
Dark futuristic `#050A14` + cyan `#00E5FF`, animated orbs, voice visualizer. Every button functional.

---

## 🛠️ Termux Build Commands (phone-only)

```bash
pkg install git openjdk-17 zip -y
git clone https://github.com/OWNER_PLACEHOLDER/super-agent && cd super-agent
curl -sL -o g.zip https://services.gradle.org/distributions/gradle-8.7-bin.zip && unzip -q g.zip
export JAVA_HOME=$PREFIX/opt/openjdk17
../gradle-8.7/bin/gradle assembleDebug --stacktrace --no-daemon
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## 🔁 Auto-Release

`.github/workflows/release.yml` runs on push to `main` + manual dispatch:
JDK 17 → Gradle 8.7 → `assembleDebug` → rename → delete old releases/tags → new release `v<run>-<sha>` with `TheSuperAgent-Latest.apk`.

## 📱 Redmi 15C (HyperOS) Setup
1. Settings → Apps → Manage apps → **The Super Agent** → **Autostart ON**
2. Battery → **No restrictions** (app + Termux)
3. Settings → Privacy → **Notification access** → The Super Agent → ON
4. Settings → Accessibility → **The Super Agent** → ON
5. **Display over other apps** → ON
6. **Usage access** → ON
7. Recents → lock the app 🔒

##  Security & Privacy
- API keys only in EncryptedSharedPreferences (AES256-GCM), never in code or logs
- WhatsApp replies only via official NotificationListener + RemoteInput, with user confirmation & contact allow-list
- PrivacyShield kill-switch stops VoiceService + clears clipboard instantly
- No root, no hidden APIs, no lockscreen bypass

## 🐞 Bug Fixes (v5.0 pack)
- NotificationListener crash on Android 14 (null extras guard)
- HyperOS foreground service (FOREGROUND_SERVICE_IMMEDIATE + fallback)
- EncryptedSharedPreferences Redmi 15C (custom MasterKey alias + fallback prefs)
- MediaSession permission graceful fail
- WhatsApp group title detection
- Spotify album-art OOM (nullable + downsample)
- Gradle/Termux chmod fail
- Accessibility config flags

## 📸 Screenshots
| Chat | Voice | Automations | Builder |
|------|-------|-------------|---------|
| soon | soon | soon | soon |

## 🗺️ Roadmap
- On-device whisper STT · cross-device sync · plugin marketplace · wearable companion

## 🤝 Contributing
Fork → branch → PR. Keep it root-free and official-API-only.

## 📄 License
MIT © pakyji

---
**Author:** pakyji · **Identity:** The Super Agent (never JARVIS)
