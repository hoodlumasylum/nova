package com.codeaque.launcher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.json.JSONObject;

/**
 * Parent / Teacher mode on the student phone. Works at home, even when class is off.
 * The parent PIN is a salted hash on this phone only. Lessons set here stay on this phone.
 */
public final class ParentStudio {
    static final String PREFS = "nova-parent";
    static final String P_HASH = "parentPinHash";
    static final String P_SALT = "parentPinSalt";
    static final String P_LESSON = "parentLessonJson";

    private ParentStudio() {}

    static SharedPreferences prefs(MainActivity a) {
        return a.getSharedPreferences(PREFS, MainActivity.MODE_PRIVATE);
    }

    static boolean pinSet(MainActivity a) {
        String hash = prefs(a).getString(P_HASH, "");
        String salt = prefs(a).getString(P_SALT, "");
        return hash != null && salt != null && !hash.isEmpty() && !salt.isEmpty();
    }

    static void open(MainActivity a) {
        if (!pinSet(a)) {
            a.parentUnlocked = false;
            a.go(MainActivity.S_PARENT_PIN);
            return;
        }
        if (a.parentUnlocked) {
            a.parentSession = true;
            a.loadParentLesson();
            a.go(MainActivity.S_PARENT_HOME);
            return;
        }
        a.go(MainActivity.S_PARENT_PIN);
    }

    static void leave(MainActivity a) {
        a.parentSession = false;
        a.parentUnlocked = false;
        if (a.prefs.getString(MainActivity.P_SNAME, "").isEmpty()
                || a.prefs.getString(MainActivity.P_CODE, "").isEmpty()
                || a.studentPinChanged()) {
            a.go(MainActivity.S_STUDENT_JOIN);
        } else {
            a.go(a.studentAllowed() ? MainActivity.S_STUDENT_HOME : MainActivity.S_STUDENT_REST);
        }
    }

    static void showPin(MainActivity a) {
        LinearLayout col = ClassUi.col(a);
        col.addView(ClassUi.title(a, "Parent / Teacher"));
        int pinType = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        if (!pinSet(a)) {
            col.addView(ClassUi.hint(a, "Choose a parent PIN of 6 to 16 letters or numbers. Capital letters matter. Keep it private. It stays only on this phone, and it is not the student PIN or the teacher's PIN."));
            a.pinField = ClassUi.field(a, "New parent PIN", pinType);
            a.pinField2 = ClassUi.field(a, "Type the PIN again", pinType);
            col.addView(a.pinField);
            col.addView(a.pinField2);
            col.addView(ClassUi.primary(a, "Save PIN and continue", new GoClick(a, 71)));
        } else {
            col.addView(ClassUi.hint(a, "Enter the parent PIN. Capital letters matter. This unlocks parent tools on this phone, even when class is off."));
            a.pinField = ClassUi.field(a, "Parent PIN", pinType);
            a.pinField2 = null;
            col.addView(a.pinField);
            col.addView(ClassUi.primary(a, "Unlock", new GoClick(a, 71)));
        }
        col.addView(ClassUi.secondary(a, "Back", new GoClick(a, 72)));
        a.statusLine = ClassUi.hint(a, "");
        col.addView(a.statusLine);
        a.setContentView(ClassUi.screen(a, col));
    }

    static void submitPin(MainActivity a) {
        String pin = a.pinField == null ? "" : a.pinField.getText().toString().trim();
        if (!pinSet(a)) {
            String pin2 = a.pinField2 == null ? "" : a.pinField2.getText().toString().trim();
            if (!ClassCodes.pinOk(pin)) {
                a.toast("Please use 6 to 16 letters or numbers. Capital letters matter.");
                return;
            }
            if (!pin.equals(pin2)) {
                a.toast("Those PINs did not match.");
                return;
            }
            String salt = ClassCodes.newSaltHex();
            String hash = ClassCodes.saltedPinHash(pin, salt);
            prefs(a).edit().putString(P_SALT, salt).putString(P_HASH, hash).apply();
            a.parentUnlocked = true;
            a.parentSession = true;
            a.loadParentLesson();
            a.go(MainActivity.S_PARENT_HOME);
            return;
        }
        String salt = prefs(a).getString(P_SALT, "");
        String hash = prefs(a).getString(P_HASH, "");
        if (!ClassCodes.saltedPinMatches(pin, salt, hash)) {
            a.toast("That PIN is not right.");
            return;
        }
        a.parentUnlocked = true;
        a.parentSession = true;
        a.loadParentLesson();
        a.go(MainActivity.S_PARENT_HOME);
    }

    static ClassLesson.Lesson classLesson(MainActivity a) {
        if (a.gate.access != null && a.gate.access.lesson != null) return a.gate.access.lesson;
        return null;
    }

    static void showHome(MainActivity a) {
        ClassStudio.stopTimer();
        LinearLayout col = ClassUi.col(a);
        a.addUpdateBanner(col);
        col.addView(ClassUi.title(a, "Parent / Teacher"));
        col.addView(ClassUi.hint(a, "You can teach at home with Nova, even when class is off. These tools stay on this phone. The child's class tools still follow the teacher's class on/off switch."));
        col.addView(ClassUi.heading(a, "Child's progress"));
        col.addView(ClassUi.body(a, NovaProgress.parentSummary(a)));
        ClassLesson.Lesson school = classLesson(a);
        col.addView(ClassUi.heading(a, "Homework from class"));
        if (school != null && school.homework != null && !school.homework.trim().isEmpty()) {
            col.addView(ClassUi.body(a, school.homework));
        } else if (school != null && school.topic != null && !school.topic.isEmpty()) {
            col.addView(ClassUi.body(a, "The class topic is " + school.topic + ", but there is no homework sheet on this phone yet."));
        } else {
            col.addView(ClassUi.body(a, "No class homework is on this phone yet."));
        }
        col.addView(ClassUi.heading(a, "Note from the teacher"));
        if (school != null && school.parentNote != null && !school.parentNote.trim().isEmpty()) {
            col.addView(ClassUi.body(a, school.parentNote));
        } else {
            col.addView(ClassUi.body(a, "The teacher has not left a note for parents yet."));
        }
        if (a.parentLesson != null && a.parentLesson.topic != null && !a.parentLesson.topic.isEmpty()) {
            col.addView(ClassUi.heading(a, "Homework set at home"));
            String hw = a.parentLesson.homework;
            if (hw == null || hw.trim().isEmpty()) hw = "Topic: " + a.parentLesson.topic;
            col.addView(ClassUi.body(a, hw));
        }
        col.addView(ClassUi.primary(a, "Share progress report", new GoClick(a, 74)));
        col.addView(ClassUi.primary(a, "Teach a topic at home", new GoClick(a, 73)));
        col.addView(ClassUi.secondary(a, "Back", new GoClick(a, 72)));
        col.addView(ClassUi.paidHint(a));
        a.setContentView(ClassUi.screen(a, col));
    }

    static void showTeach(MainActivity a) {
        a.parentSession = true;
        LinearLayout col = ClassUi.col(a);
        col.addView(ClassUi.title(a, "Teach at home"));
        col.addView(ClassUi.hint(a, "Type or say a topic. Nova will make a short lesson for you to use with your child: an explanation, flashcards, a quiz, steps, and homework. This stays on this phone."));
        a.topicField = ClassUi.field(a, "Topic, for example: the water cycle", android.text.InputType.TYPE_CLASS_TEXT);
        col.addView(a.topicField);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.rightMargin = ClassUi.dp(a, 8);
        android.widget.Button bSpeak = ClassUi.secondary(a, "Say the topic", new ButtonSpeakTopic(a));
        bSpeak.setLayoutParams(lp);
        android.widget.Button bGo = ClassUi.primary(a, "Teach this", new GoClick(a, 75));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        bGo.setLayoutParams(lp2);
        row.addView(bSpeak);
        row.addView(bGo);
        col.addView(row);
        a.statusLine = ClassUi.hint(a, a.busy ? "Please wait…" : "");
        col.addView(a.statusLine);
        ClassLesson.Lesson lesson = a.parentLesson;
        if (lesson != null && lesson.explain != null && !lesson.explain.isEmpty()) {
            col.addView(ClassUi.heading(a, lesson.topic == null || lesson.topic.isEmpty() ? "Home topic" : lesson.topic));
            col.addView(ClassUi.body(a, lesson.explain));
            col.addView(ClassUi.primary(a, "Flashcards", new GoClick(a, 59)));
            col.addView(ClassUi.primary(a, "Quick-fire quiz", new GoClick(a, 60)));
            col.addView(ClassUi.secondary(a, "Step-by-step lesson", new GoClick(a, 61)));
            col.addView(ClassUi.secondary(a, "Share homework", new GoClick(a, 64)));
        }
        col.addView(ClassUi.primary(a, "Back", new GoClick(a, 76)));
        a.setContentView(ClassUi.screen(a, col));
    }

    static void startLesson(MainActivity a) {
        String topic = a.topicField == null ? "" : a.topicField.getText().toString().trim();
        if (topic.isEmpty()) {
            a.toast("Please type or say a topic first.");
            return;
        }
        a.setBusy(true);
        if (a.statusLine != null) a.statusLine.setText("Nova is preparing the lesson…");
        new Thread(new ParentLessonJob(a, topic), "nova-parent-lesson").start();
    }

    static void onLessonReady(MainActivity a, ClassLesson.Lesson lesson, String error) {
        a.setBusy(false);
        if (lesson == null) {
            a.toast(error != null ? error : "I could not make a lesson from that topic.");
            return;
        }
        a.parentLesson = lesson;
        a.saveParentLesson();
        a.parentSession = true;
        try {
            a.ensureNovaQuiet();
            if (a.nova != null) a.nova.speak(lesson.explain);
        } catch (Exception ignored) { }
        a.toast("The home lesson is ready. It stays on this phone.");
        a.go(MainActivity.S_PARENT_TEACH);
    }

    static String reportText(MainActivity a) {
        StringBuilder b = new StringBuilder();
        b.append("Nova progress report\n\n");
        String name = a.prefs.getString(MainActivity.P_SNAME, "");
        if (name != null && !name.isEmpty()) b.append("Child: ").append(name).append("\n\n");
        b.append(NovaProgress.parentSummary(a)).append("\n\n");
        ClassLesson.Lesson school = classLesson(a);
        b.append("Homework from class:\n");
        if (school != null && school.homework != null && !school.homework.trim().isEmpty()) {
            b.append(school.homework).append("\n\n");
        } else {
            b.append("None on this phone yet.\n\n");
        }
        b.append("Note from the teacher:\n");
        if (school != null && school.parentNote != null && !school.parentNote.trim().isEmpty()) {
            b.append(school.parentNote).append("\n\n");
        } else {
            b.append("None on this phone yet.\n\n");
        }
        if (a.parentLesson != null && a.parentLesson.homework != null
                && !a.parentLesson.homework.trim().isEmpty()) {
            b.append("Homework set at home:\n").append(a.parentLesson.homework).append("\n\n");
        }
        b.append("This report was made on this phone. It was not uploaded.");
        return b.toString();
    }

    static void shareReport(MainActivity a) {
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "Nova progress report");
            send.putExtra(Intent.EXTRA_TEXT, reportText(a));
            a.startActivity(Intent.createChooser(send, "Share progress report"));
        } catch (Exception e) {
            a.toast("I could not open the Android share sheet.");
        }
    }

    static void addButton(LinearLayout col, MainActivity a) {
        col.addView(ClassUi.secondary(a, "Parent / Teacher", new GoClick(a, 70)));
    }
}

final class ParentLessonJob implements Runnable {
    final MainActivity a;
    final String topic;
    ParentLessonJob(MainActivity a, String topic) { this.a = a; this.topic = topic; }
    @Override public void run() {
        ClassLesson.Lesson lesson = null;
        String err = null;
        try { lesson = ClassLesson.generate(topic); }
        catch (Exception e) { err = "The lesson could not be prepared."; }
        a.runOnUiThread(new ParentLessonDone(a, lesson, err));
    }
}

final class ParentLessonDone implements Runnable {
    final MainActivity a;
    final ClassLesson.Lesson lesson;
    final String error;
    ParentLessonDone(MainActivity a, ClassLesson.Lesson lesson, String error) {
        this.a = a; this.lesson = lesson; this.error = error;
    }
    @Override public void run() { ParentStudio.onLessonReady(a, lesson, error); }
}
