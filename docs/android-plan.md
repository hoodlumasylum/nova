# Nova for Android — plan (after the PC app)

This is a plan only. The Android app is **not** built in this tree and must **not** be folded into CodeAque.

## Product

A separate Nova APK, installable alongside CodeAque on devices such as the K56 / Win600. Same character, same local-first rules.

- Floating **corner bubble** overlay (SYSTEM_ALERT_WINDOW / a media-style overlay) showing the animated portrait (same face art, lip-sync and expressions).
- Tap or a gamepad combo expands to a **full-screen** activity: Chat, Video prompt, Settings, Setup.
- Shrink back to the bubble.

## On-device stack

- **LLM:** llama.cpp Android (or Google AIEdge / ExecuTorch if it is clearly easier to ship). Default a small Qwen2.5 **0.5B–1.5B** GGUF, Q4, downloaded at first-run Setup, never bundled in the APK if it would bloat the store listing. Honour low-power (0.5B, thread cap, unload when idle).
- **Speech in:** whisper.cpp **tiny** (or tiny.en) via a JNI build, or the platform speech recognizer as a fallback if the user allows it — but prefer the on-device tiny model so it still works offline.
- **Speech out:** Piper Android build if a reliable JNI/binary is available; otherwise a high-quality **Android TTS** voice labelled honestly (not “CodeAque voice” unless we ship the Piper amy model). Voice picker + Preview. **My voice** stays Beta / coming soon, own-voice consent, no import of other people’s clips.
- **Wake word:** “Hey Nova” off by default; same honesty as desktop.

## Data and modes

- Data under app-private storage (and optionally a user-visible folder only if we say so).
- Chat history, **local memory** (view / edit / forget all), personality presets + editable blurb.
- **Video prompt** mode: write a paste-ready prompt, Copy to clipboard. **No Seedance or any online API.**
- Offline after Setup. No accounts or keys.

## Overlay reality checks

- Android will require the “display over other apps” permission, explained in-app before the system sheet.
- Some games and Android versions ignore or restrict overlays; be honest in the UI.
- Battery: unload the model when idle; don’t record until the bubble, a button, or an enabled wake word asks for it.

## Shipping

- Own package name, own signing, own GitHub release — **not** a CodeAque APK flavour.
- Build only after the PC app is in a shape Code is happy to live with.
