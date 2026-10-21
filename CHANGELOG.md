# Changelog — The Super Agent

## v5.0 (2026-10-18)
- Initial public source drop: full Super Agent identity (no JARVIS lineage).
- Standalone OpenRouter intelligence with EncryptedSharedPreferences (AES256-GCM) + keystore fallback.
- Voice Super Agent: foreground mic service `super_agent_voice_v4`, wake word "Hey Super Agent", ur-PK/it-IT/en-US.
- Device Control Hub with 40+ tools.
- WhatsApp auto-reply via NotificationListener + RemoteInput (official API only).
- Spotify Super Control via MediaSession.
- Automation Engine V2 with NL creation, 6 triggers, 6 templates, visual flow editor.
- App Builder Pro (Kotlin Compose + React + Node generation, ZIP export, GitHub push).
- 12 super features v4–v5.
- Premium 10-tab Compose UI (#050A14 / #00E5FF).
- GitHub auto-release workflow with old-release cleanup.
- Bugfix pack: Android 14 notification null-extras, foreground-service start fallback, keystore fallback, MediaSession grace, Spotify art OOM, Termux gradle chmod, accessibility flags.
