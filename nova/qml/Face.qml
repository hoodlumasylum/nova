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

    // Normalized boxes on the 1024² portrait (see nova/face_layout.py).
    readonly property var leftEye:  ({ x: 0.345, y: 0.358, w: 0.120, h: 0.075 })
    readonly property var rightEye: ({ x: 0.528, y: 0.346, w: 0.128, h: 0.080 })
    readonly property var mouthBox: ({ x: 0.412, y: 0.528, w: 0.205, h: 0.092 })
    readonly property var focusBox: ({ x: 0.18,  y: 0.02,  w: 0.66,  h: 0.78  })

    clip: true

    // Map of how shut the lids are (0 open, 1 fully closed).
    property real blink: 0
    property real lidBias: 0          // extra droop from expression
    property real eyeScale: 1
    property real mouthRest: 0        // slightly open when laughing / surprised even if silent
    property real headTilt: 0
    property real glow: 0

    function applyExpression() {
        var e = face.expression
        if (e === "sad") { lidBias = 0.28; eyeScale = 0.96; mouthRest = 0; headTilt = 4; glow = 0 }
        else if (e === "thinking") { lidBias = 0.18; eyeScale = 1.0; mouthRest = 0; headTilt = -5; glow = 0.55 }
        else if (e === "surprised") { lidBias = -0.15; eyeScale = 1.08; mouthRest = 0.35; headTilt = 0; glow = 0.2 }
        else if (e === "laughing") { lidBias = 0.45; eyeScale = 0.92; mouthRest = 0.55; headTilt = -2; glow = 0.15 }
        else if (e === "listening") { lidBias = 0.05; eyeScale = 1.04; mouthRest = 0; headTilt = -3; glow = 0.35 }
        else if (e === "happy") { lidBias = 0.12; eyeScale = 1.0; mouthRest = 0.08; headTilt = 0; glow = 0.1 }
        else { lidBias = 0; eyeScale = 1; mouthRest = 0; headTilt = 0; glow = 0 }
    }

    onExpressionChanged: applyExpression()
    Component.onCompleted: applyExpression()

    // Idle bob and thinking pulse.
    SequentialAnimation on anchors.topMargin {
        running: true; loops: Animation.Infinite
        NumberAnimation { to: 2; duration: 2600; easing.type: Easing.InOutSine }
        NumberAnimation { to: 0; duration: 2600; easing.type: Easing.InOutSine }
    }

    Timer {
        interval: 4200
        running: true
        repeat: true
        onTriggered: {
            // Don't blink mid-surprise if lids are meant to stay wide — still a short blink is fine.
            blinkAnim.restart()
            interval = 2800 + Math.random() * 3500
        }
    }
    SequentialAnimation {
        id: blinkAnim
        NumberAnimation { target: face; property: "blink"; to: 1; duration: 70 }
        PauseAnimation { duration: 50 }
        NumberAnimation { target: face; property: "blink"; to: 0; duration: 110 }
    }

    Item {
        id: portrait
        width: face.cropToHead ? face.width / face.focusBox.w : face.width
        height: face.cropToHead ? face.height / face.focusBox.h : face.height
        x: face.cropToHead ? -face.focusBox.x * width : 0
        y: face.cropToHead ? -face.focusBox.y * height : 0
        rotation: face.headTilt
        transformOrigin: Item.Center
        scale: face.expression === "listening" ? 1.02 : 1.0
        Behavior on rotation { NumberAnimation { duration: 280 } }
        Behavior on scale { NumberAnimation { duration: 280 } }

        Image {
            id: photo
            anchors.fill: parent
            source: face.portrait
            fillMode: Image.PreserveAspectFit
            smooth: true
            asynchronous: true
        }

        // Gold circuit glow for thinking / listening.
        Rectangle {
            anchors.fill: photo
            color: "#40E8C36A"
            opacity: face.glow * (0.55 + 0.45 * Math.sin(glowTime.t))
            visible: opacity > 0.02
            // Keep the photo, just wash a little gold over it.
        }
        Timer {
            id: glowTime
            property real t: 0
            interval: 40; running: face.glow > 0; repeat: true
            onTriggered: t += 0.13
        }

        // Eyelids — skin-coloured covers over each eye opening.
        Repeater {
            model: [face.leftEye, face.rightEye]
            delegate: Item {
                required property var modelData
                x: modelData.x * portrait.width
                y: modelData.y * portrait.height
                width: modelData.w * portrait.width
                height: modelData.h * portrait.height
                scale: face.eyeScale
                transformOrigin: Item.Center

                Rectangle {
                    id: upperLid
                    anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top
                    height: parent.height * Math.max(0, Math.min(1, face.blink * 0.85 + face.lidBias * 0.55 + 0.08))
                    color: "#5C3324"
                    opacity: 0.92
                    radius: width
                }
                Rectangle {
                    anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom
                    height: parent.height * Math.max(0, Math.min(0.55, face.blink * 0.45 + Math.max(0, face.lidBias) * 0.25))
                    color: "#4A291E"
                    opacity: 0.88
                    radius: width
                }
            }
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

            // Interior
            Rectangle {
                anchors.horizontalCenter: parent.horizontalCenter
                anchors.verticalCenter: parent.verticalCenter
                width: parent.width * (0.72 + 0.1 * mouth.openAmt)
                height: parent.height * (0.18 + 0.72 * mouth.openAmt)
                radius: width
                color: "#2A0E0A"
                border.color: "#A86B55"
                border.width: Math.max(2, parent.height * 0.06)

                // Teeth on bigger openings
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
            // Lower lip highlight
            Rectangle {
                anchors.horizontalCenter: parent.horizontalCenter
                anchors.bottom: parent.bottom
                anchors.bottomMargin: parent.height * 0.08 * (1 - mouth.openAmt)
                width: parent.width * 0.7
                height: parent.height * 0.22
                radius: width
                color: "#C38A72"
                opacity: 0.75
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
}
