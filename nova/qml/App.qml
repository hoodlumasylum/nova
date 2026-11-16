import QtQuick 2.15
import QtQuick.Window 2.15
import QtQuick.Controls 2.15

QtObject {
    id: root

    property int bubbleSize: 176

    property var bubble: Window {
        id: bubbleWindow
        objectName: "bubbleWindow"
        width: root.bubbleSize
        height: root.bubbleSize
        visible: false
        color: "transparent"
        title: "Nova"
        flags: Qt.FramelessWindowHint | Qt.WindowStaysOnTopHint | Qt.Tool | Qt.NoDropShadowWindowHint

        property point dragStart: Qt.point(0, 0)
        property point winStart: Qt.point(0, 0)
        property bool dragging: false

        Rectangle {
            anchors.fill: parent
            radius: width / 2
            color: "#E61A1210"
            border.color: "#E8C36A"
            border.width: 3
            clip: true

            Face {
                id: bubbleFace
                anchors.fill: parent
                anchors.margins: 6
                cropToHead: true
                mouthOpen: nova.mouthOpen
                expression: nova.listening ? "listening" : (nova.thinking ? "thinking" : nova.expression)
                talking: nova.talking
                listening: nova.listening
                thinking: nova.thinking
            }

            // Listening ring
            Rectangle {
                anchors.fill: parent
                radius: width / 2
                color: "transparent"
                border.color: nova.listening ? "#7CE8C36A" : (nova.thinking ? "#55E8C36A" : "transparent")
                border.width: 5
                visible: nova.listening || nova.thinking
            }
        }

        MouseArea {
            id: dragArea
            anchors.fill: parent
            acceptedButtons: Qt.LeftButton | Qt.RightButton
            property bool moved: false
            property real pressX: 0
            property real pressY: 0
            onPressed: function(mouse) {
                moved = false
                pressX = mouse.x
                pressY = mouse.y
            }
            onPositionChanged: function(mouse) {
                if (!(mouse.buttons & Qt.LeftButton))
                    return
                var dx = mouse.x - pressX
                var dy = mouse.y - pressY
                if (!moved && Math.abs(dx) < 6 && Math.abs(dy) < 6)
                    return
                moved = true
                bubbleWindow.x += dx
                bubbleWindow.y += dy
            }
            onReleased: function(mouse) {
                if (mouse.button === Qt.RightButton) {
                    nova.setPage("settings")
                    return
                }
                if (moved)
                    nova.saveBubblePos(bubbleWindow.x, bubbleWindow.y)
                else
                    nova.toggleExpanded()
            }
        }
    }

    property var main: Window {
        id: mainWindow
        objectName: "mainWindow"
        visible: false
        color: "#0E0C0A"
        title: "Nova"
        width: 1100
        height: 720

        Rectangle {
            anchors.fill: parent
            color: "#0E0C0A"

            // Left: large face
            Item {
                id: facePane
                width: Math.min(parent.width * 0.42, parent.height * 0.85)
                anchors.left: parent.left
                anchors.top: parent.top
                anchors.bottom: parent.bottom
                anchors.margins: 24

                Rectangle {
                    anchors.fill: parent
                    radius: 24
                    color: "#1A1210"
                    border.color: "#E8C36A"
                    border.width: 2
                    clip: true

                    Face {
                        anchors.fill: parent
                        cropToHead: true
                        mouthOpen: nova.mouthOpen
                        expression: nova.listening ? "listening" : (nova.thinking ? "thinking" : nova.expression)
                        talking: nova.talking
                        listening: nova.listening
                        thinking: nova.thinking
                    }
                }

                Text {
                    anchors.horizontalCenter: parent.horizontalCenter
                    anchors.bottom: parent.bottom
                    anchors.bottomMargin: 14
                    text: nova.listening ? "Listening…" : (nova.thinking ? "Thinking…" : (nova.talking ? "Speaking…" : "Nova"))
                    color: "#E8C36A"
                    font.pixelSize: 18
                    style: Text.Outline
                    styleColor: "#000000"
                }
            }

            Column {
                id: rightPane
                anchors.left: facePane.right
                anchors.right: parent.right
                anchors.top: parent.top
                anchors.bottom: parent.bottom
                anchors.margins: 24
                anchors.leftMargin: 18
                spacing: 12

                Row {
                    spacing: 8
                    Repeater {
                        model: [
                            { id: "chat", label: "Chat" },
                            { id: "video", label: "Video prompt" },
                            { id: "settings", label: "Settings" },
                            { id: "setup", label: "Setup" }
                        ]
                        delegate: Rectangle {
                            required property var modelData
                            width: tabLabel.implicitWidth + 28
                            height: 36
                            radius: 18
                            color: nova.page === modelData.id ? "#E8C36A" : "#2A211C"
                            border.color: "#E8C36A"
                            border.width: 1
                            Text {
                                id: tabLabel
                                anchors.centerIn: parent
                                text: modelData.label
                                color: nova.page === modelData.id ? "#1A1210" : "#F3E6D0"
                                font.pixelSize: 14
                            }
                            MouseArea { anchors.fill: parent; onClicked: nova.setPage(modelData.id) }
                        }
                    }
                    Item { width: 12; height: 1 }
                    Rectangle {
                        width: shrinkLabel.implicitWidth + 24
                        height: 36
                        radius: 18
                        color: "#2A211C"
                        border.color: "#8A7A60"
                        border.width: 1
                        Text { id: shrinkLabel; anchors.centerIn: parent; text: "Shrink to corner"; color: "#F3E6D0"; font.pixelSize: 13 }
                        MouseArea { anchors.fill: parent; onClicked: nova.shrinkToBubble() }
                    }
                }

                Text {
                    width: parent.width
                    wrapMode: Text.WordWrap
                    color: "#C9B89A"
                    font.pixelSize: 12
                    text: nova.status !== "" ? nova.status : nova.speedNote
                }

                Loader {
                    id: pageLoader
                    width: parent.width
                    height: parent.height - 90
                    sourceComponent: nova.page === "video" ? videoPage
                                     : nova.page === "settings" ? settingsPage
                                     : nova.page === "setup" ? setupPage
                                     : chatPage
                }
            }
        }

        Shortcut {
            sequence: "Escape"
            onActivated: nova.shrinkToBubble()
        }
        Shortcut {
            sequence: nova.ptt
            onActivated: nova.startTalk()
            enabled: nova.page === "chat"
        }
    }

    property Component chatPage: ChatPage {}
    property Component videoPage: VideoPage {}
    property Component settingsPage: SettingsPage {}
    property Component setupPage: SetupPage {}
}
