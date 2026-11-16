# Nova

Nova is a standalone offline talking assistant for Linux and Windows. She is not part of CodeAque. After Setup she stays on this PC — no accounts, no API keys. Models are downloaded at Setup, not bundled here.

## Install on Linux

1. Download `nova-0.1.0.tar.gz` or `nova-0.1.0.zip` from the latest [release](https://github.com/hoodlumasylum/nova/releases).
2. Extract it.
3. Run `install-desktop.sh`.
4. Open **Nova Setup** and wait for the downloads to finish (several GB; there is a progress display).
5. Open **Nova**. Click the corner bubble to expand, or press Escape to shrink back.

## Install on Windows

1. Download `nova-0.1.0-windows.zip` from the latest [release](https://github.com/hoodlumasylum/nova/releases).
2. Extract it.
3. Double-click **Nova-Setup.exe** (or `setup.bat`).
4. If asked, install Python 3.10 or newer from [python.org](https://www.python.org/downloads/windows/) and tick **Add python.exe to PATH**, then run Setup again.
5. When Setup finishes, open **Nova** (`Nova.exe`, `run.bat`, or the Desktop / Start Menu shortcut).

Windows Setup downloads official Ollama for Windows, Piper, and whisper.cpp builds into `%LOCALAPPDATA%\Nova`. Same rule as Linux: nothing heavy is bundled in this zip.

## Honest notes

- **Not yet tested on real hardware** — not on a Linux handheld, and not on a real Windows 11 PC (including a Nitro V16). Please report what breaks.
- **My voice** is coming soon / Beta. You can record only your own voice (consent required). There is no way to import someone else’s clips. A reliable on-device clone is not ready, so she keeps using the CodeAque voice until it is.
- **“Hey Nova”** is experimental and off by default.
- Exclusive-fullscreen games may cover the always-on-top bubble. Borderless windowed works better.
- On Wayland, a system-wide hotkey may not work; use the bubble, the tray, or an in-window shortcut. On Windows the default hotkey is Ctrl+Alt+N (untested on real hardware).
- Video prompt mode only writes text you can copy into a tool such as Seedance. Nova does not call Seedance or any online API, and does not generate video.
- Memory is a local notes file you can view, edit, or forget in Settings. That is not training or retraining a model.
- Replies on a laptop or handheld will be slower than on a desktop — that is normal.

Default model: `llama3.2:3b`. Low-power: `qwen2.5:1.5b`. Nova’s Ollama listens on `127.0.0.1:11435` so it does not collide with CodeAque.

A separate Nova Android app is planned later, not as part of CodeAque. See [docs/android-plan.md](docs/android-plan.md).
