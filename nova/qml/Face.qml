import QtQuick 2.15

Item {
    id: face
    property real mouthOpen: 0
    property string expression: "idle"
    property bool talking: false
    property bool listening: false
    property bool thinking: false
    property bool cropToHead: false
    property url portrait: nova && nova.faceImage ? nova.faceImage : ""
    // Cycle gaze and blinks for off-screen previews (no cursor).
    property bool previewDemo: false
    property bool trackCursor: true
    // When false, gazeX/Y can be set from a preview or test without being overwritten.
    property bool autoGaze: true

    // Lid coverage (includes the lash margin) and a tighter gaze box for the iris.
    // Normalised on the 1024² portrait — see nova/face_layout.py.
    readonly property var leftLid:  ({ x: 0.345, y: 0.358, w: 0.120, h: 0.075 })
    readonly property var rightLid: ({ x: 0.516, y: 0.336, w: 0.132, h: 0.068 })
    readonly property var leftGaze: ({ x: 0.352, y: 0.370, w: 0.108, h: 0.052 })
    readonly property var rightGaze:({ x: 0.518, y: 0.344, w: 0.124, h: 0.054 })
    readonly property var mouthBox: ({ x: 0.418, y: 0.488, w: 0.195, h: 0.070 })
    readonly property var focusBox: ({ x: 0.18,  y: 0.02,  w: 0.66,  h: 0.78  })

    clip: true
    antialiasing: true
    layer.enabled: true
    layer.smooth: true
    layer.samples: 4

    // Map of how shut the lids are (0 open, 1 fully closed).
    property real blink: 0
    property real lidBias: 0
    property real eyeScale: 1
    property real mouthRest: 0
    property real headTilt: 0
    property real glow: 0

    // Damped gaze in -1..1, plus a tiny idle saccade and breath.
    property real gazeX: 0
    property real gazeY: 0
    property real gazeTX: 0
    property real gazeTY: 0
    property real saccadeX: 0
    property real saccadeY: 0
    property real breath: 0
    property real pupil: 0.0          // extra dilation, 0..1
    property real lightPhase: 0

    Behavior on gazeX { SmoothedAnimation { duration: 220; maximumEasingTime: 300; reversingMode: SmoothedAnimation.Sync } }
    Behavior on gazeY { SmoothedAnimation { duration: 220; maximumEasingTime: 300; reversingMode: SmoothedAnimation.Sync } }
    Behavior on lidBias { NumberAnimation { duration: 280; easing.type: Easing.InOutSine } }
    Behavior on eyeScale { NumberAnimation { duration: 280; easing.type: Easing.InOutSine } }
    Behavior on pupil { NumberAnimation { duration: 420; easing.type: Easing.InOutSine } }

    function applyExpression() {
        var e = face.expression
        if (e === "sad") { lidBias = 0.28; eyeScale = 0.96; mouthRest = 0; headTilt = 4; glow = 0; pupil = 0.12 }
        else if (e === "thinking") { lidBias = 0.18; eyeScale = 1.0; mouthRest = 0; headTilt = -5; glow = 0.55; pupil = 0.2 }
        else if (e === "surprised") { lidBias = -0.15; eyeScale = 1.08; mouthRest = 0.35; headTilt = 0; glow = 0.2; pupil = -0.08 }
        else if (e === "laughing") { lidBias = 0.45; eyeScale = 0.92; mouthRest = 0.55; headTilt = -2; glow = 0.15; pupil = 0 }
        else if (e === "listening") { lidBias = 0.05; eyeScale = 1.04; mouthRest = 0; headTilt = -3; glow = 0.35; pupil = 0.08 }
        else if (e === "happy") { lidBias = 0.12; eyeScale = 1.0; mouthRest = 0; headTilt = 0; glow = 0.1; pupil = 0 }
        else { lidBias = 0; eyeScale = 1; mouthRest = 0; headTilt = 0; glow = 0; pupil = 0 }
    }

    onExpressionChanged: applyExpression()
    Component.onCompleted: applyExpression()

    function _clampf(v, a, b) { return Math.max(a, Math.min(b, v)) }

    function tickCursor() {
        var tx = face.saccadeX
        var ty = face.saccadeY
        if (face.previewDemo) {
            var t = demoClock.t
            tx += 0.55 * Math.sin(t * 0.6)
            ty += 0.22 * Math.sin(t * 0.42 + 0.7)
        } else if (face.trackCursor && typeof nova !== "undefined" && nova && (nova.cursorX !== undefined)) {
            var mid = face.mapToGlobal(Qt.point(face.width * 0.5, face.height * 0.42))
            var dx = nova.cursorX - mid.x
            var dy = nova.cursorY - mid.y
            // Soft, limited range so the real iris stays inside the socket.
            tx += face._clampf(dx / 520, -1, 1)
            ty += face._clampf(dy / 700, -0.7, 0.7)
        }
        face.gazeTX = face._clampf(tx, -1, 1)
        face.gazeTY = face._clampf(ty, -0.75, 0.75)
        if (face.autoGaze) {
            face.gazeX = face.gazeTX
            face.gazeY = face.gazeTY
        }
    }

    // Idle bob as a tiny Y offset — do not touch anchors.topMargin (that would
    // collapse the real margins and jump the layout).
    property real bobY: 0
    SequentialAnimation on bobY {
        running: true; loops: Animation.Infinite
        NumberAnimation { to: 2; duration: 3200; easing.type: Easing.InOutSine }
        NumberAnimation { to: 0; duration: 3200; easing.type: Easing.InOutSine }
    }
    SequentialAnimation on breath {
        running: true; loops: Animation.Infinite
        NumberAnimation { to: 1; duration: 4200; easing.type: Easing.InOutSine }
        NumberAnimation { to: 0; duration: 4200; easing.type: Easing.InOutSine }
    }

    Timer {
        interval: 16
        running: true
        repeat: true
        onTriggered: {
            face.lightPhase += 0.016
            face.tickCursor()
        }
    }

    Timer {
        id: demoClock
        property real t: 0
        interval: 16
        running: face.previewDemo
        repeat: true
        onTriggered: t += 0.016
    }

    // Natural blinks: faster close, slightly slower open, occasional double.
    Timer {
        id: blinkTimer
        interval: 4200
        running: true
        repeat: true
        property bool second: false
        onTriggered: {
            blinkAnim.restart()
            if (!second && Math.random() < 0.16) {
                second = true
                interval = 220
            } else {
                second = false
                interval = 2600 + Math.random() * 3800
            }
        }
    }
    SequentialAnimation {
        id: blinkAnim
        NumberAnimation { target: face; property: "blink"; to: 1; duration: 72; easing.type: Easing.InQuad }
        PauseAnimation { duration: 36 }
        NumberAnimation { target: face; property: "blink"; to: 0; duration: 140; easing.type: Easing.OutQuad }
    }

    // Subtle idle saccades (small, infrequent, not a constant wobble).
    Timer {
        interval: 1800
        running: true
        repeat: true
        onTriggered: {
            if (Math.random() < 0.55) {
                face.saccadeX = (Math.random() - 0.5) * 0.22
                face.saccadeY = (Math.random() - 0.5) * 0.12
            } else {
                face.saccadeX = 0
                face.saccadeY = 0
            }
            interval = 1400 + Math.random() * 2800
        }
    }

    Item {
        id: portrait
        // Keep the square portrait unsquashed so eye overlays sit on the real pixels.
        property real side: face.cropToHead
                            ? Math.max(face.width / face.focusBox.w, face.height / face.focusBox.h)
                            : Math.min(face.width, face.height)
        width: side
        height: side
        x: face.cropToHead ? -face.focusBox.x * side : (face.width - side) / 2
        y: (face.cropToHead ? -face.focusBox.y * side : (face.height - side) / 2) + face.bobY
        rotation: face.headTilt
        transformOrigin: Item.Center
        scale: (face.expression === "listening" ? 1.02 : 1.0) * (1 + face.breath * 0.008)
        antialiasing: true
        Behavior on rotation { NumberAnimation { duration: 320; easing.type: Easing.InOutSine } }
        Behavior on scale { NumberAnimation { duration: 320; easing.type: Easing.InOutSine } }

        Image {
            id: photo
            anchors.fill: parent
            source: face.portrait
            fillMode: Image.Stretch
            smooth: true
            asynchronous: false
            antialiasing: true
            mipmap: true
        }

        // Warm key-light that eases with gaze and head tilt (same side as the portrait lighting).
        Image {
            id: warmLight
            source: Qt.resolvedUrl("../assets/face-warm-light.png")
            width: parent.width * 0.7
            height: parent.height * 0.7
            x: parent.width * 0.42 + face.gazeX * parent.width * 0.04 + face.headTilt * 0.6
            y: parent.height * 0.08 - face.gazeY * parent.height * 0.03
            opacity: 0.10 + 0.05 * Math.abs(face.gazeX) + 0.04 * face.glow
            smooth: true
            visible: opacity > 0.02
        }

        // Gold circuit glow for thinking / listening.
        Rectangle {
            anchors.fill: photo
            color: "#40E8C36A"
            opacity: face.glow * (0.55 + 0.45 * Math.sin(glowTime.t))
            visible: opacity > 0.02
        }
        Timer {
            id: glowTime
            property real t: 0
            interval: 40; running: face.glow > 0; repeat: true
            onTriggered: t += 0.13
        }

        EyeSocket {
            isLeft: true
            lidBox: face.leftLid
            gazeBox: face.leftGaze
            lidUpper: Qt.resolvedUrl("../assets/lid-left-upper.png")
            lidLower: Qt.resolvedUrl("../assets/lid-left-lower.png")
        }
        EyeSocket {
            isLeft: false
            lidBox: face.rightLid
            gazeBox: face.rightGaze
            lidUpper: Qt.resolvedUrl("../assets/lid-right-upper.png")
            lidLower: Qt.resolvedUrl("../assets/lid-right-lower.png")
        }

        // Open-mouth overlay, lip-synced from the spoken WAV envelope.
        Item {
            id: mouth
            x: face.mouthBox.x * portrait.width
            y: face.mouthBox.y * portrait.height
            width: face.mouthBox.w * portrait.width
            height: face.mouthBox.h * portrait.height
            property real openAmt: Math.max(face.mouthOpen, face.mouthRest)
            visible: openAmt > 0.04
            opacity: Math.min(1, openAmt * 3)

            Rectangle {
                anchors.horizontalCenter: parent.horizontalCenter
                anchors.verticalCenter: parent.verticalCenter
                width: parent.width * (0.72 + 0.1 * mouth.openAmt)
                height: parent.height * (0.18 + 0.72 * mouth.openAmt)
                radius: width
                color: "#2A0E0A"
                border.color: "#A86B55"
                border.width: Math.max(2, parent.height * 0.06)
                antialiasing: true

                Rectangle {
                    anchors.horizontalCenter: parent.horizontalCenter
                    anchors.top: parent.top
                    anchors.topMargin: parent.height * 0.08
                    width: parent.width * 0.62
                    height: parent.height * 0.22 * Math.max(0, mouth.openAmt - 0.25)
                    radius: 2
                    color: "#E8D4C8"
                    visible: height > 1
                    opacity: 0.9
                }
            }
            Rectangle {
                anchors.horizontalCenter: parent.horizontalCenter
                anchors.bottom: parent.bottom
                anchors.bottomMargin: parent.height * 0.08 * (1 - mouth.openAmt)
                width: parent.width * 0.7
                height: parent.height * 0.22
                radius: width
                color: "#C38A72"
                opacity: 0.75
                antialiasing: true
            }
        }
    }

    // Fallback if the portrait is missing.
    Rectangle {
        anchors.fill: parent
        color: "#1A1210"
        visible: photo.status !== Image.Ready
        Text {
            anchors.centerIn: parent
            text: "Nova"
            color: "#E8C36A"
            font.pixelSize: Math.max(16, face.width * 0.12)
        }
    }

    // One eye: the real iris pixels shift inside a soft almond mask, and
    // skin-sampled lids close inside that same opening (never a rectangle on the cheek).
    component EyeSocket: Item {
        id: sock
        property bool isLeft: true
        property var lidBox
        property var gazeBox
        property url lidUpper
        property url lidLower

        x: lidBox.x * portrait.width
        y: lidBox.y * portrait.height
        width: lidBox.w * portrait.width
        height: lidBox.h * portrait.height
        scale: face.eyeScale
        transformOrigin: Item.Center
        antialiasing: true

        Canvas {
            id: eyeCanvas
            anchors.fill: parent
            antialiasing: true
            renderStrategy: Canvas.Immediate
            renderTarget: Canvas.Image
            property real gx: face.gazeX
            property real gy: face.gazeY
            property real dilate: face.pupil
            property real bl: face.blink
            property real lb: face.lidBias
            property int photoStatus: photo.status
            onGxChanged: requestPaint()
            onGyChanged: requestPaint()
            onDilateChanged: requestPaint()
            onBlChanged: requestPaint()
            onLbChanged: requestPaint()
            onPhotoStatusChanged: requestPaint()
            onWidthChanged: requestPaint()
            onHeightChanged: requestPaint()
            Component.onCompleted: requestPaint()

            Image {
                id: maskImg
                source: Qt.resolvedUrl("../assets/eye-mask.png")
                visible: false
                width: eyeCanvas.width
                height: eyeCanvas.height
                onStatusChanged: if (status === Image.Ready) eyeCanvas.requestPaint()
            }
            Image {
                id: shadowImg
                source: Qt.resolvedUrl("../assets/eye-socket-shadow.png")
                visible: false
            }
            Image {
                id: wetImg
                source: Qt.resolvedUrl("../assets/eye-wetline.png")
                visible: false
            }
            Image {
                id: catchImg
                source: Qt.resolvedUrl("../assets/eye-catchlight.png")
                visible: false
            }
            Image {
                id: upperSrc
                source: sock.lidUpper
                visible: false
                onStatusChanged: if (status === Image.Ready) eyeCanvas.requestPaint()
            }
            Image {
                id: lowerSrc
                source: sock.lidLower
                visible: false
                onStatusChanged: if (status === Image.Ready) eyeCanvas.requestPaint()
            }

            onPaint: {
                var ctx = getContext("2d")
                var w = width, h = height
                if (w < 2 || h < 2)
                    return
                ctx.clearRect(0, 0, w, h)
                if (photo.status !== Image.Ready)
                    return

                var iw = photo.sourceSize.width || 1024
                var ih = photo.sourceSize.height || 1024
                var box = sock.lidBox
                var pad = 0.35
                var ox = face.gazeX * w * 0.11
                var oy = face.gazeY * h * 0.09
                var sx = (box.x - box.w * pad) * iw
                var sy = (box.y - box.h * pad) * ih
                var sw = box.w * (1 + 2 * pad) * iw
                var sh = box.h * (1 + 2 * pad) * ih
                var dx = -w * pad + ox
                var dy = -h * pad + oy
                var dw = w * (1 + 2 * pad)
                var dh = h * (1 + 2 * pad)

                ctx.drawImage(photo, sx, sy, sw, sh, dx, dy, dw, dh)

                // A very light socket shade, only enough to seat the iris. The portrait
                // already has catchlights; they travel with the shifted pixels.
                ctx.globalAlpha = 0.22
                ctx.drawImage(shadowImg, 0, 0, w, h)
                ctx.globalAlpha = 1

                // Lids — only when blinking or the expression actually droops them.
                // Resting lids stay the painted ones on the portrait, so no extra sliver.
                var upper = Math.max(0, Math.min(1, face.blink * 1.0 + Math.max(0, face.lidBias) * 0.55))
                var lower = Math.max(0, Math.min(0.55, face.blink * 0.48 + Math.max(0, face.lidBias) * 0.22))
                // Clip-and-draw the lid textures at their natural size so they are
                // not squashed, and so a soft leading edge is only at the lash line.
                ctx.fillStyle = sock.isLeft ? "#6B3E24" : "#3E2214"
                if (upper > 0.01) {
                    ctx.fillRect(0, 0, w, h * upper)
                    ctx.drawImage(upperSrc, 0, 0, w, h * upper)
                }
                ctx.fillStyle = sock.isLeft ? "#7A4A2C" : "#5A3420"
                if (lower > 0.01) {
                    ctx.fillRect(0, h * (1 - lower), w, h * lower)
                    ctx.drawImage(lowerSrc, 0, h * (1 - lower), w, h * lower)
                }

                // Soft almond clip so nothing rectangular is stamped on the cheek.
                ctx.globalCompositeOperation = "destination-in"
                ctx.drawImage(maskImg, 0, 0, w, h)
                ctx.globalCompositeOperation = "source-over"
            }
        }
    }
}
