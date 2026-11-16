import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick.Layouts 1.15

Flickable {
    id: root
    contentWidth: width
    contentHeight: col.height
    clip: true
    boundsBehavior: Flickable.StopAtBounds

    Column {
        id: col
        width: parent.width
        spacing: 14

        Text { text: "Personality"; color: "#E8C36A"; font.pixelSize: 16 }
        Text {
            width: parent.width
            wrapMode: Text.WordWrap
            color: "#C9B89A"
            font.pixelSize: 12
            text: "Presets plus a short description you can edit. Both go into Nova's local system prompt."
        }
        Flow {
            width: parent.width
            spacing: 8
            Repeater {
                model: nova.personalityPresets
                delegate: Rectangle {
                    required property var modelData
                    width: 220
                    height: 72
                    radius: 12
                    color: nova.personality === modelData.key ? "#E8C36A" : "#2A211C"
                    border.color: "#E8C36A"
                    Text {
                        anchors.fill: parent
                        anchors.margins: 8
                        wrapMode: Text.Wrap
                        text: modelData.title + "\n" + modelData.blurb
                        color: nova.personality === modelData.key ? "#1A1210" : "#F3E6D0"
                        font.pixelSize: 11
                    }
                    MouseArea { anchors.fill: parent; onClicked: nova.personality = modelData.key }
                }
            }
        }
        Text { text: "Your description (optional)"; color: "#C9B89A"; font.pixelSize: 12 }
        Rectangle {
            width: parent.width
            height: 70
            radius: 12
            color: "#2A211C"
            border.color: "#E8C36A"
            TextArea {
                anchors.fill: parent
                anchors.margins: 8
                color: "#F3E6D0"
                font.pixelSize: 13
                wrapMode: TextArea.Wrap
                background: null
                text: nova.personalityText
                onEditingFinished: nova.personalityText = text
                onTextChanged: if (activeFocus) nova.personalityText = text
                placeholderText: "e.g. Keep answers short, lots of co-op banter, call me Code…"
                placeholderTextColor: "#8A7A60"
            }
        }

        Rectangle { width: parent.width; height: 1; color: "#3A2E26" }

        Text { text: "Memory"; color: "#E8C36A"; font.pixelSize: 16 }
        Text {
            width: parent.width
            wrapMode: Text.WordWrap
            color: "#C9B89A"
            font.pixelSize: 12
            text: nova.memoryDisclaimer
        }

        Text { text: "Facts about you"; color: "#F3E6D0"; font.pixelSize: 13 }
        Repeater {
            model: nova.memoryFacts
            delegate: Row {
                required property var modelData
                spacing: 8
                width: col.width
                Rectangle {
                    width: parent.width - 80
                    height: factEdit.implicitHeight + 16
                    radius: 8
                    color: "#2A211C"
                    TextInput {
                        id: factEdit
                        anchors.fill: parent
                        anchors.margins: 8
                        color: "#F3E6D0"
                        font.pixelSize: 13
                        text: modelData.text
                        wrapMode: TextInput.Wrap
                        onEditingFinished: nova.editMemoryFact(modelData.id, text)
                    }
                }
                Rectangle {
                    width: 64
                    height: 32
                    radius: 8
                    color: "#2A211C"
                    border.color: "#8A7A60"
                    Text { anchors.centerIn: parent; text: "Delete"; color: "#F3E6D0"; font.pixelSize: 11 }
                    MouseArea { anchors.fill: parent; onClicked: nova.deleteMemoryFact(modelData.id) }
                }
            }
        }
        Text {
            visible: nova.memoryFacts.length === 0
            text: "No saved facts yet. Tell Nova your name, games you play, or things you like — or add one here."
            color: "#8A7A60"
            font.pixelSize: 12
            width: parent.width
            wrapMode: Text.WordWrap
        }
        Row {
            spacing: 8
            Rectangle {
                width: 320
                height: 36
                radius: 8
                color: "#2A211C"
                border.color: "#E8C36A"
                TextInput {
                    id: newFact
                    anchors.fill: parent
                    anchors.margins: 8
                    color: "#F3E6D0"
                    font.pixelSize: 13
                    Keys.onReturnPressed: {
                        nova.addMemoryFact(text)
                        text = ""
                    }
                    Text { anchors.fill: parent; text: "Add a fact…"; color: "#8A7A60"; visible: !newFact.text; font.pixelSize: 13 }
                }
            }
            Rectangle {
                width: 64
                height: 36
                radius: 8
                color: "#E8C36A"
                Text { anchors.centerIn: parent; text: "Add"; color: "#1A1210"; font.pixelSize: 13 }
                MouseArea {
                    anchors.fill: parent
                    onClicked: {
                        nova.addMemoryFact(newFact.text)
                        newFact.text = ""
                    }
                }
            }
        }

        Text { text: "Saved prompts"; color: "#F3E6D0"; font.pixelSize: 13 }
        Repeater {
            model: nova.savedPrompts
            delegate: Row {
                required property var modelData
                spacing: 8
                width: col.width
                Text {
                    width: parent.width - 160
                    text: modelData.title
                    elide: Text.ElideRight
                    color: "#F3E6D0"
                    font.pixelSize: 13
                    anchors.verticalCenter: parent.verticalCenter
                }
                Rectangle {
                    width: 64
                    height: 28
                    radius: 8
                    color: "#2A211C"
                    border.color: "#E8C36A"
                    Text { anchors.centerIn: parent; text: "Open"; color: "#E8C36A"; font.pixelSize: 11 }
                    MouseArea { anchors.fill: parent; onClicked: nova.loadSavedPrompt(modelData.id) }
                }
                Rectangle {
                    width: 64
                    height: 28
                    radius: 8
                    color: "#2A211C"
                    border.color: "#8A7A60"
                    Text { anchors.centerIn: parent; text: "Delete"; color: "#F3E6D0"; font.pixelSize: 11 }
                    MouseArea { anchors.fill: parent; onClicked: nova.deleteSavedPrompt(modelData.id) }
                }
            }
        }
        Text {
            visible: nova.savedPrompts.length === 0
            text: "No saved video prompts yet. Write one in Video prompt and tap Save to memory."
            color: "#8A7A60"
            font.pixelSize: 12
            width: parent.width
            wrapMode: Text.WordWrap
        }

        Rectangle {
            width: forgetLabel.implicitWidth + 28
            height: 40
            radius: 12
            color: "#3A1C1C"
            border.color: "#C07070"
            Text { id: forgetLabel; anchors.centerIn: parent; text: forgetConfirm.armed ? "Click again to forget all" : "Forget all memory"; color: "#F3E6D0"; font.pixelSize: 13 }
            MouseArea {
                id: forgetConfirm
                property bool armed: false
                anchors.fill: parent
                onClicked: {
                    if (!armed) { armed = true; return }
                    nova.forgetAllMemory()
                    armed = false
                }
            }
        }

        Rectangle { width: parent.width; height: 1; color: "#3A2E26" }

        Text { text: "Voice"; color: "#E8C36A"; font.pixelSize: 16 }
        Repeater {
            model: nova.voices
            delegate: Rectangle {
                required property var modelData
                width: col.width
                height: 56
                radius: 10
                color: nova.voice === modelData.key ? "#2E2410" : "#2A211C"
                border.color: nova.voice === modelData.key ? "#E8C36A" : "#3A2E26"
                Row {
                    anchors.fill: parent
                    anchors.margins: 10
                    spacing: 10
                    Column {
                        width: parent.width - 160
                        Text { text: modelData.title + (modelData.beta ? " — Beta / coming soon" : ""); color: "#F3E6D0"; font.pixelSize: 14 }
                        Text { text: modelData.subtitle; color: "#8A7A60"; font.pixelSize: 11; width: parent.width; wrapMode: Text.Wrap }
                    }
                    Rectangle {
                        width: 64
                        height: 28
                        radius: 8
                        color: "#2A211C"
                        border.color: "#E8C36A"
                        visible: !modelData.beta || modelData.ready
                        Text { anchors.centerIn: parent; text: "Preview"; color: "#E8C36A"; font.pixelSize: 11 }
                        MouseArea { anchors.fill: parent; onClicked: nova.previewVoice(modelData.key) }
                    }
                    Rectangle {
                        width: 72
                        height: 28
                        radius: 8
                        color: nova.voice === modelData.key ? "#E8C36A" : "#2A211C"
                        border.color: "#E8C36A"
                        Text { anchors.centerIn: parent; text: nova.voice === modelData.key ? "Selected" : "Use"; color: nova.voice === modelData.key ? "#1A1210" : "#F3E6D0"; font.pixelSize: 11 }
                        MouseArea { anchors.fill: parent; onClicked: nova.voice = modelData.key }
                    }
                }
            }
        }

        Text {
            width: parent.width
            wrapMode: Text.WordWrap
            color: "#C9B89A"
            font.pixelSize: 12
            text: nova.consentText
        }
        Row {
            spacing: 8
            Rectangle {
                width: 22
                height: 22
                radius: 4
                color: nova.myVoiceConsent ? "#E8C36A" : "#2A211C"
                border.color: "#E8C36A"
                MouseArea { anchors.fill: parent; onClicked: nova.myVoiceConsent = !nova.myVoiceConsent }
            }
            Text {
                text: "I will only record my own voice."
                color: "#F3E6D0"
                font.pixelSize: 13
                anchors.verticalCenter: parent.verticalCenter
            }
        }
        Rectangle {
            width: recLabel.implicitWidth + 28
            height: 36
            radius: 12
            color: "#2A211C"
            border.color: "#E8C36A"
            Text { id: recLabel; anchors.centerIn: parent; text: "Record a sample (Beta)"; color: "#F3E6D0"; font.pixelSize: 13 }
            MouseArea { anchors.fill: parent; onClicked: nova.recordMyVoice() }
        }

        Rectangle { width: parent.width; height: 1; color: "#3A2E26" }

        Text { text: "Bubble and shortcuts"; color: "#E8C36A"; font.pixelSize: 16 }
        Text {
            width: parent.width
            wrapMode: Text.WordWrap
            color: "#C9B89A"
            font.pixelSize: 12
            text: nova.overlayNote
        }
        Text { text: "Corner"; color: "#F3E6D0"; font.pixelSize: 13 }
        Row {
            spacing: 8
            Repeater {
                model: [
                    { id: "top-left", label: "Top left" },
                    { id: "top-right", label: "Top right" },
                    { id: "bottom-left", label: "Bottom left" },
                    { id: "bottom-right", label: "Bottom right" }
                ]
                delegate: Rectangle {
                    required property var modelData
                    width: lab.implicitWidth + 20
                    height: 32
                    radius: 8
                    color: nova.corner === modelData.id ? "#E8C36A" : "#2A211C"
                    border.color: "#E8C36A"
                    Text { id: lab; anchors.centerIn: parent; text: modelData.label; color: nova.corner === modelData.id ? "#1A1210" : "#F3E6D0"; font.pixelSize: 12 }
                    MouseArea { anchors.fill: parent; onClicked: nova.corner = modelData.id }
                }
            }
        }
        Row {
            spacing: 8
            Rectangle {
                width: 22
                height: 22
                radius: 4
                color: nova.clickThrough ? "#E8C36A" : "#2A211C"
                border.color: "#E8C36A"
                MouseArea { anchors.fill: parent; onClicked: nova.clickThrough = !nova.clickThrough }
            }
            Text {
                width: col.width - 40
                wrapMode: Text.WordWrap
                text: "Click-through bubble (clicks pass through to the game). Use the hotkey, tray, or turn this off in Settings to expand."
                color: "#F3E6D0"
                font.pixelSize: 13
            }
        }
        Text {
            width: parent.width
            wrapMode: Text.WordWrap
            color: "#C9B89A"
            font.pixelSize: 12
            text: "Push-to-talk: " + nova.ptt + "   ·   Controller combo: " + nova.padCombo
                  + (nova.hotkeyWarning !== "" ? "\n" + nova.hotkeyWarning : "")
        }
        Row {
            spacing: 8
            Rectangle {
                width: 22
                height: 22
                radius: 4
                color: nova.wakeWord ? "#E8C36A" : "#2A211C"
                border.color: "#E8C36A"
                MouseArea { anchors.fill: parent; onClicked: nova.wakeWord = !nova.wakeWord }
            }
            Text { text: "Wake word “Hey Nova” (off by default, experimental)"; color: "#F3E6D0"; font.pixelSize: 13; anchors.verticalCenter: parent.verticalCenter }
        }
        Text {
            width: parent.width
            wrapMode: Text.WordWrap
            color: "#8A7A60"
            font.pixelSize: 12
            text: nova.wakeNote
        }
        Row {
            spacing: 8
            Rectangle {
                width: 22
                height: 22
                radius: 4
                color: nova.lowPower ? "#E8C36A" : "#2A211C"
                border.color: "#E8C36A"
                MouseArea { anchors.fill: parent; onClicked: nova.lowPower = !nova.lowPower }
            }
            Text {
                width: col.width - 40
                wrapMode: Text.Wrap
                text: "Low power: smaller 1.5B model, fewer CPU threads, unload when idle. Safer on a laptop or handheld."
                color: "#F3E6D0"
                font.pixelSize: 13
            }
        }

        Text {
            width: parent.width
            wrapMode: Text.WordWrap
            color: "#8A7A60"
            font.pixelSize: 11
            text: "Nova " + nova.version + "  ·  Offline after setup  ·  Chat history and memory stay on this PC."
        }
        Rectangle {
            width: clearLabel.implicitWidth + 24
            height: 32
            radius: 8
            color: "#2A211C"
            border.color: "#8A7A60"
            Text { id: clearLabel; anchors.centerIn: parent; text: "Clear chat history"; color: "#F3E6D0"; font.pixelSize: 12 }
            MouseArea { anchors.fill: parent; onClicked: nova.clearChat() }
        }
    }
}
