import json
import os
import struct
import tempfile
import unittest
import wave
from pathlib import Path

# Isolate all tests to a temp data/config dir.
_TMP = Path(tempfile.mkdtemp(prefix="nova-test-"))
os.environ["NOVA_DATA_DIR"] = str(_TMP / "data")
os.environ["NOVA_CONFIG_DIR"] = str(_TMP / "config")

from nova import config, express, face_layout, history, lipsync, memory, paths, video_prompt
from nova.config import personality_prompt
from nova.speech import CONSENT_TEXT, VOICES


class PathsTests(unittest.TestCase):
    def test_overrides(self):
        self.assertTrue(str(paths.data_dir()).startswith(str(_TMP)))
        self.assertEqual(paths.memory_file(), paths.data_dir() / "memory.json")
        self.assertTrue(paths.face_image().is_file())


class PersonalityTests(unittest.TestCase):
    def test_default_includes_honest_ai_and_memory(self):
        text = personality_prompt(config.DEFAULTS)
        self.assertIn("Nova", text)
        self.assertIn("AI", text)
        self.assertIn("not training or retraining", text)
        self.assertIn("[[happy]]", text)
        self.assertIn("Seedance", text)

    def test_custom_description_is_included(self):
        cfg = dict(config.DEFAULTS)
        cfg["personality"] = "hype"
        cfg["personality_text"] = "Call me Code and keep it short."
        text = personality_prompt(cfg)
        self.assertIn("Hype buddy", text)
        self.assertIn("Call me Code and keep it short.", text)


class ExpressTests(unittest.TestCase):
    def test_tag(self):
        expr, spoken = express.split_tag("[[laughing]]\nHaha, nice one.")
        self.assertEqual(expr, "laughing")
        self.assertEqual(spoken, "Haha, nice one.")

    def test_sentiment_fallback(self):
        expr, spoken = express.split_tag("I'm sorry, that's tough.")
        self.assertEqual(expr, "sad")
        self.assertEqual(spoken, "I'm sorry, that's tough.")

    def test_idle(self):
        expr, spoken = express.split_tag("The shortcut is in Settings.")
        self.assertEqual(expr, "idle")
        self.assertIn("Settings", spoken)


class LipSyncTests(unittest.TestCase):
    def test_envelope_from_wav(self):
        wav = _TMP / "beep.wav"
        rate = 16000
        # 0.3s quiet, 0.4s loud, 0.2s quiet
        frames = []
        for i in range(int(rate * 0.9)):
            t = i / rate
            amp = 20000 if 0.3 <= t <= 0.7 else 200
            frames.append(int(amp * __import__("math").sin(2 * 3.1415 * 220 * t)))
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(rate)
            w.writeframes(struct.pack("<" + "h" * len(frames), *frames))
        env = lipsync.mouth_envelope(wav, frame_ms=20)
        self.assertTrue(env)
        mid = lipsync.mouth_at(env, 0.5)
        quiet = lipsync.mouth_at(env, 0.05)
        self.assertGreater(mid, 0.4)
        self.assertLess(quiet, 0.2)
        self.assertEqual(lipsync.mouth_at([], 0.1), 0.0)


class MemoryTests(unittest.TestCase):
    def setUp(self):
        memory.forget_all()

    def test_add_edit_delete_and_context(self):
        item = memory.add_fact("The user's name is Code.")
        self.assertIsNotNone(item)
        memory.add_fact("They play on a Win600.")
        block = memory.context_block("chat")
        self.assertIn("Code", block)
        self.assertIn("Win600", block)
        self.assertIn("not training or retraining", block)
        self.assertTrue(memory.update_fact(item["id"], "The user's name is Code (Hazel)."))
        self.assertIn("Hazel", memory.context_block("chat"))
        self.assertTrue(memory.delete_fact(item["id"]))
        self.assertNotIn("Hazel", memory.context_block("chat"))

    def test_duplicate_fact_not_added_twice(self):
        memory.add_fact("Likes racing games")
        memory.add_fact("likes racing games")
        self.assertEqual(len(memory.load()["facts"]), 1)

    def test_remember_tags_stripped_and_stored(self):
        spoken, stored = memory.apply_remembered(
            "[[happy]] Nice to meet you.\n[[remember: The user's name is Code]]"
        )
        self.assertNotIn("[[remember", spoken)
        self.assertTrue(stored)
        self.assertIn("Code", memory.context_block("chat"))

    def test_saved_prompts_and_forget_all(self):
        memory.add_saved_prompt("Subject:\nA golden robot\n\nPaste this:\nA golden robot turns.", idea="gold robot")
        block = memory.context_block("video")
        self.assertIn("gold robot", block.lower() + block)
        self.assertIn("golden robot", block)
        memory.forget_all()
        data = memory.load()
        self.assertEqual(data["facts"], [])
        self.assertEqual(data["saved_prompts"], [])
        self.assertIn("no saved notes", memory.context_block("chat").lower())

    def test_no_retraining_claim_in_disclaimers(self):
        self.assertNotIn("retrain the model", "notes file")
        self.assertIn("not training or retraining", memory.context_block("chat"))


class VideoPromptTests(unittest.TestCase):
    def test_template_has_all_sections_and_paste(self):
        text = video_prompt.template("She turns to the camera, gold lines pulse.", "still.jpg")
        for name in video_prompt.SECTIONS:
            self.assertIn(name + ":", text)
        self.assertIn("Paste this:", text)
        self.assertIn("still.jpg", text)

    def test_disclaimer_is_offline_only(self):
        self.assertIn("does not call Seedance", video_prompt.DISCLAIMER)
        self.assertIn("does not generate video", video_prompt.DISCLAIMER)
        self.assertIn("do not call any online api", video_prompt.SYSTEM.lower())

    def test_user_message_mentions_filename_not_pixels(self):
        msg = video_prompt.user_message("slow orbit", "/tmp/foo/hero.png")
        self.assertIn("hero.png", msg)
        self.assertIn("Do not claim you viewed the pixels", msg)

    def test_parse_keeps_structured_output(self):
        raw = "\n".join(
            [
                "Subject: A dancer",
                "Action / motion: spins",
                "Camera movement: orbit",
                "Lighting: soft",
                "Style: film",
                "Duration: 5 seconds",
                "Negative notes: jitter",
                "",
                "Paste this: A dancer spins.",
            ]
        )
        out = video_prompt.parse_model_output(raw, "dancer", "")
        self.assertIn("A dancer", out)
        self.assertIn("Paste this:", out)


class HistoryTests(unittest.TestCase):
    def test_roundtrip(self):
        history.clear()
        history.save([{"role": "user", "content": "hi"}, {"role": "assistant", "content": "hey"}])
        msgs = history.load()
        self.assertEqual(len(msgs), 2)
        self.assertEqual(msgs[0]["content"], "hi")
        history.clear()
        self.assertEqual(history.load(), [])


class VoicePolicyTests(unittest.TestCase):
    def test_consent_and_no_file_import_voice(self):
        self.assertIn("YOUR OWN voice", CONSENT_TEXT)
        self.assertIn("never lets you import", CONSENT_TEXT.lower())
        self.assertIn("codeaque", VOICES)
        self.assertIn("myvoice", VOICES)
        self.assertIn("Beta", VOICES["myvoice"]["title"] + VOICES["myvoice"]["subtitle"])

    def test_face_layout_boxes_on_unit_square(self):
        for box in (face_layout.LEFT_EYE, face_layout.RIGHT_EYE, face_layout.MOUTH, face_layout.BUBBLE_FOCUS):
            self.assertGreaterEqual(box["x"], 0)
            self.assertGreaterEqual(box["y"], 0)
            self.assertLessEqual(box["x"] + box["w"], 1.05)
            self.assertLessEqual(box["y"] + box["h"], 1.05)


class ConfigRoundtripTests(unittest.TestCase):
    def test_save_load(self):
        data = config.load()
        data["personality"] = "coach"
        data["click_through"] = True
        config.save(data)
        again = config.load()
        self.assertEqual(again["personality"], "coach")
        self.assertTrue(again["click_through"])


class InstallPlatformTests(unittest.TestCase):
    def test_windows_official_urls_are_zips(self):
        from nova import install
        ollama = install.OLLAMA_URL[("windows", "x86_64")]
        piper = install.PIPER_URL[("windows", "x86_64")]
        whisper = install.WHISPER_URL[("windows", "x86_64")]
        self.assertTrue(ollama.endswith(".zip"))
        self.assertIn("ollama-windows-amd64", ollama)
        self.assertTrue(piper.endswith("piper_windows_amd64.zip"))
        self.assertTrue(whisper.endswith("whisper-bin-x64.zip"))
        self.assertIn("github.com/ollama/ollama", ollama)
        self.assertIn("github.com/rhasspy/piper", piper)
        self.assertIn("github.com/ggml-org/whisper.cpp", whisper)

    def test_archive_name(self):
        from nova import install
        self.assertEqual(install._archive_name("https://example/a.zip", "ollama"), "ollama.zip")
        self.assertEqual(install._archive_name("https://example/a.tar.zst", "ollama"), "ollama.tar.zst")

    def test_is_windows_helper_exists(self):
        self.assertFalse(paths.is_windows())


if __name__ == "__main__":
    unittest.main()


class FaceAssetTests(unittest.TestCase):
    def test_layout_has_lid_and_gaze_boxes(self):
        for box in (face_layout.LEFT_EYE, face_layout.RIGHT_EYE, face_layout.LEFT_LID, face_layout.RIGHT_LID,
                    face_layout.MOUTH, face_layout.BUBBLE_FOCUS):
            self.assertGreater(box["w"], 0)
            self.assertGreater(box["h"], 0)
            self.assertLess(box["x"] + box["w"], 1.01)
            self.assertLess(box["y"] + box["h"], 1.01)
        # Gaze boxes sit inside (or on) the lid coverage, so the iris cannot slide onto the cheek.
        for gaze, lid in ((face_layout.LEFT_EYE, face_layout.LEFT_LID), (face_layout.RIGHT_EYE, face_layout.RIGHT_LID)):
            self.assertGreaterEqual(gaze["x"], lid["x"] - 0.002)
            self.assertGreaterEqual(gaze["y"], lid["y"] - 0.002)
            self.assertLessEqual(gaze["x"] + gaze["w"], lid["x"] + lid["w"] + 0.002)
            self.assertLessEqual(gaze["y"] + gaze["h"], lid["y"] + lid["h"] + 0.002)

    def test_eye_overlay_assets_exist(self):
        assets = Path(__file__).resolve().parents[1] / "nova" / "assets"
        for name in ("eye-mask.png", "eye-catchlight.png", "eye-wetline.png", "eye-socket-shadow.png",
                     "face-warm-light.png", "lid-left-upper.png", "lid-left-lower.png",
                     "lid-right-upper.png", "lid-right-lower.png", "nova-face.jpg"):
            self.assertTrue((assets / name).is_file(), name)

    def test_face_qml_has_smoothed_gaze_and_soft_lids(self):
        qml = (Path(__file__).resolve().parents[1] / "nova" / "qml" / "Face.qml").read_text(encoding="utf-8")
        self.assertIn("SmoothedAnimation", qml)
        self.assertIn("EyeSocket", qml)
        self.assertIn("previewDemo", qml)
        self.assertIn("lid-left-upper.png", qml)
        self.assertIn("eye-catchlight.png", qml)
        self.assertNotIn('color: "#5C3324"', qml)

    def test_setup_bat_installs_python_312(self):
        bat = (Path(__file__).resolve().parents[1] / "setup.bat").read_text(encoding="utf-8")
        self.assertIn("Python.Python.3.12", bat)
        self.assertIn("py -3.12", bat)
        self.assertIn("pythonw", bat.lower())
        self.assertIn("setup.log", bat)
        self.assertIn("InstallAllUsers=0", bat)
