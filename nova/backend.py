"""QObject glue between the QML UI and Nova's local engine, speech, memory, and video prompts."""
from __future__ import annotations

import shutil
import time
from pathlib import Path

from PySide6.QtCore import Property, QObject, QPoint, QStandardPaths, Qt, QThread, QTimer, QUrl, Signal, Slot
from PySide6.QtGui import QClipboard, QCursor, QGuiApplication
from PySide6.QtWidgets import QFileDialog

from . import __version__, config, engine, express, history, install, lipsync, memory, paths, speech, video_prompt, wake
from .config import PERSONALITY_PRESETS
from .speech import CONSENT_TEXT, VOICES


class _ChatWorker(QThread):
    finished_ok = Signal(str)
    failed = Signal(str)

    def __init__(self, cfg: dict, messages: list[dict], extra: str):
        super().__init__()
        self.cfg = cfg
        self.messages = messages
        self.extra = extra

    def run(self) -> None:
        try:
            self.finished_ok.emit(engine.chat(self.cfg, self.messages, system_extra=self.extra))
        except Exception as e:
            self.failed.emit(str(e) or "The local model could not answer.")


class _VideoWorker(QThread):
    finished_ok = Signal(str)
    failed = Signal(str)

    def __init__(self, cfg: dict, idea: str, image_path: str, extra: str):
        super().__init__()
        self.cfg = cfg
        self.idea = idea
        self.image_path = image_path
        self.extra = extra

    def run(self) -> None:
        try:
            user = video_prompt.user_message(self.idea, self.image_path or None)
            raw = engine.complete(self.cfg, video_prompt.SYSTEM, user, system_extra=self.extra)
            name = Path(self.image_path).name if self.image_path else ""
            self.finished_ok.emit(video_prompt.parse_model_output(raw, self.idea, name))
        except Exception as e:
            name = Path(self.image_path).name if self.image_path else ""
            # Still give a paste-ready template if the model is missing.
            fallback = video_prompt.template(self.idea, name)
            self.failed.emit(str(e) or "Could not reach the local model.")
            self.finished_ok.emit(fallback)


class _TalkWorker(QThread):
    finished_ok = Signal(str)
    failed = Signal(str)

    def __init__(self, seconds: int):
        super().__init__()
        self.seconds = seconds

    def run(self) -> None:
        try:
            wav = paths.tmp_dir() / "listen.wav"
            speech.record_wav(wav, seconds=self.seconds)
            self.finished_ok.emit(speech.transcribe(wav))
        except Exception as e:
            self.failed.emit(str(e) or "Could not hear you.")


class _SpeakWorker(QThread):
    ready = Signal(str)  # wav path
    failed = Signal(str)

    def __init__(self, text: str, cfg: dict):
        super().__init__()
        self.text = text
        self.cfg = cfg

    def run(self) -> None:
        try:
            wav = paths.tmp_dir() / "speak.wav"
            if wav.exists():
                wav.unlink()
            speech.synthesize(self.text, self.cfg, wav)
            self.ready.emit(str(wav))
        except Exception as e:
            self.failed.emit(str(e) or "Could not speak.")


class Backend(QObject):
    expressionChanged = Signal()
    mouthOpenChanged = Signal()
    expandedChanged = Signal()
    pageChanged = Signal()
    statusChanged = Signal()
    busyChanged = Signal()
    listeningChanged = Signal()
    thinkingChanged = Signal()
    talkingChanged = Signal()
    messagesChanged = Signal()
    setupNeededChanged = Signal()
    configChanged = Signal()
    memoryChanged = Signal()
    videoPromptChanged = Signal()
    draftChanged = Signal()
    speedNoteChanged = Signal()
    hotkeyWarningChanged = Signal()
    bubbleMoved = Signal(int, int)  # hint for the window
    cursorChanged = Signal()

    def __init__(self):
        super().__init__()
        self._cfg = config.load()
        self._expression = "idle"
        self._mouth_open = 0.0
        self._expanded = False
        self._page = "chat"  # chat | video | settings | setup
        self._status = ""
        self._busy = False
        self._listening = False
        self._thinking = False
        self._talking = False
        self._messages = history.load()
        self._setup_needed = install.setup_needed(self._cfg)
        self._draft = ""
        self._video_idea = ""
        self._video_image = ""
        self._video_out = ""
        self._speed_note = (
            "Nova runs a small model on this PC. Replies on a laptop or handheld will be slower "
            "than on a desktop — that's normal. After setup she stays offline: no accounts, no keys."
        )
        self._hotkey_warning = ""
        self._envelope: list[tuple[float, float]] = []
        self._speak_started = 0.0
        self._player = None
        self._worker = None
        self._speak_worker = None
        self._last_activity = time.time()
        self._setup_fraction = 0.0
        self._setup_log = ""

        self._lip_timer = QTimer(self)
        self._lip_timer.setInterval(30)
        self._lip_timer.timeout.connect(self._tick_lips)

        self._idle_timer = QTimer(self)
        self._idle_timer.setInterval(30_000)
        self._idle_timer.timeout.connect(self._maybe_unload)
        self._idle_timer.start()

        self._cursor = QPoint(0, 0)
        self._cursor_timer = QTimer(self)
        self._cursor_timer.setInterval(16)
        self._cursor_timer.timeout.connect(self._poll_cursor)
        self._cursor_timer.start()

        if not self._setup_needed:
            # Warm the server in the background later; don't block UI start.
            QTimer.singleShot(800, self._warm)
        self._wake = None
        if self._cfg.get("wake_word"):
            QTimer.singleShot(1200, self._sync_wake)

    # --- properties ---
    def _get_expression(self) -> str:
        return self._expression

    def _set_expression(self, value: str) -> None:
        value = value if value in express.EXPRESSIONS else "idle"
        if value != self._expression:
            self._expression = value
            self.expressionChanged.emit()

    expression = Property(str, _get_expression, _set_expression, notify=expressionChanged)

    def _get_mouth(self) -> float:
        return self._mouth_open

    def _set_mouth(self, value: float) -> None:
        value = max(0.0, min(1.0, float(value)))
        if abs(value - self._mouth_open) > 0.01:
            self._mouth_open = value
            self.mouthOpenChanged.emit()

    mouthOpen = Property(float, _get_mouth, _set_mouth, notify=mouthOpenChanged)

    def _get_expanded(self) -> bool:
        return self._expanded

    expanded = Property(bool, _get_expanded, notify=expandedChanged)

    def _get_page(self) -> str:
        return self._page

    page = Property(str, _get_page, notify=pageChanged)

    def _get_status(self) -> str:
        return self._status

    status = Property(str, _get_status, notify=statusChanged)

    def _get_busy(self) -> bool:
        return self._busy

    busy = Property(bool, _get_busy, notify=busyChanged)

    def _get_listening(self) -> bool:
        return self._listening

    listening = Property(bool, _get_listening, notify=listeningChanged)

    def _get_thinking(self) -> bool:
        return self._thinking

    thinking = Property(bool, _get_thinking, notify=thinkingChanged)

    def _get_talking(self) -> bool:
        return self._talking

    talking = Property(bool, _get_talking, notify=talkingChanged)

    def _get_messages(self):
        return self._messages

    messages = Property("QVariantList", _get_messages, notify=messagesChanged)

    def _get_setup_needed(self) -> bool:
        return bool(self._setup_needed)

    setupNeeded = Property(bool, _get_setup_needed, notify=setupNeededChanged)

    def _get_setup_items(self):
        return list(self._setup_needed)

    setupItems = Property("QVariantList", _get_setup_items, notify=setupNeededChanged)

    def _get_draft(self) -> str:
        return self._draft

    def _set_draft(self, value: str) -> None:
        if value != self._draft:
            self._draft = value
            self.draftChanged.emit()

    draft = Property(str, _get_draft, _set_draft, notify=draftChanged)

    speedNote = Property(str, lambda self: self._speed_note, notify=speedNoteChanged)
    hotkeyWarning = Property(str, lambda self: self._hotkey_warning, notify=hotkeyWarningChanged)
    version = Property(str, lambda self: __version__, constant=True)
    consentText = Property(str, lambda self: CONSENT_TEXT, constant=True)
    videoDisclaimer = Property(str, lambda self: video_prompt.DISCLAIMER, constant=True)
    memoryDisclaimer = Property(
        str,
        lambda self: (
            "These are notes saved in a file on this PC. Nova rereads them when she answers. "
            "This is not training or retraining, and forgetting a note does not 'unlearn' a model."
        ),
        constant=True,
    )
    faceImage = Property(str, lambda self: Path(paths.face_image()).as_uri(), constant=True)

    def _poll_cursor(self) -> None:
        try:
            p = QCursor.pos()
        except Exception:
            return
        if p.x() != self._cursor.x() or p.y() != self._cursor.y():
            self._cursor = p
            self.cursorChanged.emit()

    cursorX = Property(int, lambda self: int(self._cursor.x()), notify=cursorChanged)
    cursorY = Property(int, lambda self: int(self._cursor.y()), notify=cursorChanged)

    def _get_video_idea(self) -> str:
        return self._video_idea

    def _set_video_idea(self, value: str) -> None:
        self._video_idea = value or ""
        self.videoPromptChanged.emit()

    videoIdea = Property(str, _get_video_idea, _set_video_idea, notify=videoPromptChanged)

    def _get_video_image(self) -> str:
        return self._video_image

    videoImage = Property(str, _get_video_image, notify=videoPromptChanged)

    def _get_video_image_url(self) -> str:
        if self._video_image:
            return Path(self._video_image).as_uri()
        return ""

    videoImageUrl = Property(str, _get_video_image_url, notify=videoPromptChanged)

    def _get_video_out(self) -> str:
        return self._video_out

    videoOut = Property(str, _get_video_out, notify=videoPromptChanged)

    def _cfg_prop(name, typ=str):
        def getter(self):
            return self._cfg.get(name)
        def setter(self, value):
            if self._cfg.get(name) != value:
                self._cfg[name] = typ(value) if typ is not bool else bool(value)
                config.save(self._cfg)
                self.configChanged.emit()
        return Property(typ, getter, setter, notify=lambda: None)

    # Manual properties for settings
    def _get_corner(self) -> str:
        return self._cfg.get("corner") or "bottom-right"

    def _set_corner(self, value: str) -> None:
        if value in ("top-left", "top-right", "bottom-left", "bottom-right") and value != self._cfg.get("corner"):
            self._cfg["corner"] = value
            self._cfg["bubble_x"] = None
            self._cfg["bubble_y"] = None
            config.save(self._cfg)
            self.configChanged.emit()

    corner = Property(str, _get_corner, _set_corner, notify=configChanged)

    def _get_click_through(self) -> bool:
        return bool(self._cfg.get("click_through"))

    def _set_click_through(self, value: bool) -> None:
        self._cfg["click_through"] = bool(value)
        config.save(self._cfg)
        self.configChanged.emit()

    clickThrough = Property(bool, _get_click_through, _set_click_through, notify=configChanged)

    def _get_voice(self) -> str:
        return self._cfg.get("voice") or "codeaque"

    def _set_voice(self, value: str) -> None:
        self._cfg["voice"] = value
        config.save(self._cfg)
        self.configChanged.emit()

    voice = Property(str, _get_voice, _set_voice, notify=configChanged)

    def _get_wake(self) -> bool:
        return bool(self._cfg.get("wake_word"))

    def _set_wake(self, value: bool) -> None:
        self._cfg["wake_word"] = bool(value)
        config.save(self._cfg)
        self.configChanged.emit()
        self._sync_wake()

    wakeWord = Property(bool, _get_wake, _set_wake, notify=configChanged)

    def _get_low(self) -> bool:
        return bool(self._cfg.get("low_power"))

    def _set_low(self, value: bool) -> None:
        self._cfg["low_power"] = bool(value)
        config.save(self._cfg)
        self.configChanged.emit()

    lowPower = Property(bool, _get_low, _set_low, notify=configChanged)

    def _get_ptt(self) -> str:
        return self._cfg.get("ptt") or "Ctrl+Alt+N"

    def _set_ptt(self, value: str) -> None:
        self._cfg["ptt"] = value
        config.save(self._cfg)
        self.configChanged.emit()

    ptt = Property(str, _get_ptt, _set_ptt, notify=configChanged)

    def _get_pad(self) -> str:
        return self._cfg.get("pad_combo") or "Select+Y"

    def _set_pad(self, value: str) -> None:
        self._cfg["pad_combo"] = value
        config.save(self._cfg)
        self.configChanged.emit()

    padCombo = Property(str, _get_pad, _set_pad, notify=configChanged)

    def _get_personality(self) -> str:
        return self._cfg.get("personality") or "nova"

    def _set_personality(self, value: str) -> None:
        self._cfg["personality"] = value
        config.save(self._cfg)
        self.configChanged.emit()

    personality = Property(str, _get_personality, _set_personality, notify=configChanged)

    def _get_ptext(self) -> str:
        return self._cfg.get("personality_text") or ""

    def _set_ptext(self, value: str) -> None:
        self._cfg["personality_text"] = (value or "")[:500]
        config.save(self._cfg)
        self.configChanged.emit()

    personalityText = Property(str, _get_ptext, _set_ptext, notify=configChanged)

    def _get_consent(self) -> bool:
        return bool(self._cfg.get("my_voice_consent"))

    def _set_consent(self, value: bool) -> None:
        self._cfg["my_voice_consent"] = bool(value)
        config.save(self._cfg)
        self.configChanged.emit()

    myVoiceConsent = Property(bool, _get_consent, _set_consent, notify=configChanged)

    def _get_voices(self):
        rows = []
        for key, meta in VOICES.items():
            rows.append({
                "key": key,
                "title": meta["title"],
                "subtitle": meta["subtitle"],
                "ready": bool(speech.voice_files(key)) if key != "myvoice" else speech.my_voice_ready(),
                "beta": key == "myvoice",
            })
        return rows

    voices = Property("QVariantList", _get_voices, notify=configChanged)

    def _get_presets(self):
        rows = []
        for key, meta in PERSONALITY_PRESETS.items():
            rows.append({"key": key, "title": meta["title"], "blurb": meta["blurb"]})
        return rows

    personalityPresets = Property("QVariantList", _get_presets, constant=True)

    def _get_facts(self):
        data = memory.load()
        return [{"id": f.get("id"), "text": f.get("text")} for f in data["facts"]]

    memoryFacts = Property("QVariantList", _get_facts, notify=memoryChanged)

    def _get_prompts(self):
        data = memory.load()
        return [
            {"id": p.get("id"), "title": p.get("title"), "prompt": p.get("prompt"), "idea": p.get("idea")}
            for p in data["saved_prompts"]
        ]

    savedPrompts = Property("QVariantList", _get_prompts, notify=memoryChanged)

    preflightNotes = Property("QVariantList", lambda self: install.preflight_notes(), constant=True)

    def _get_setup_frac(self) -> float:
        return self._setup_fraction

    setupFraction = Property(float, _get_setup_frac, notify=statusChanged)

    def _get_setup_log(self) -> str:
        return self._setup_log

    setupLog = Property(str, _get_setup_log, notify=statusChanged)

    bubbleX = Property(int, lambda self: int(self._cfg.get("bubble_x") or -1), notify=configChanged)
    bubbleY = Property(int, lambda self: int(self._cfg.get("bubble_y") or -1), notify=configChanged)

    myVoiceReady = Property(bool, lambda self: speech.my_voice_ready(), notify=configChanged)
    wakeNote = Property(
        str,
        lambda self: (
            "“Hey Nova” is off by default. There is no official pretrained openWakeWord phrase for it, "
            "so this uses a simple local energy gate plus whisper and can misfire. Works best on X11 "
            "with a quiet room. On Wayland, use the bubble or the push-to-talk hotkey instead."
        ),
        constant=True,
    )
    overlayNote = Property(
        str,
        lambda self: (
            "The corner bubble stays on top of the desktop and most windowed or borderless games. "
            "Exclusive-fullscreen games may still cover it — use borderless windowed if you want her visible."
        ),
        constant=True,
    )

    # --- slots ---
    def _set_status(self, text: str) -> None:
        self._status = text
        self.statusChanged.emit()

    def _set_busy(self, value: bool) -> None:
        if self._busy != value:
            self._busy = value
            self.busyChanged.emit()

    def _touch(self) -> None:
        self._last_activity = time.time()

    def _warm(self) -> None:
        try:
            engine.start_server(self._cfg)
        except Exception:
            pass

    def _maybe_unload(self) -> None:
        if self._busy or self._talking or self._thinking or self._listening:
            return
        if time.time() - self._last_activity < engine.IDLE_UNLOAD_SEC:
            return
        try:
            engine.unload(self._cfg)
        except Exception:
            pass

    def _refresh_setup(self) -> None:
        needed = install.setup_needed(self._cfg)
        if needed != self._setup_needed:
            self._setup_needed = needed
            self.setupNeededChanged.emit()

    def _sync_wake(self) -> None:
        want = bool(self._cfg.get("wake_word"))
        if not want:
            if self._wake is not None:
                self._wake.stop()
                self._wake = None
            return
        if self._wake is not None:
            return
        listener = wake.WakeListener(lambda: QTimer.singleShot(0, self._on_wake))
        if listener.start():
            self._wake = listener
            self._set_status("“Hey Nova” is on — experimental, local mic only.")
        else:
            self._set_status(listener.error)

    def _on_wake(self) -> None:
        if not self._expanded:
            self.setExpanded(True)
        self.setPage("chat")
        self.startTalk()

    def set_hotkey_warning(self, text: str) -> None:

        self._hotkey_warning = text or ""
        self.hotkeyWarningChanged.emit()

    @Slot()
    def toggleExpanded(self) -> None:
        self._expanded = not self._expanded
        if self._expanded and self._page not in ("chat", "video", "settings", "setup"):
            self._page = "chat"
            self.pageChanged.emit()
        self.expandedChanged.emit()

    @Slot(bool)
    def setExpanded(self, value: bool) -> None:
        if self._expanded != bool(value):
            self._expanded = bool(value)
            self.expandedChanged.emit()

    @Slot(str)
    def setPage(self, name: str) -> None:
        if name in ("chat", "video", "settings", "setup") and name != self._page:
            self._page = name
            self.pageChanged.emit()
        if not self._expanded:
            self._expanded = True
            self.expandedChanged.emit()

    @Slot()
    def shrinkToBubble(self) -> None:
        self.setExpanded(False)

    @Slot(str)
    def submitChat(self, text: str) -> None:
        text = (text or self._draft or "").strip()
        if not text or self._busy:
            return
        self._draft = ""
        self._touch()
        self._messages.append({"role": "user", "content": text})
        history.save(self._messages)
        self.messagesChanged.emit()
        self._thinking = True
        self.thinkingChanged.emit()
        self._set_expression("thinking")
        self._set_status("Thinking… this can take a little while on a laptop.")
        self._set_busy(True)
        extra = memory.context_block("chat")
        worker = _ChatWorker(dict(self._cfg), list(self._messages), extra)
        worker.finished_ok.connect(self._on_chat_ok)
        worker.failed.connect(self._on_fail)
        worker.finished.connect(worker.deleteLater)
        self._worker = worker
        worker.start()

    def _on_chat_ok(self, raw: str) -> None:
        spoken, _stored = memory.apply_remembered(raw)
        expr, spoken = express.split_tag(spoken)
        spoken = spoken.strip()
        if _stored:
            self.memoryChanged.emit()
        self._messages.append({"role": "assistant", "content": spoken})
        history.save(self._messages)
        self.messagesChanged.emit()
        self._thinking = False
        self.thinkingChanged.emit()
        self._set_expression(expr if expr != "idle" else "happy")
        self._set_busy(False)
        self._set_status("")
        if spoken:
            self._speak(spoken)

    def _on_fail(self, err: str) -> None:
        self._thinking = False
        self.thinkingChanged.emit()
        self._listening = False
        self.listeningChanged.emit()
        self._set_busy(False)
        self._set_expression("sad")
        self._set_status(err)

    @Slot()
    def startTalk(self) -> None:
        if self._busy:
            return
        self._touch()
        self._listening = True
        self.listeningChanged.emit()
        self._set_expression("listening")
        self._set_status("Listening…")
        self._set_busy(True)
        worker = _TalkWorker(seconds=6)
        worker.finished_ok.connect(self._on_heard)
        worker.failed.connect(self._on_fail)
        worker.finished.connect(worker.deleteLater)
        self._worker = worker
        worker.start()

    def _on_heard(self, text: str) -> None:
        self._listening = False
        self.listeningChanged.emit()
        self._set_busy(False)
        if not (text or "").strip():
            self._set_status("I didn't catch that.")
            self._set_expression("idle")
            return
        self.submitChat(text.strip())

    def _speak(self, text: str) -> None:
        # Don't try to speak if Piper isn't installed yet — the chat still shows.
        if not speech.piper_bin().is_file():
            return
        self._set_status("Speaking…")
        worker = _SpeakWorker(text, dict(self._cfg))
        worker.ready.connect(self._on_wav_ready)
        worker.failed.connect(lambda err: self._set_status(err))
        worker.finished.connect(worker.deleteLater)
        self._speak_worker = worker
        worker.start()

    def _on_wav_ready(self, wav_path: str) -> None:
        wav = Path(wav_path)
        self._envelope = lipsync.mouth_envelope(wav)
        try:
            self._player = speech.play_wav(wav)
        except Exception as e:
            self._set_status(str(e))
            return
        self._talking = True
        self.talkingChanged.emit()
        self._speak_started = time.monotonic()
        self._lip_timer.start()

    def _tick_lips(self) -> None:
        t = time.monotonic() - self._speak_started
        open_amt = lipsync.mouth_at(self._envelope, t)
        self._set_mouth(open_amt)
        ended = False
        if self._player is not None and self._player.poll() is not None:
            ended = True
        if self._envelope and t > (self._envelope[-1][0] + 0.25):
            ended = True
        if ended:
            self._lip_timer.stop()
            self._set_mouth(0.0)
            self._talking = False
            self.talkingChanged.emit()
            self._player = None
            if self._expression in ("listening", "thinking"):
                self._set_expression("idle")
            self._set_status("")

    @Slot()
    def stopTalking(self) -> None:
        if self._player is not None and self._player.poll() is None:
            self._player.terminate()
        self._lip_timer.stop()
        self._set_mouth(0.0)
        self._talking = False
        self.talkingChanged.emit()

    @Slot()
    def clearChat(self) -> None:
        history.clear()
        self._messages = []
        self.messagesChanged.emit()

    @Slot()
    def writeVideoPrompt(self) -> None:
        if self._busy:
            return
        idea = (self._video_idea or "").strip()
        if not idea and not self._video_image:
            self._set_status("Describe the idea first — or attach a still and say what should move.")
            return
        self._touch()
        self._thinking = True
        self.thinkingChanged.emit()
        self._set_expression("thinking")
        self._set_status("Writing a prompt you can paste. Nova will not call Seedance or any online API.")
        self._set_busy(True)
        extra = memory.context_block("video")
        worker = _VideoWorker(dict(self._cfg), idea, self._video_image, extra)
        worker.finished_ok.connect(self._on_video_ok)
        worker.failed.connect(lambda err: self._set_status(err + " Used a local template if needed."))
        worker.finished.connect(worker.deleteLater)
        self._worker = worker
        worker.start()

    def _on_video_ok(self, text: str) -> None:
        self._video_out = text
        self.videoPromptChanged.emit()
        self._thinking = False
        self.thinkingChanged.emit()
        self._set_busy(False)
        self._set_expression("happy")
        self._set_status("Prompt ready — copy it, then paste it into your video tool yourself.")

    @Slot()
    def pickVideoImage(self) -> None:
        path, _ = QFileDialog.getOpenFileName(
            None,
            "Attach a still (stays on this PC — Nova does not upload it)",
            QStandardPaths.writableLocation(QStandardPaths.PicturesLocation) or str(Path.home()),
            "Images (*.png *.jpg *.jpeg *.webp *.bmp)",
        )
        if path:
            self._video_image = path
            self.videoPromptChanged.emit()

    @Slot()
    def clearVideoImage(self) -> None:
        self._video_image = ""
        self.videoPromptChanged.emit()

    @Slot()
    def copyVideoPrompt(self) -> None:
        if not self._video_out:
            return
        cb = QGuiApplication.clipboard()
        cb.setText(self._video_out, QClipboard.Clipboard)
        self._set_status("Copied. Paste it into Seedance or your other video tool — Nova does not send it.")

    @Slot()
    def saveVideoPrompt(self) -> None:
        if not self._video_out:
            return
        memory.add_saved_prompt(
            self._video_out,
            idea=self._video_idea,
            title=(self._video_idea or "Video prompt")[:80],
            image_name=Path(self._video_image).name if self._video_image else "",
        )
        self.memoryChanged.emit()
        self._set_status("Saved to local memory on this PC.")

    @Slot(str)
    def loadSavedPrompt(self, prompt_id: str) -> None:
        for item in memory.load()["saved_prompts"]:
            if item.get("id") == prompt_id:
                self._video_out = item.get("prompt") or ""
                self._video_idea = item.get("idea") or ""
                self.videoPromptChanged.emit()
                self.setPage("video")
                return

    @Slot(str)
    def addMemoryFact(self, text: str) -> None:
        if memory.add_fact(text):
            self.memoryChanged.emit()

    @Slot(str, str)
    def editMemoryFact(self, fact_id: str, text: str) -> None:
        if memory.update_fact(fact_id, text):
            self.memoryChanged.emit()

    @Slot(str)
    def deleteMemoryFact(self, fact_id: str) -> None:
        if memory.delete_fact(fact_id):
            self.memoryChanged.emit()

    @Slot(str)
    def deleteSavedPrompt(self, prompt_id: str) -> None:
        if memory.delete_saved_prompt(prompt_id):
            self.memoryChanged.emit()

    @Slot()
    def forgetAllMemory(self) -> None:
        memory.forget_all()
        self.memoryChanged.emit()
        self._set_status("Local notes were deleted. This did not retrain or 'unlearn' a model.")

    @Slot(int, int)
    def saveBubblePos(self, x: int, y: int) -> None:
        self._cfg["bubble_x"] = int(x)
        self._cfg["bubble_y"] = int(y)
        config.save(self._cfg)

    @Slot(str)
    def snapCorner(self, corner: str) -> None:
        self._set_corner(corner)

    @Slot(str)
    def previewVoice(self, key: str) -> None:
        cfg = dict(self._cfg)
        cfg["voice"] = key if key != "myvoice" or speech.my_voice_ready() else "codeaque"
        sample = "Hey, it's Nova. This is how this voice sounds."
        self._speak_worker_cfg = cfg
        worker = _SpeakWorker(sample, cfg)
        worker.ready.connect(self._on_wav_ready)
        worker.failed.connect(lambda err: self._set_status(err))
        worker.finished.connect(worker.deleteLater)
        self._speak_worker = worker
        worker.start()

    @Slot()
    def recordMyVoice(self) -> None:
        if not self._cfg.get("my_voice_consent"):
            self._set_status("Tick the consent box first — you must only record your own voice.")
            return
        self._set_status(
            "My voice is Beta and not a finished clone yet. Recording a short sample to this PC — "
            "Nova will not import files or clone anyone else."
        )
        self._set_busy(True)
        self._listening = True
        self.listeningChanged.emit()

        class Rec(QThread):
            done = Signal(str)
            failed = Signal(str)

            def run(self_inner) -> None:
                try:
                    dest = paths.my_voice_dir() / "sample.wav"
                    speech.record_wav(dest, seconds=8)
                    self_inner.done.emit(str(dest))
                except Exception as e:
                    self_inner.failed.emit(str(e))

        worker = Rec()
        worker.done.connect(self._on_my_voice_recorded)
        worker.failed.connect(self._on_fail)
        worker.finished.connect(worker.deleteLater)
        self._worker = worker
        worker.start()

    def _on_my_voice_recorded(self, path: str) -> None:
        self._listening = False
        self.listeningChanged.emit()
        self._set_busy(False)
        self.configChanged.emit()
        self._set_status(
            "Sample saved at " + path + ". A reliable local clone is not ready yet, so My voice stays Beta / coming soon. "
            "Nova will keep using the CodeAque voice until a real on-device clone ships. "
            "No one else's clips can be imported."
        )

    @Slot()
    def runSetup(self) -> None:
        if self._busy:
            return
        self.setPage("setup")
        self._set_busy(True)
        self._setup_log = ""
        self._setup_fraction = 0.0
        self.statusChanged.emit()

        cfg = dict(self._cfg)

        class Setup(QThread):
            progress = Signal(str, float)
            done = Signal()
            failed = Signal(str)

            def run(self_inner) -> None:
                def emit(text, frac):
                    self_inner.progress.emit(text, -1.0 if frac is None else float(frac))

                try:
                    install.run_full_setup(cfg, install.Progress(emit), voices=["codeaque"])
                    self_inner.done.emit()
                except Exception as e:
                    self_inner.failed.emit(str(e) or "Setup failed.")

        worker = Setup()
        worker.progress.connect(self._on_setup_progress)
        worker.done.connect(self._on_setup_done)
        worker.failed.connect(self._on_fail)
        worker.finished.connect(worker.deleteLater)
        self._worker = worker
        worker.start()

    def _on_setup_progress(self, text: str, frac: float) -> None:
        self._setup_log = (self._setup_log + "\n" + text).strip()
        if frac >= 0:
            self._setup_fraction = frac
        self._set_status(text)

    def _on_setup_done(self) -> None:
        self._set_busy(False)
        self._refresh_setup()
        self.configChanged.emit()
        self._set_status("Nova is ready. You can start chatting or writing prompts.")
        self._set_expression("happy")
        try:
            root = Path(__file__).resolve().parents[1]
            install.write_desktop_entries(root)
        except Exception:
            pass

    @Slot()
    def openSetupFolder(self) -> None:
        paths.data_dir().mkdir(parents=True, exist_ok=True)
        if shutil.which("xdg-open"):
            import subprocess
            subprocess.Popen(["xdg-open", str(paths.data_dir())])
