# Software Nova needs

Nothing heavy is bundled in the zip. `setup.bat` (Windows) or `setup.sh` (Linux) installs the
app pieces first; **Nova Setup** then downloads voices and models into a local folder
(`%LOCALAPPDATA%\Nova` on Windows, `~/.local/share/nova` on Linux). Sizes are approximate.

## Needed to open the app

| Software | What it's for | Typical size | How it is installed |
|---|---|---|---|
| Python 3.12 (3.10+ will work) | Runs Nova | Installer ~27 MB, about 100 MB on disk | `setup.bat` uses winget, or the official silent per-user installer from python.org |
| PySide6 Essentials | The window, her face, the tray icon | About 80–120 MB | `pip` inside a local `.venv` (the full PySide6 pack is much larger and is not required) |

## Needed for her to talk and listen (Nova Setup)

These are official builds, downloaded once. After that she stays on this PC — no accounts, no API keys.

| Software | What it's for | Typical size | Optional? |
|---|---|---|---|
| Ollama (official Windows / Linux build) | Runs the local chat model | About 1.5 GB (includes GPU libraries) | Needed for chat |
| `llama3.2:3b` (default model) | Her brain for replies | About 2.0 GB | Needed unless you pick Low power |
| `qwen2.5:1.5b` (Low power) | Smaller model for weaker PCs | About 1.0 GB | Alternative to the 3B model, not both required |
| Piper | Speech out (text to voice) | About 10–20 MB | Needed to hear her |
| CodeAque voice (`en_US-amy-medium`) | The voice she uses today | About 18 MB | Needed to hear her |
| whisper.cpp | Speech in (your mic) | About 10–30 MB for the program | Needed so she can hear you |
| `ggml-base.en` | English speech-recognition model | About 141 MB | Needed so she can hear you |

**Disk for a first-time Setup:** plan on roughly **6–8 GB** free (Ollama ~1.5 GB, a 3B model ~2 GB, voices and speech a few hundred MB, plus Python and PySide6).

## Optional voices and extras

| Software | What it's for | Typical size |
|---|---|---|
| Extra Piper voices (`lessac`, `hfc_female`, `alba`, `jenny`) | Other built-in voices you can pick in Settings | About 18 MB each |
| My voice | Record only your own voice (consent required). A reliable on-device clone is not ready yet, so she keeps the CodeAque voice | — |
| `python-xlib` | Linux X11 global hotkeys | A few hundred KB |
| `zstandard` | Unpacking Ollama's `.tar.zst` on Linux | A few MB |
| ffmpeg | Microphone recording on Windows (whisper needs a wav) | Varies; only if you want to talk to her by voice on Windows and ffmpeg is not already there |

“Hey Nova” wake-word is experimental and off by default. Video prompt mode only writes text; Nova does not call Seedance or any online API.
