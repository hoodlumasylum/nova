# Nova

Nova is a standalone offline talking assistant for Linux and Windows. She is not part of CodeAque. After Setup she stays on this PC — no accounts, no API keys. Models are downloaded at Setup, not bundled here.

## Install on Linux

1. Download `nova-0.1.0.tar.gz` or `nova-0.1.0.zip` from the latest [release](https://github.com/hoodlumasylum/nova/releases).
2. Extract it.
3. Run `install-desktop.sh`.
4. Open **Nova Setup** and wait for the downloads to finish (several GB; there is a progress display).
5. Open **Nova**. Click the corner bubble to expand, or press Escape to shrink back.

## Install on Windows

1. Download the Nova zip and extract it (for example into Downloads).
2. Double-click **setup.bat** (or **Nova-Setup.exe** if you have it).
3. `setup.bat` is meant to be one click, with no terminal knowledge:
   - Installs Python 3.12 for this user if it is missing (winget, or the official silent installer).
   - Creates a local environment and installs Nova's Python packages.
   - Puts **Nova** and **Nova Setup** shortcuts on the Desktop and in the Start Menu (Nova opens with `pythonw`, so no black console window).
   - Then opens Nova Setup so she can download voices and models.
   - Safe to run again: it will not redo work that is already done. A log is written to `%LOCALAPPDATA%\Nova\setup.log`.
4. When Setup finishes, open **Nova** from the Desktop shortcut (or `run.bat`).

Windows Setup downloads official Ollama for Windows, Piper, and whisper.cpp builds into `%LOCALAPPDATA%\Nova`. Same rule as Linux: nothing heavy is bundled in this zip.

A full list of what she needs, including optional voices and sizes, is in [docs/software.md](docs/software.md).

## Her face

Nova's portrait is an original character, not a picture of a real person, and the app does not clone anyone's face. Blink, gaze and lighting are layered 2D animation on that still portrait (the real iris shifts inside a soft mask, lids are sampled from her own skin, catchlights and a warm light ease with movement). It can look alive on the desktop, but it is not a filmed or 3D performance.

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
