import QtQuick 2.15
import QtQuick.Controls 2.15

Flickable {
    anchors.fill: parent
    contentWidth: width
    contentHeight: col.height
    clip: true

    Column {
        id: col
        width: parent.width
        spacing: 12

        Text { text: "Setup and updates"; color: "#E8C36A"; font.pixelSize: 16 }
        Text {
            width: parent.width
            wrapMode: Text.WordWrap
            color: "#C9B89A"
            font.pixelSize: 13
            text: "Nova downloads official builds of Ollama, Piper, whisper.cpp, the CodeAque voice, and a local model into ~/.local/share/nova/. Nothing is bundled in the app folder. After this, she stays offline — no accounts or keys. This can take a while and uses several GB of disk."
        }

        Repeater {
            model: nova.preflightNotes
            delegate: Text {
                required property var modelData
                width: col.width
                wrapMode: Text.WordWrap
                color: "#F3E6D0"
                font.pixelSize: 13
                text: "• " + modelData
            }
        }

        Text {
            visible: nova.setupNeeded
            width: parent.width
            wrapMode: Text.WordWrap
            color: "#E8C36A"
            font.pixelSize: 13
            text: "Still needed: " + nova.setupItems.join(", ")
        }
        Text {
            visible: !nova.setupNeeded
            width: parent.width
            wrapMode: Text.WordWrap
            color: "#A0C090"
            font.pixelSize: 13
            text: "Everything required is already on this PC. You can still run Setup to update."
        }

        Rectangle {
            width: parent.width
            height: 16
            radius: 8
            color: "#2A211C"
            Rectangle {
                width: parent.width * Math.max(0, Math.min(1, nova.setupFraction))
                height: parent.height
                radius: 8
                color: "#E8C36A"
            }
        }

        Text {
            width: parent.width
            wrapMode: Text.WordWrap
            color: "#C9B89A"
            font.pixelSize: 13
            text: nova.status
        }

        Rectangle {
            width: goLabel.implicitWidth + 32
            height: 44
            radius: 12
            color: nova.busy ? "#5A4A3A" : "#E8C36A"
            Text { id: goLabel; anchors.centerIn: parent; text: nova.busy ? "Working…" : (nova.setupNeeded ? "Install Nova" : "Check for updates"); color: "#1A1210"; font.pixelSize: 15 }
            MouseArea { anchors.fill: parent; enabled: !nova.busy; onClicked: nova.runSetup() }
        }

        Text {
            width: parent.width
            wrapMode: Text.Wrap
            color: "#8A7A60"
            font.pixelSize: 11
            font.family: "monospace"
            text: nova.setupLog
        }
    }
}
