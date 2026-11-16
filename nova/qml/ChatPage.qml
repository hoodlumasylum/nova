import QtQuick 2.15
import QtQuick.Controls 2.15

Item {
    id: root

    Column {
        anchors.fill: parent
        spacing: 10

        Rectangle {
            width: parent.width
            height: parent.height - 64
            radius: 16
            color: "#16110F"
            border.color: "#3A2E26"
            clip: true

            ListView {
                id: list
                anchors.fill: parent
                anchors.margins: 14
                spacing: 10
                model: nova.messages
                clip: true
                delegate: Rectangle {
                    required property var modelData
                    width: list.width
                    height: body.implicitHeight + 18
                    radius: 12
                    color: modelData.role === "user" ? "#2A211C" : "#24180F"
                    border.color: modelData.role === "user" ? "#5A4A3A" : "#E8C36A"
                    border.width: 1
                    Text {
                        id: body
                        anchors.left: parent.left
                        anchors.right: parent.right
                        anchors.verticalCenter: parent.verticalCenter
                        anchors.margins: 10
                        text: (modelData.role === "user" ? "You: " : "Nova: ") + modelData.content
                        wrapMode: Text.Wrap
                        color: "#F3E6D0"
                        font.pixelSize: 14
                    }
                }
                onCountChanged: Qt.callLater(function() { list.positionViewAtEnd() })
            }

            Text {
                anchors.centerIn: parent
                visible: nova.messages.length === 0
                color: "#8A7A60"
                width: parent.width * 0.8
                wrapMode: Text.Wrap
                horizontalAlignment: Text.AlignHCenter
                text: nova.setupNeeded
                    ? "Finish Setup to download a local model and the CodeAque voice. After that, chat stays on this PC."
                    : "Type a message, or press Talk / your push-to-talk key and speak. History is saved only on this PC."
            }
        }

        Row {
            spacing: 8
            width: parent.width

            Rectangle {
                width: parent.width - 168
                height: 48
                radius: 12
                color: "#2A211C"
                border.color: "#E8C36A"
                TextInput {
                    id: input
                    anchors.fill: parent
                    anchors.margins: 12
                    color: "#F3E6D0"
                    font.pixelSize: 14
                    clip: true
                    enabled: !nova.busy
                    text: nova.draft
                    onTextChanged: nova.draft = text
                    Keys.onReturnPressed: {
                        nova.submitChat(text)
                        text = ""
                    }
                    Text {
                        anchors.fill: parent
                        text: "Message Nova…"
                        color: "#8A7A60"
                        visible: !input.text
                        font.pixelSize: 14
                    }
                }
            }

            Rectangle {
                width: 72
                height: 48
                radius: 12
                color: nova.listening ? "#E8C36A" : "#2A211C"
                border.color: "#E8C36A"
                enabled: !nova.busy || nova.listening
                Text {
                    anchors.centerIn: parent
                    text: nova.listening ? "…" : "Talk"
                    color: nova.listening ? "#1A1210" : "#F3E6D0"
                    font.pixelSize: 14
                }
                MouseArea { anchors.fill: parent; onClicked: nova.startTalk() }
            }
            Rectangle {
                width: 72
                height: 48
                radius: 12
                color: "#2A211C"
                border.color: "#E8C36A"
                Text { anchors.centerIn: parent; text: "Send"; color: "#E8C36A"; font.pixelSize: 14 }
                MouseArea {
                    anchors.fill: parent
                    onClicked: {
                        nova.submitChat(input.text)
                        input.text = ""
                    }
                }
            }
        }
    }
}
