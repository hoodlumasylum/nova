"""Normalized (0-1) regions on nova-face.jpg (1024×1024) for eye and mouth overlays."""

# Viewer-left eye (her right). Tuned from the portrait.
LEFT_EYE = {"x": 0.345, "y": 0.358, "w": 0.120, "h": 0.075}
# Viewer-right eye (her left).
RIGHT_EYE = {"x": 0.528, "y": 0.346, "w": 0.128, "h": 0.080}
# Closed-lips region used as the lip-sync box.
MOUTH = {"x": 0.412, "y": 0.528, "w": 0.205, "h": 0.092}
# Tight head crop for the corner bubble (cuts most of the shoulders).
BUBBLE_FOCUS = {"x": 0.18, "y": 0.02, "w": 0.66, "h": 0.78}

# Lip colours sampled from the portrait, for the open-mouth overlay.
LIP_UPPER = "#C38A72"
LIP_LOWER = "#A86B55"
LIP_INNER = "#2A0E0A"
TEETH = "#E8D4C8"
SKIN_LID = "#5C3324"
