"""Push-to-talk / expand hotkeys. On Wayland, global shortcuts may not work — the UI is honest about that."""
from __future__ import annotations

import os
import shutil
import subprocess
import threading


def wayland_session() -> bool:
    return bool(os.environ.get("WAYLAND_DISPLAY")) and not os.environ.get("NOVA_FORCE_X11")


def parse_combo(spec: str) -> tuple[list[str], str]:
    parts = [p.strip() for p in (spec or "").split("+") if p.strip()]
    if not parts:
        return ["ctrl", "alt"], "n"
    key = parts[-1].lower()
    mods = [p.lower() for p in parts[:-1]]
    return mods, key


def notify(title: str, body: str) -> None:
    if shutil.which("notify-send"):
        subprocess.Popen(["notify-send", "-a", "Nova", title, body], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


class GlobalHotkey:
    """Best-effort X11 grab. Disabled on Wayland (use the bubble, tray, or in-window shortcuts)."""

    def __init__(self, spec: str, callback):
        self.spec = spec
        self.callback = callback
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self.error = ""

    def start(self) -> bool:
        if os.name == "nt":
            return self._start_windows()
        if wayland_session():
            self.error = (
                "This is a Wayland session, so a system-wide hotkey may not work. "
                "Use the bubble, the tray icon, or a shortcut inside Nova's window. "
                "A controller combo still works when Nova can see the pad."
            )
            return False
        try:
            from Xlib import X, display
            from Xlib.XK import string_to_keysym
        except Exception:
            self.error = "Global hotkeys need python-xlib on X11. The bubble, tray, and in-window shortcuts still work."
            return False
        self._thread = threading.Thread(target=self._run, args=(display, X, string_to_keysym), daemon=True)
        self._thread.start()
        return True

    def stop(self) -> None:
        self._stop.set()

    def _start_windows(self) -> bool:
        """Register a process-wide hotkey with the Win32 API (RegisterHotKey)."""
        try:
            import ctypes
            from ctypes import wintypes
        except Exception as e:
            self.error = f"Could not load Windows hotkey support: {e}"
            return False
        mods, key = parse_combo(self.spec)
        mask = 0
        if "ctrl" in mods or "control" in mods:
            mask |= 0x0002  # MOD_CONTROL
        if "alt" in mods or "mod1" in mods:
            mask |= 0x0001  # MOD_ALT
        if "shift" in mods:
            mask |= 0x0004
        if "super" in mods or "meta" in mods or "win" in mods:
            mask |= 0x0008
        named = {"space": 0x20, "tab": 0x09, "esc": 0x1B, "escape": 0x1B, "enter": 0x0D, "return": 0x0D}
        if key in named:
            vk = named[key]
        elif len(key) == 1:
            vk = ord(key.upper())
        else:
            self.error = f"Could not map hotkey {self.spec}."
            return False
        user32 = ctypes.windll.user32
        hotkey_id = 0x4E01
        WM_HOTKEY = 0x0312
        PeekMessageW = user32.PeekMessageW
        PeekMessageW.argtypes = [ctypes.POINTER(wintypes.MSG), wintypes.HWND, wintypes.UINT, wintypes.UINT, wintypes.UINT]
        PeekMessageW.restype = wintypes.BOOL

        def _run() -> None:
            if not user32.RegisterHotKey(None, hotkey_id, mask, vk):
                self.error = f"Could not register {self.spec} (it may already be in use). The bubble, tray, and in-window shortcuts still work."
                return
            try:
                msg = wintypes.MSG()
                while not self._stop.is_set():
                    if PeekMessageW(ctypes.byref(msg), None, 0, 0, 1) and msg.message == WM_HOTKEY:
                        self.callback()
                    else:
                        self._stop.wait(0.05)
            finally:
                user32.UnregisterHotKey(None, hotkey_id)

        self._thread = threading.Thread(target=_run, daemon=True)
        self._thread.start()
        return True

    def _run(self, display_mod, X, string_to_keysym) -> None:
        try:
            d = display_mod.Display()
            root = d.screen().root
            mods, key = parse_combo(self.spec)
            mask = 0
            if "ctrl" in mods or "control" in mods:
                mask |= X.ControlMask
            if "alt" in mods or "mod1" in mods:
                mask |= X.Mod1Mask
            if "shift" in mods:
                mask |= X.ShiftMask
            if "super" in mods or "meta" in mods or "win" in mods:
                mask |= X.Mod4Mask
            ks = string_to_keysym(key)
            kc = d.keysym_to_keycode(ks)
            if not kc:
                self.error = f"Could not map hotkey {self.spec}."
                return
            # Ignore lock/num/scroll so the grab still fires.
            for extra in (0, X.LockMask, X.Mod2Mask, X.LockMask | X.Mod2Mask):
                root.grab_key(kc, mask | extra, True, X.GrabModeAsync, X.GrabModeAsync)
            d.flush()
            while not self._stop.is_set():
                if d.pending_events() == 0:
                    self._stop.wait(0.15)
                    continue
                ev = d.next_event()
                if ev.type == X.KeyPress:
                    self.callback()
        except Exception as e:
            self.error = f"Could not grab the hotkey: {e}"
