"""Offline image-to-video prompt writer. Nova never calls Seedance or any other online API."""
from __future__ import annotations

from pathlib import Path

SECTIONS = (
    "Subject",
    "Action / motion",
    "Camera movement",
    "Lighting",
    "Style",
    "Duration",
    "Negative notes",
)

DISCLAIMER = (
    "Nova only writes a prompt you can paste into a video tool such as Seedance. "
    "She does not call Seedance, does not upload your still, and does not generate video."
)

SYSTEM = """You write ready-to-paste image-to-video prompts for tools such as Seedance, Kling, Runway, or Luma.

You do not generate video. You do not call any online API or service. You only write text the user can copy.

Write in clear English. Keep it concrete and filmable. Do not invent copyrighted characters, real celebrities, or logos the user did not ask for.

Always use this layout, with these exact headings:

Subject:
Action / motion:
Camera movement:
Lighting:
Style:
Duration:
Negative notes:

Paste this:
<one dense paragraph the user can paste into the video tool, covering the same details>

If the user attached a still image, mention that they should use that same still as the image input in their video tool. You cannot see the pixels of the image; rely on their description and the filename.

Duration defaults to 5 seconds if they do not say otherwise.
Negative notes should list things to avoid (jitter, morphing hands, extra limbs, watermark, text, cut, jump).
"""


def user_message(idea: str, image_path: str | None = None) -> str:
    idea = (idea or "").strip()
    lines = [
        "Write an image-to-video prompt from this idea:",
        idea or "(The user did not add a description — write a careful generic prompt and say what is missing.)",
    ]
    if image_path:
        p = Path(image_path)
        lines.append(
            f"They will use a local still image as the starting frame. Filename: {p.name}. "
            "Remind them to attach that same still in their video tool. Do not claim you viewed the pixels."
        )
    return "\n".join(lines)


def template(idea: str, image_name: str = "", duration: str = "5 seconds") -> str:
    """Deterministic fallback if the local model is not installed yet."""
    idea = (idea or "").strip() or "A subject matching the attached still, with gentle natural motion."
    subject = idea.split(".")[0].strip()[:240]
    if image_name:
        subject += f" Start from the user's still image ({image_name}) as the first frame."
    action = f"The subject moves naturally and continuously, following this idea: {idea}"
    camera = "Slow, stable camera; a gentle push-in or slight orbit; no hard cuts."
    lighting = "Keep the lighting consistent with the still; soft, cinematic, no sudden exposure jumps."
    style = "Photoreal or match the still; smooth motion, sharp focus, no flicker."
    negative = (
        "jitter, flicker, morphing face or hands, extra limbs, warped background, "
        "watermark, subtitles, text overlay, jump cut, still frame, low resolution"
    )
    paste = (
        f"{subject} {action} {camera} {lighting} {style} Duration {duration}. "
        f"Avoid {negative}."
    )
    if image_name:
        paste += f" Use the still {image_name} as the input image."
    return format_prompt(
        {
            "Subject": subject,
            "Action / motion": action,
            "Camera movement": camera,
            "Lighting": lighting,
            "Style": style,
            "Duration": duration,
            "Negative notes": negative,
        },
        paste,
    )


def format_prompt(sections: dict[str, str], paste: str) -> str:
    lines = []
    for name in SECTIONS:
        lines.append(f"{name}:")
        lines.append((sections.get(name) or "").strip() or "—")
        lines.append("")
    lines.append("Paste this:")
    lines.append((paste or "").strip())
    return "\n".join(lines).strip() + "\n"


def parse_model_output(text: str, idea: str = "", image_name: str = "") -> str:
    """Keep a well-structured model reply; otherwise fall back to the template."""
    raw = (text or "").strip()
    if not raw:
        return template(idea, image_name)
    low = raw.lower()
    needed = ("subject", "action", "camera", "lighting", "style", "duration", "negative")
    if sum(1 for n in needed if n in low) >= 5 and "paste" in low:
        return raw if raw.endswith("\n") else raw + "\n"
    # Model wrote something loose — keep it, but prepend the section template so it's paste-ready.
    base = template(idea, image_name)
    return base + "\nModel notes:\n" + raw + "\n"
