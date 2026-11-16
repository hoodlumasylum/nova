import QtQuick 2.15
import QtQuick.Controls 2.15

Item {
    id: root

    Flickable {
        anchors.fill: parent
        contentWidth: width
        contentHeight: col.height
        clip: true

        Column {
            id: col
            width: parent.width
            spacing: 10

            Text {
                width: parent.width
                wrapMode: Text.WordWrap
                color: "#E8C36A"
                font.pixelSize: 16
                text: "Video prompt"
            }
            Text {
                width: parent.width
                wrapMode: Text.WordWrap
                color: "#C9B89A"
                font.pixelSize: 13
                text: nova.videoDisclaimer
            }

            Text {
                width: parent.width
                wrapMode: Text.WordWrap
                color: "#8A7A60"
                font.pixelSize: 12
                text: "Describe the idea. You can optionally attach a still — it stays on this PC. Nova will write a prompt with subject, action/motion, camera, lighting, style, duration, and negative notes, plus a paste-ready paragraph."
            }

            Rectangle {
                width: parent.width
                height: 90
                radius: 12
                color: "#2A211C"
                border.color: "#E8C36A"
                TextArea {
                    id: idea
                    anchors.fill: parent
                    anchors.margins: 8
                    color: "#F3E6D0"
                    font.pixelSize: 14
                    wrapMode: TextArea.Wrap
                    background: null
                    text: nova.videoIdea
                    onTextChanged: nova.videoIdea = text
                    placeholderText: "e.g. She looks toward the camera, gold circuitry lights pulse, slow push-in…"
                    placeholderTextColor: "#8A7A60"
                }
            }

            Row {
                spacing: 8
                Rectangle {
                    width: attachLabel.implicitWidth + 24
                    height: 36
                    radius: 12
                    color: "#2A211C"
                    border.color: "#E8C36A"
                    Text { id: attachLabel; anchors.centerIn: parent; text: nova.videoImage ? "Change still…" : "Attach still (optional)"; color: "#F3E6D0"; font.pixelSize: 13 }
                    MouseArea { anchors.fill: parent; onClicked: nova.pickVideoImage() }
                }
                Rectangle {
                    visible: nova.videoImage !== ""
                    width: clearLabel.implicitWidth + 24
                    height: 36
                    radius: 12
                    color: "#2A211C"
                    border.color: "#8A7A60"
                    Text { id: clearLabel; anchors.centerIn: parent; text: "Remove still"; color: "#F3E6D0"; font.pixelSize: 13 }
                    MouseArea { anchors.fill: parent; onClicked: nova.clearVideoImage() }
                }
                Text {
                    anchors.verticalCenter: parent.verticalCenter
                    color: "#8A7A60"
                    font.pixelSize: 12
                    text: nova.videoImage ? nova.videoImage : ""
                    elide: Text.ElideMiddle
                    width: 280
                }
            }

            Image {
                visible: nova.videoImageUrl !== ""
                source: nova.videoImageUrl
                width: 160
                height: 160
                fillMode: Image.PreserveAspectCrop
            }

            Row {
                spacing: 8
                Rectangle {
                    width: writeLabel.implicitWidth + 28
                    height: 40
                    radius: 12
                    color: "#E8C36A"
                    enabled: !nova.busy
                    opacity: nova.busy ? 0.5 : 1
                    Text { id: writeLabel; anchors.centerIn: parent; text: nova.busy ? "Writing…" : "Write prompt"; color: "#1A1210"; font.pixelSize: 14 }
                    MouseArea { anchors.fill: parent; onClicked: nova.writeVideoPrompt() }
                }
                Rectangle {
                    width: copyLabel.implicitWidth + 28
                    height: 40
                    radius: 12
                    color: "#2A211C"
                    border.color: "#E8C36A"
                    enabled: nova.videoOut !== ""
                    opacity: nova.videoOut !== "" ? 1 : 0.4
                    Text { id: copyLabel; anchors.centerIn: parent; text: "Copy"; color: "#E8C36A"; font.pixelSize: 14 }
                    MouseArea { anchors.fill: parent; onClicked: nova.copyVideoPrompt() }
                }
                Rectangle {
                    width: saveLabel.implicitWidth + 28
                    height: 40
                    radius: 12
                    color: "#2A211C"
                    border.color: "#E8C36A"
                    enabled: nova.videoOut !== ""
                    opacity: nova.videoOut !== "" ? 1 : 0.4
                    Text { id: saveLabel; anchors.centerIn: parent; text: "Save to memory"; color: "#F3E6D0"; font.pixelSize: 14 }
                    MouseArea { anchors.fill: parent; onClicked: nova.saveVideoPrompt() }
                }
            }

            Text {
                width: parent.width
                visible: nova.videoOut !== ""
                wrapMode: Text.WordWrap
                color: "#8A7A60"
                font.pixelSize: 12
                text: "Copy and paste this into Seedance (or another tool) yourself. Nova does not send it anywhere."
            }

            Rectangle {
                width: parent.width
                height: Math.max(180, out.implicitHeight + 20)
                visible: nova.videoOut !== ""
                radius: 12
                color: "#16110F"
                border.color: "#3A2E26"
                Text {
                    id: out
                    anchors.fill: parent
                    anchors.margins: 10
                    text: nova.videoOut
                    wrapMode: Text.Wrap
                    color: "#F3E6D0"
                    font.pixelSize: 13
                    font.family: "monospace"
                }
            }
        }
    }
}
