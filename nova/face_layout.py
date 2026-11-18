"""Normalized (0-1) regions on nova-face.jpg (1024×1024) for eye and mouth overlays."""

# Lid coverage — includes the lash margin, used to close the eye.
LEFT_LID = {"x": 0.345, "y": 0.358, "w": 0.120, "h": 0.075}
RIGHT_LID = {"x": 0.516, "y": 0.336, "w": 0.132, "h": 0.068}

# Tighter visible eyeball, inside the lashes, used to shift the real iris.
LEFT_EYE = {"x": 0.352, "y": 0.370, "w": 0.108, "h": 0.052}
RIGHT_EYE = {"x": 0.518, "y": 0.344, "w": 0.124, "h": 0.054}

# Closed-lips region used as the lip-sync box.
MOUTH = {"x": 0.418, "y": 0.488, "w": 0.195, "h": 0.070}
# Tight head crop for the corner bubble (cuts most of the shoulders).
BUBBLE_FOCUS = {"x": 0.18, "y": 0.02, "w": 0.66, "h": 0.78}

# Lip colours sampled from the portrait, for the open-mouth overlay.
LIP_UPPER = "#C38A72"
LIP_LOWER = "#A86B55"
LIP_INNER = "#2A0E0A"
TEETH = "#E8D4C8"
SKIN_LID = "#5C3324"
