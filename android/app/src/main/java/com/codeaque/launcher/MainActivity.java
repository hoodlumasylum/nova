package com.codeaque.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Standalone Nova for class: teacher controls, student one-to-one helper,
 * and Class mode (topic tools for teachers and students).
 */
public class MainActivity extends Activity implements NovaView.Host {
    static final int REQ_MIC = 64;
    static final int REQ_SPEECH = 65;
    static final int REQ_TOPIC_SPEECH = 66;
    static final int REQ_START_CODE = 67;

    static final int S_ROLE = 0;
    static final int S_TEACHER_PIN = 1;
    static final int S_TEACHER_HOME = 2;
    static final int S_TEACHER_STUDENTS = 3;
    static final int S_TEACHER_CLASS = 4;
    static final int S_TEACHER_NOVA = 5;
    static final int S_STUDENT_JOIN = 6;
    static final int S_STUDENT_REST = 7;
    static final int S_STUDENT_NOVA = 8;
    static final int S_STUDENT_HOME = 9;
    static final int S_TEACHER_CARDS = 40;
    static final int S_TEACHER_QUICK = 41;
    static final int S_TEACHER_STEPS = 42;
    static final int S_TODAY = 43;
    static final int S_STUDENT_CARDS = 44;
    static final int S_SELF_QUIZ = 45;
    static final int S_PROGRESS = 46;

    static final String PREFS = "nova-class";
    static final String P_ROLE = "role";
    static final String P_PIN = "pinHash";
    static final String P_PIN_SALT = "teacherPinSalt";
    static final String P_CODE = "classCode";
    static final String P_STUDENT_PIN = "studentPin";
    static final String P_JOIN_HASH = "joinedStudentPinHash";
    static final String P_SID = "studentId";
    static final String P_SNAME = "studentName";
    static final String P_CACHE = "accessCache";
    static final String P_FETCH = "lastFetchAt";
    static final String P_CODE_UNTIL = "codeUntil";
    static final int POLL_MS = 15_000;

    SharedPreferences prefs;
    final ClassGate.Holder gate = new ClassGate.Holder();
    final Handler handler = new Handler();
    NovaView nova;
    int screen = S_ROLE;
    boolean busy;
    EditText pinField, pinField2, codeField, nameField, topicField;
    TextView statusLine;
    ClassLesson.Lesson draftLesson;
    String pendingAsk;
    final Runnable poll = new PollTick(this);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(ClassUi.BG);
            getWindow().setNavigationBarColor(ClassUi.BG);
        }
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadLocal();
        routeFromPrefs();
        show();
        ClassGate.pullControl(gate, new AfterPull(this, false));
        NovaUpdateGo.handle(this, getIntent());
        NovaUpdateGo.check(this);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        NovaUpdateGo.handle(this, intent);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(poll);
        handler.postDelayed(poll, 400);
        NovaUpdateGo.check(this);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(poll);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(poll);
        if (nova != null) {
            try { nova.release(); } catch (Exception ignored) { }
            nova = null;
        }
        super.onDestroy();
    }

    void loadLocal() {
        String cache = prefs.getString(P_CACHE, "");
        if (!cache.isEmpty()) gate.access = ClassGate.fromJson(cache);
        gate.lastFetchAt = prefs.getLong(P_FETCH, 0);
        gate.codeUntil = prefs.getLong(P_CODE_UNTIL, 0);
        if (gate.access != null) {
            gate.access.pinHash = "";
        }
    }

    void saveCache() {
        prefs.edit()
                .putString(P_CACHE, ClassGate.toJson(gate.access))
                .putLong(P_FETCH, gate.lastFetchAt)
                .putLong(P_CODE_UNTIL, gate.codeUntil)
                .apply();
    }

    void routeFromPrefs() {
        String role = prefs.getString(P_ROLE, "");
        if ("teacher".equals(role)) {
            if (prefs.getString(P_PIN, "").isEmpty()) screen = S_TEACHER_PIN;
            else screen = S_TEACHER_NOVA;
        } else if ("student".equals(role)) {
            if (prefs.getString(P_SNAME, "").isEmpty() || prefs.getString(P_CODE, "").isEmpty()
                    || studentPinChanged()) {
                screen = S_STUDENT_JOIN;
            } else {
                screen = studentAllowed() ? S_STUDENT_HOME : S_STUDENT_REST;
            }
        } else {
            screen = S_ROLE;
        }
    }

    boolean isTeacher() { return "teacher".equals(prefs.getString(P_ROLE, "")); }

    String studentId() {
        String id = prefs.getString(P_SID, "");
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(P_SID, id).apply();
        }
        return id;
    }

    boolean studentAllowed() {
        long now = System.currentTimeMillis();
        return ClassGate.allowed(gate.access, studentId(), now, gate.codeUntil, gate.lastFetchAt);
    }

    void show() {
        if (screen == S_TEACHER_NOVA) { showNova(true); return; }
        if (screen == S_STUDENT_NOVA) { showNova(false); return; }
        if (screen == S_STUDENT_REST) { showResting(); return; }
        if (screen == S_TEACHER_HOME) { showTeacherHome(); return; }
        if (screen == S_TEACHER_PIN) { showTeacherPin(); return; }
        if (screen == S_TEACHER_STUDENTS) { showStudents(); return; }
        if (screen == S_TEACHER_CLASS) { showClassMode(); return; }
        if (screen == S_STUDENT_JOIN) { showStudentJoin(); return; }
        if (screen == S_STUDENT_HOME) { ClassStudio.showStudentHome(this); return; }
        if (screen == S_TEACHER_CARDS) { ClassStudio.showCards(this, true); return; }
        if (screen == S_TEACHER_QUICK) { ClassStudio.showQuick(this, true); return; }
        if (screen == S_TEACHER_STEPS) { ClassStudio.showSteps(this, true); return; }
        if (screen == S_TODAY) { ClassStudio.showToday(this); return; }
        if (screen == S_STUDENT_CARDS) { ClassStudio.showCards(this, false); return; }
        if (screen == S_SELF_QUIZ) { ClassStudio.showQuick(this, false); return; }
        if (screen == S_PROGRESS) { ClassStudio.showProgress(this); return; }
        showRole();
    }

    void go(int next) {
        if (next != S_TEACHER_QUICK) ClassStudio.stopTimer();
        screen = next;
        show();
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    void setBusy(boolean on) {
        busy = on;
        if (statusLine != null) {
            if (on) statusLine.setText("Please wait…");
        }
    }

    void showRole() {
        LinearLayout col = ClassUi.col(this);
        addUpdateBanner(col);
        col.addView(ClassUi.title(this, "Nova"));
        col.addView(ClassUi.hint(this, "A classroom helper. Students use Nova on their own phones. The teacher decides when she is awake."));
        col.addView(ClassUi.primary(this, "I am the teacher", new GoClick(this, 1)));
        col.addView(ClassUi.secondary(this, "I am a student", new GoClick(this, 2)));
        setContentView(ClassUi.screen(this, col));
    }

    void showTeacherPin() {
        LinearLayout col = ClassUi.col(this);
        boolean first = prefs.getString(P_PIN, "").isEmpty();
        col.addView(ClassUi.title(this, "Teacher"));
        int pinType = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        if (first) {
            col.addView(ClassUi.hint(this, "Choose a teacher PIN of 6 to 16 letters or numbers. Capital letters matter. Keep it private. It stays only on this phone."));
            pinField = ClassUi.field(this, "New teacher PIN", pinType);
            pinField2 = ClassUi.field(this, "Type the PIN again", pinType);
            col.addView(pinField);
            col.addView(pinField2);
            col.addView(ClassUi.primary(this, "Save PIN and continue", new GoClick(this, 3)));
        } else {
            col.addView(ClassUi.hint(this, "Enter your teacher PIN. Capital letters matter."));
            pinField = ClassUi.field(this, "Teacher PIN", pinType);
            pinField2 = null;
            col.addView(pinField);
            col.addView(ClassUi.primary(this, "Unlock", new GoClick(this, 3)));
        }
        col.addView(ClassUi.secondary(this, "Back", new GoClick(this, 4)));
        statusLine = ClassUi.hint(this, "");
        col.addView(statusLine);
        setContentView(ClassUi.screen(this, col));
    }

    void showTeacherHome() {
        LinearLayout col = ClassUi.col(this);
        addUpdateBanner(col);
        col.addView(ClassUi.title(this, "Teacher / Class tools"));
        col.addView(ClassUi.hint(this, "Class on/off, the student PIN, and lesson tools are here. Just Nova is your personal assistant and does not use the class lock."));
        if (needsStudentPinPublish()) ensureStudentPin(true, S_TEACHER_HOME);
        String pin = displayStudentPin();
        col.addView(ClassUi.hint(this, "Give this student PIN to your class. They type it to join:"));
        col.addView(ClassUi.codeView(this, pin.isEmpty() ? "——" : pin));
        col.addView(ClassUi.secondary(this, "New student PIN", new GoClick(this, 62)));
        boolean on = gate.access != null && gate.access.classActive(System.currentTimeMillis());
        col.addView(ClassUi.banner(this, on ? "Class is ON" : "Class is OFF", on));
        boolean homeAll = gate.access != null && gate.access.homeAllActive(System.currentTimeMillis());
        col.addView(ClassUi.hint(this, homeAll
                ? "Home use is on for the whole class."
                : "Home use is off, unless you have allowed it for a student."));
        if (!gate.online && gate.lastFetchAt > 0) {
            col.addView(ClassUi.hint(this, "Could not reach the class switch just now. You can still start class with a start code."));
        }
        statusLine = ClassUi.hint(this, statusText());
        col.addView(statusLine);
        if (on) col.addView(ClassUi.danger(this, "End class", new GoClick(this, 10)));
        else col.addView(ClassUi.primary(this, "Start class", new GoClick(this, 11)));
        col.addView(ClassUi.secondary(this, homeAll ? "Turn off home use for everyone" : "Allow home use for everyone", new GoClick(this, 12)));
        col.addView(ClassUi.primary(this, "Class mode (topic and tools)", new GoClick(this, 13)));
        col.addView(ClassUi.primary(this, "Just Nova", new GoClick(this, 14)));
        col.addView(ClassUi.secondary(this, "Students", new GoClick(this, 15)));
        col.addView(ClassUi.secondary(this, "Show a start code", new GoClick(this, 16)));
        setContentView(ClassUi.screen(this, col));
    }

    void showStudents() {
        LinearLayout col = ClassUi.col(this);
        col.addView(ClassUi.title(this, "Students"));
        col.addView(ClassUi.hint(this, "Turn home use on or off for one student. They appear here after they join with the student PIN."));
        statusLine = ClassUi.hint(this, statusText());
        col.addView(statusLine);
        ArrayList<ClassStudent> list = gate.roster;
        if (list == null || list.isEmpty()) {
            col.addView(ClassUi.body(this, "No students have joined yet."));
        } else {
            for (int i = 0; i < list.size(); i++) {
                final ClassStudent s = list.get(i);
                boolean allowed = gate.access != null && gate.access.homeAllowedFor(s.id, System.currentTimeMillis());
                String label = (s.name == null || s.name.isEmpty() ? "Student" : s.name)
                        + (allowed ? "  ·  home use on" : "  ·  home use off");
                col.addView(ClassUi.secondary(this, label, new StudentHomeClick(this, s.id, !allowed)));
            }
        }
        col.addView(ClassUi.secondary(this, "Refresh list", new GoClick(this, 17)));
        col.addView(ClassUi.primary(this, "Back", new GoClick(this, 18)));
        setContentView(ClassUi.screen(this, col));
    }

    void showClassMode() {
        LinearLayout col = ClassUi.col(this);
        col.addView(ClassUi.title(this, "Class mode"));
        col.addView(ClassUi.hint(this, "Type or say a topic. Nova will make a short lesson: an explanation, flashcards, a quiz, steps, and a homework sheet."));
        topicField = ClassUi.field(this, "Topic, for example: the water cycle", InputType.TYPE_CLASS_TEXT);
        col.addView(topicField);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.rightMargin = ClassUi.dp(this, 8);
        ButtonSpeakTopic speak = new ButtonSpeakTopic(this);
        android.widget.Button bSpeak = ClassUi.secondary(this, "Say the topic", speak);
        bSpeak.setLayoutParams(lp);
        android.widget.Button bGo = ClassUi.primary(this, "Teach this", new GoClick(this, 20));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        bGo.setLayoutParams(lp2);
        row.addView(bSpeak);
        row.addView(bGo);
        col.addView(row);
        statusLine = ClassUi.hint(this, busy ? "Please wait…" : statusText());
        col.addView(statusLine);
        ClassLesson.Lesson lesson = draftLesson != null ? draftLesson
                : (gate.access != null ? gate.access.lesson : null);
        if (lesson != null && lesson.explain != null && !lesson.explain.isEmpty()) {
            col.addView(ClassUi.heading(this, lesson.topic == null || lesson.topic.isEmpty() ? "Today's topic" : lesson.topic));
            col.addView(ClassUi.body(this, lesson.explain));
            int showQ = Math.min(3, lesson.questions.size());
            for (int i = 0; i < showQ; i++) {
                ClassLesson.Item it = lesson.questions.get(i);
                col.addView(ClassUi.heading(this, "Question " + (i + 1)));
                col.addView(ClassUi.body(this, it.q + (it.a == null || it.a.isEmpty() ? "" : "\nAnswer: " + it.a)));
            }
            if (lesson.questions.size() > 3) {
                col.addView(ClassUi.hint(this, "The quick-fire quiz has " + lesson.questions.size() + " questions."));
            }
            col.addView(ClassUi.primary(this, "Flashcards", new GoClick(this, 59)));
            col.addView(ClassUi.primary(this, "Quick-fire quiz", new GoClick(this, 60)));
            col.addView(ClassUi.secondary(this, "Step-by-step lesson", new GoClick(this, 61)));
            col.addView(ClassUi.secondary(this, "Share homework", new GoClick(this, 64)));
        }
        col.addView(ClassUi.secondary(this, "Back", new GoClick(this, 18)));
        setContentView(ClassUi.screen(this, col));
    }

    void showStudentJoin() {
        LinearLayout col = ClassUi.col(this);
        col.addView(ClassUi.title(this, "Join class"));
        addUpdateBanner(col);
        if (studentPinChanged()) {
            col.addView(ClassUi.hint(this, "The class PIN has changed. Ask your teacher for the new student PIN."));
        } else {
            col.addView(ClassUi.hint(this, "Type the student PIN your teacher showed you, and your first name."));
        }
        codeField = ClassUi.field(this, "Student PIN", InputType.TYPE_CLASS_NUMBER);
        nameField = ClassUi.field(this, "Your first name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        String existingCode = prefs.getString(P_CODE, "");
        String existingName = prefs.getString(P_SNAME, "");
        if (!existingCode.isEmpty()) codeField.setText(existingCode);
        if (!existingName.isEmpty()) nameField.setText(existingName);
        col.addView(codeField);
        col.addView(nameField);
        col.addView(ClassUi.primary(this, "Join", new GoClick(this, 30)));
        col.addView(ClassUi.secondary(this, "I have a start or home code", new GoClick(this, 31)));
        col.addView(ClassUi.secondary(this, "Back", new GoClick(this, 4)));
        statusLine = ClassUi.hint(this, "");
        col.addView(statusLine);
        setContentView(ClassUi.screen(this, col));
    }

    void showResting() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ClassUi.BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        int p = ClassUi.dp(this, 28);
        col.setPadding(p, p, p, p);
        ImageView face = new ImageView(this);
        Bitmap bm = faceBitmap();
        if (bm != null) face.setImageBitmap(bm);
        face.setAlpha(0.55f);
        face.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size = ClassUi.dp(this, 240);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(size, size);
        flp.gravity = Gravity.CENTER_HORIZONTAL;
        flp.bottomMargin = ClassUi.dp(this, 20);
        face.setLayoutParams(flp);
        col.addView(face);
        TextView msg = ClassUi.title(this, "Nova is resting until class");
        msg.setGravity(Gravity.CENTER);
        msg.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        col.addView(msg);
        col.addView(ClassUi.hint(this, "Your teacher will turn Nova on when class starts. If they have allowed home use, she will wake up for you."));
        col.addView(ClassUi.secondary(this, "I have a start or home code", new GoClick(this, 31)));
        statusLine = ClassUi.hint(this, statusText());
        col.addView(statusLine);
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        quietNova();
    }

    void showNova(boolean teacher) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ClassUi.BG);
        if (nova == null) {
            SharedPreferences np = getSharedPreferences("nova", MODE_PRIVATE);
            np.edit().putBoolean(NovaView.PREF_BIG, true)
                    .putBoolean(NovaView.PREF_HIDDEN, false)
                    .putBoolean(NovaView.PREF_VOICE_TALK, true)
                    .putBoolean(NovaView.PREF_SMARTER, true)
                    .apply();
            nova = new NovaView(this, np, false);
            nova.setHost(this);
            nova.setKids(true);
            new Thread(new NovaEngineWarmup(this), "nova-warmup").start();
        } else if (nova.getParent() instanceof ViewGroup) {
            ((ViewGroup) nova.getParent()).removeView(nova);
        }
        root.addView(nova, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        nova.post(new OpenNovaBig(this, nova));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xEE0E1218);
        int pad = ClassUi.dp(this, 8);
        bar.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        bp.rightMargin = ClassUi.dp(this, 6);
        android.widget.Button back = teacher
                ? ClassUi.primary(this, "Teacher / Class tools", new GoClick(this, 18))
                : ClassUi.secondary(this, "Class tools", new GoClick(this, 45));
        back.setLayoutParams(bp);
        bar.addView(back);
        if (teacher && NovaUpdateGo.pending() != null) {
            android.widget.Button up = ClassUi.primary(this, "Update ready", new GoClick(this, 63));
            LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            up.setLayoutParams(bp2);
            bar.addView(up);
        } else if (!teacher && gate.access != null && gate.access.lesson != null
                && gate.access.lesson.explain != null && !gate.access.lesson.explain.isEmpty()) {
            android.widget.Button q = ClassUi.primary(this, "Today's topic", new GoClick(this, 40));
            LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            q.setLayoutParams(bp2);
            bar.addView(q);
        }
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.BOTTOM;
        root.addView(bar, blp);
        setContentView(root);
    }

    void showStudentQuiz() {
        ClassLesson.Lesson lesson = gate.access != null ? gate.access.lesson : null;
        LinearLayout col = ClassUi.col(this);
        col.addView(ClassUi.title(this, lesson != null && lesson.topic != null && !lesson.topic.isEmpty()
                ? lesson.topic : "Today's quiz"));
        if (lesson == null || lesson.questions.isEmpty()) {
            col.addView(ClassUi.hint(this, "No quiz yet. Your teacher can start one in Class mode."));
        } else {
            col.addView(ClassUi.body(this, lesson.explain == null ? "" : lesson.explain));
            for (int i = 0; i < lesson.questions.size(); i++) {
                ClassLesson.Item it = lesson.questions.get(i);
                col.addView(ClassUi.heading(this, "Question " + (i + 1)));
                col.addView(ClassUi.body(this, it.q));
            }
            col.addView(ClassUi.hint(this, "Ask Nova if you want help. She will not show the answers unless your teacher does."));
        }
        col.addView(ClassUi.primary(this, "Back to Nova", new GoClick(this, 14)));
        setContentView(ClassUi.screen(this, col));
    }

    Bitmap faceBitmap() {
        try {
            InputStream in = getAssets().open("nova-face-cutout.png");
            Bitmap bm = BitmapFactory.decodeStream(in);
            in.close();
            return bm;
        } catch (Exception e) {
            return null;
        }
    }

    void quietNova() {
        if (nova == null) return;
        try { nova.stopListen(); } catch (Exception ignored) { }
        try { nova.stopSpeak(); } catch (Exception ignored) { }
        try { nova.setExpanded(false); } catch (Exception ignored) { }
    }

    String displayCode() {
        return displayStudentPin();
    }

    String displayStudentPin() {
        if (isTeacher()) return prefs.getString(P_STUDENT_PIN, "");
        return prefs.getString(P_CODE, "");
    }

    boolean studentPinChanged() {
        String joined = prefs.getString(P_JOIN_HASH, "");
        String published = "";
        if (gate.access != null && gate.access.studentPinHash != null) published = gate.access.studentPinHash;
        return !joined.isEmpty() && !published.isEmpty() && !joined.equalsIgnoreCase(published);
    }

    boolean teacherPinMatches(String pin) {
        String salt = prefs.getString(P_PIN_SALT, "");
        String hash = prefs.getString(P_PIN, "");
        if (!salt.isEmpty() && !hash.isEmpty()) return ClassCodes.saltedPinMatches(pin, salt, hash);
        return ClassCodes.legacyUnsaltedMatches(pin, hash);
    }

    void storeTeacherPin(String pin) {
        String salt = ClassCodes.newSaltHex();
        String hash = ClassCodes.saltedPinHash(pin, salt);
        prefs.edit().putString(P_PIN_SALT, salt).putString(P_PIN, hash).apply();
    }

    boolean needsStudentPinPublish() {
        String pin = prefs.getString(P_STUDENT_PIN, "");
        boolean missingHash = gate.access == null
                || gate.access.studentPinHash == null
                || gate.access.studentPinHash.isEmpty();
        return !ClassCodes.studentPinOk(pin) || missingHash;
    }

    void ensureStudentPin(boolean publishIfNew) {
        ensureStudentPin(publishIfNew, S_TEACHER_HOME);
    }

    void ensureStudentPin(boolean publishIfNew, int next) {
        String pin = prefs.getString(P_STUDENT_PIN, "");
        boolean fresh = !ClassCodes.studentPinOk(pin);
        if (fresh) {
            pin = ClassCodes.randomStudentPin();
            prefs.edit().putString(P_STUDENT_PIN, pin).apply();
        }
        boolean missingHash = gate.access == null
                || gate.access.studentPinHash == null
                || gate.access.studentPinHash.isEmpty();
        if (fresh || (publishIfNew && missingHash)) publishStudentPin(pin);
        if ((fresh || (publishIfNew && missingHash)) && isTeacher()) {
            setBusy(true);
            ClassGate.pushControl(gate, new AfterPush(this, "A student PIN is ready to share.", next));
        }
    }

    void publishStudentPin(String pin) {
        String salt = ClassCodes.newSaltHex();
        String hash = ClassCodes.saltedPinHash(pin, salt);
        if (gate.access == null) gate.access = new ClassAccess();
        gate.access.studentPinSalt = salt;
        gate.access.studentPinHash = hash;
        gate.access.pinHash = "";
        gate.access.classCode = "";
    }

    void confirmNewStudentPin() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("New student PIN")
                .setMessage("This will lock out the old student PIN. Students will need the new one, and old start or home codes will stop working.")
                .setPositiveButton("Make a new PIN", new NewStudentPinOk(this))
                .setNegativeButton("Cancel", null)
                .show();
    }

    void regenerateStudentPin() {
        String pin = ClassCodes.randomStudentPin();
        prefs.edit().putString(P_STUDENT_PIN, pin).apply();
        publishStudentPin(pin);
        setBusy(true);
        ClassGate.pushControl(gate, new AfterPush(this,
                "New student PIN: " + pin + ". The old one no longer works.", S_TEACHER_HOME));
    }

    void addUpdateBanner(LinearLayout col) {
        NovaUpdate.Offer o = NovaUpdateGo.pending();
        if (o == null) return;
        String name = o.versionName == null || o.versionName.isEmpty() ? o.tag : o.versionName;
        col.addView(ClassUi.banner(this, "Update ready", true));
        col.addView(ClassUi.hint(this, "Nova " + name + " is ready. Tap to download and install. Your class stays on this phone."));
        col.addView(ClassUi.primary(this, "Update now", new GoClick(this, 63)));
    }

    void ensureNovaQuiet() {
        if (nova != null) return;
        try {
            android.content.SharedPreferences np = getSharedPreferences("nova", MODE_PRIVATE);
            nova = new NovaView(this, np, false);
            nova.setHost(this);
            nova.setKids(true);
        } catch (Exception ignored) { }
    }

    String statusText() {
        if (busy) return "Please wait…";
        if (gate.online) return "Class switch is connected.";
        if (gate.lastError != null && !gate.lastError.isEmpty()) {
            return "Class switch is offline. " + gate.lastError;
        }
        return "";
    }

    void pullTick() {
        ClassGate.pullControl(gate, new AfterPull(this, false));
        if (isTeacher() && screen == S_TEACHER_STUDENTS) {
            ClassGate.pullRoster(gate, new AfterRoster(this, false));
        }
        if ("student".equals(prefs.getString(P_ROLE, "")) && !prefs.getString(P_SNAME, "").isEmpty()) {
            ClassGate.checkIn(gate, studentId(), prefs.getString(P_SNAME, ""), null);
        }
    }

    void onGo(int action) {
        if (busy && action != 4 && action != 18) return;
        switch (action) {
            case 1:
                prefs.edit().putString(P_ROLE, "teacher").apply();
                go(S_TEACHER_PIN);
                break;
            case 2:
                prefs.edit().putString(P_ROLE, "student").apply();
                go(S_STUDENT_JOIN);
                break;
            case 3:
                submitTeacherPin();
                break;
            case 4:
                prefs.edit().remove(P_ROLE).apply();
                go(S_ROLE);
                break;
            case 10:
                endClass();
                break;
            case 11:
                startClass();
                break;
            case 12:
                toggleHomeAll();
                break;
            case 13:
                go(S_TEACHER_CLASS);
                break;
            case 14:
                go(isTeacher() ? S_TEACHER_NOVA : S_STUDENT_NOVA);
                break;
            case 15:
                go(S_TEACHER_STUDENTS);
                ClassGate.pullRoster(gate, new AfterRoster(this, true));
                break;
            case 16:
                showStartCodeDialog();
                break;
            case 17:
                ClassGate.pullRoster(gate, new AfterRoster(this, true));
                toast("Refreshing…");
                break;
            case 18:
                go(isTeacher() ? S_TEACHER_HOME : (studentAllowed() ? S_STUDENT_HOME : S_STUDENT_REST));
                break;
            case 20:
                startLesson();
                break;
            case 30:
                submitStudentJoin();
                break;
            case 31:
                askStartCode();
                break;
            case 32:
                go(studentAllowed() ? S_STUDENT_HOME : S_STUDENT_REST);
                break;
            case 33:
                ClassStudio.beginQuick(this, false);
                break;
            case 40:
                go(S_TODAY);
                break;
            case 41:
                ClassStudio.beginCards(this);
                break;
            case 42:
                ClassStudio.beginQuick(this, false);
                break;
            case 43:
                ClassStudio.askAboutLesson(this);
                break;
            case 44:
                go(S_PROGRESS);
                break;
            case 45:
                go(isTeacher() ? S_TEACHER_CLASS : (studentAllowed() ? S_STUDENT_HOME : S_STUDENT_REST));
                break;
            case 50:
                ClassStudio.flipCard(this);
                break;
            case 51:
                ClassStudio.nextCard(this);
                break;
            case 52:
                ClassStudio.prevCard(this);
                break;
            case 54:
                ClassStudio.nextStep(this);
                break;
            case 55:
                ClassStudio.prevStep(this);
                break;
            case 56:
                ClassStudio.mark(this, true);
                break;
            case 57:
                ClassStudio.mark(this, false);
                break;
            case 58:
                ClassStudio.reveal(this);
                break;
            case 59:
                ClassStudio.beginCards(this);
                break;
            case 60:
                ClassStudio.beginQuick(this, true);
                break;
            case 61:
                ClassStudio.beginSteps(this);
                break;
            case 62:
                confirmNewStudentPin();
                break;
            case 63:
                NovaUpdateGo.startInstall(this);
                break;
            case 64:
                ClassStudio.shareHomework(this);
                break;
            default:
                break;
        }
    }

    void submitTeacherPin() {
        String pin = pinField == null ? "" : pinField.getText().toString().trim();
        boolean first = prefs.getString(P_PIN, "").isEmpty();
        if (first) {
            String pin2 = pinField2 == null ? "" : pinField2.getText().toString().trim();
            if (!ClassCodes.pinOk(pin)) {
                toast("Please use 6 to 16 letters or numbers. Capital letters matter.");
                return;
            }
            if (!pin.equals(pin2)) {
                toast("Those PINs did not match.");
                return;
            }
            storeTeacherPin(pin);
            String studentPin = ClassCodes.randomStudentPin();
            prefs.edit().putString(P_ROLE, "teacher").putString(P_STUDENT_PIN, studentPin).apply();
            publishStudentPin(studentPin);
            setBusy(true);
            ClassGate.pushControl(gate, new AfterPush(this,
                    "PIN saved on this phone. Give students this PIN: " + studentPin
                            + ". Open Teacher / Class tools when you need class controls.",
                    S_TEACHER_NOVA));
        } else {
            if (!teacherPinMatches(pin)) {
                toast("That PIN is not right.");
                return;
            }
            // Upgrade a 1.0.0 on-phone hash to a salted one, still only on this phone.
            if (prefs.getString(P_PIN_SALT, "").isEmpty()) storeTeacherPin(pin);
            prefs.edit().putString(P_ROLE, "teacher").apply();
            if (needsStudentPinPublish()) {
                ensureStudentPin(true, S_TEACHER_NOVA);
            } else {
                go(S_TEACHER_NOVA);
            }
        }
    }

    void startClass() {
        long until = System.currentTimeMillis() + 3L * 60L * 60L * 1000L;
        gate.access.classOn = true;
        gate.access.classUntil = until;
        setBusy(true);
        ClassGate.pushControl(gate, new AfterPush(this, "Class is on for 3 hours. Students will see Nova when they refresh.", S_TEACHER_HOME));
    }

    void endClass() {
        gate.access.classOn = false;
        gate.access.classUntil = 0;
        setBusy(true);
        ClassGate.pushControl(gate, new AfterPush(this, "Class is off. Students will see Nova resting, unless you have allowed home use.", S_TEACHER_HOME));
    }

    void toggleHomeAll() {
        boolean on = !(gate.access != null && gate.access.homeAllActive(System.currentTimeMillis()));
        gate.access.homeUseAll = on;
        gate.access.homeUntil = on ? System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L : 0;
        setBusy(true);
        ClassGate.pushControl(gate, new AfterPush(this,
                on ? "Home use is on for everyone, for 7 days or until you turn it off."
                        : "Home use for everyone is off.",
                S_TEACHER_HOME));
    }

    void toggleStudentHome(String id, boolean on) {
        gate.access.setHome(id, on);
        setBusy(true);
        ClassGate.pushControl(gate, new AfterPush(this,
                on ? "Home use is on for that student." : "Home use is off for that student.",
                S_TEACHER_STUDENTS));
    }

    void startLesson() {
        String topic = topicField == null ? "" : topicField.getText().toString().trim();
        if (topic.isEmpty()) {
            toast("Please type or say a topic first.");
            return;
        }
        setBusy(true);
        if (statusLine != null) statusLine.setText("Nova is preparing the lesson…");
        new Thread(new LessonJob(this, topic), "nova-lesson").start();
    }

    void onLessonReady(ClassLesson.Lesson lesson, String error) {
        setBusy(false);
        if (lesson == null) {
            toast(error != null ? error : "I could not make a lesson from that topic.");
            return;
        }
        draftLesson = lesson;
        gate.access.lesson = lesson;
        ClassGate.pushControl(gate, new AfterPush(this, "Lesson is ready. Students can see the quiz when they have access.", S_TEACHER_CLASS));
        // Speak the explanation if Nova is available.
        if (nova == null) {
            SharedPreferences np = getSharedPreferences("nova", MODE_PRIVATE);
            nova = new NovaView(this, np, false);
            nova.setHost(this);
            nova.setKids(true);
        }
        try { nova.speak(lesson.explain); } catch (Exception ignored) { }
        show();
    }

    void submitStudentJoin() {
        String pin = codeField == null ? "" : codeField.getText().toString().trim();
        String name = nameField == null ? "" : nameField.getText().toString().trim();
        if (!ClassCodes.studentPinOk(pin)) {
            toast("Please type the 5 or 6 digit student PIN.");
            return;
        }
        if (name.isEmpty()) {
            toast("Please type your first name.");
            return;
        }
        String salt = gate.access != null ? gate.access.studentPinSalt : "";
        String hash = gate.access != null ? gate.access.studentPinHash : "";
        if (salt == null || hash == null || salt.isEmpty() || hash.isEmpty()) {
            toast("The teacher has not finished set-up, or the class switch is still loading. Try again in a moment.");
            ClassGate.pullControl(gate, new AfterPull(this, false));
            return;
        }
        if (!ClassCodes.saltedPinMatches(pin, salt, hash)) {
            toast("That student PIN is not right. Ask your teacher.");
            return;
        }
        prefs.edit().putString(P_ROLE, "student").putString(P_CODE, pin)
                .putString(P_JOIN_HASH, hash)
                .putString(P_SNAME, name).putString(P_SID, studentId()).apply();
        setBusy(true);
        ClassGate.checkIn(gate, studentId(), name, new AfterJoin(this, pin));
    }

    void askStartCode() {
        final EditText field = ClassUi.field(this, "Start or home code", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Start or home code")
                .setMessage("If the class switch is offline, your teacher can give you a start code or a home code.")
                .setView(field)
                .setPositiveButton("OK", new StartCodeOk(this, field))
                .setNegativeButton("Cancel", null)
                .show();
    }

    void applyStartCode(String raw) {
        String classCode = displayCode();
        if (classCode.isEmpty()) classCode = prefs.getString(P_CODE, "");
        ClassCodes.Grant g = ClassCodes.parse(raw, classCode, System.currentTimeMillis());
        if (g == null) {
            toast("That code is not right, or it is for a different class.");
            return;
        }
        gate.codeUntil = g.untilMs;
        gate.codeHome = g.home;
        prefs.edit().putLong(P_CODE_UNTIL, g.untilMs).apply();
        toast(g.home ? "Home use is on until the code runs out." : "Class is on for you until the code runs out.");
        refreshStudentGate(true);
    }

    void showStartCodeDialog() {
        String classCode = displayStudentPin();
        if (classCode.isEmpty()) {
            toast("Set up your class first.");
            return;
        }
        String start = ClassCodes.make(false, 3, classCode);
        String home = ClassCodes.make(true, 7, classCode);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Share a code")
                .setMessage("If a student cannot reach the class switch, they can type one of these:\n\n"
                        + "Start class for 3 hours:\n" + start + "\n\n"
                        + "Home use for 7 days:\n" + home + "\n\n"
                        + "These work even without the internet. They run out when the time is up.")
                .setPositiveButton("OK", null)
                .show();
    }

    void refreshStudentGate(boolean showAfter) {
        if (!isTeacher() && "student".equals(prefs.getString(P_ROLE, ""))) {
            ClassGate.pullControl(gate, new AfterPull(this, showAfter));
            String name = prefs.getString(P_SNAME, "");
            if (!name.isEmpty()) ClassGate.checkIn(gate, studentId(), name, null);
        } else if (showAfter) {
            routeFromPrefs();
            show();
        } else {
            ClassGate.pullControl(gate, new AfterPull(this, false));
        }
    }

    void onPulled(boolean ok) {
        setBusy(false);
        saveCache();
        if ("student".equals(prefs.getString(P_ROLE, ""))
                && !prefs.getString(P_SNAME, "").isEmpty()) {
            if (studentPinChanged()) {
                if (screen != S_STUDENT_JOIN) {
                    toast("The class PIN has changed. Ask your teacher for the new student PIN.");
                    go(S_STUDENT_JOIN);
                }
                return;
            }
            int next = studentAllowed() ? S_STUDENT_HOME : S_STUDENT_REST;
            if (!studentAllowed() && screen != S_STUDENT_JOIN && screen != S_STUDENT_REST) {
                go(S_STUDENT_REST);
                return;
            }
            if (screen == S_STUDENT_REST && studentAllowed()) go(S_STUDENT_HOME);
            else if (screen == S_STUDENT_REST) showResting();
            else if (screen == S_STUDENT_HOME) ClassStudio.showStudentHome(this);
        } else if (screen == S_TEACHER_HOME) {
            showTeacherHome();
        }
    }

    // ---- Nova host ----

    @Override public void onNovaExpandChanged(boolean expanded) { }

    @Override public boolean novaMicAllowed() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void novaRequestMic() {
        if (novaMicAllowed()) {
            if (nova != null) nova.onMicPermission(true);
            return;
        }
        boolean asked = NovaMicPerm.asked(this);
        boolean rationale = false;
        if (Build.VERSION.SDK_INT >= 23) {
            rationale = shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        if (asked && !rationale) {
            b.setTitle("The microphone is off")
                    .setMessage("Open Settings, tap Permissions, and allow the microphone, then tap the mic again.")
                    .setPositiveButton("Open Settings", new NovaMicOpenSettingsClick(this))
                    .setNegativeButton("Not now", new NovaMicNotNowClick(this))
                    .show();
            if (nova != null) nova.onPermissionDenied();
            return;
        }
        b.setTitle("Nova would like the mic")
                .setMessage("Nova listens so you can ask out loud. The words stay on this phone.")
                .setPositiveButton("Allow", new NovaMicAllowClick(this))
                .setNegativeButton("Not now", new NovaMicNotNowClick(this))
                .show();
    }

    void onMicDialogDenied() {
        if (nova != null) nova.onMicPermission(false);
    }

    void onMicDialogOpenSettings() {
        if (nova != null) nova.onPermissionDenied();
    }

    void speakNovaLine(String s) {
        if (nova != null && s != null) nova.speak(s);
    }

    @Override public void novaStartSpeechPrompt() {
        try { startActivityForResult(NovaSpeech.recognizeIntent(false), REQ_SPEECH); }
        catch (Exception e) { novaOpenSpeechInstall(); }
    }

    @Override public void novaOpenSpeechInstall() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("market://details?id=" + NovaSpeechAvail.TTS_PACKAGE)));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + NovaSpeechAvail.TTS_PACKAGE)));
            } catch (Exception e2) {
                toast(NovaSpeechAvail.NO_SERVICE);
            }
        }
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] grant) {
        super.onRequestPermissionsResult(req, perms, grant);
        if (req == REQ_MIC) {
            boolean ok = grant != null && grant.length > 0 && grant[0] == PackageManager.PERMISSION_GRANTED;
            if (nova != null) nova.onMicPermission(ok);
        }
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (result != RESULT_OK || data == null) return;
        ArrayList<String> said = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        String text = said != null && !said.isEmpty() ? said.get(0) : null;
        if (text == null || text.trim().isEmpty()) return;
        if (req == REQ_SPEECH && nova != null) nova.onSpeechPromptResult(text);
        if (req == REQ_TOPIC_SPEECH && topicField != null) {
            topicField.setText(text.trim());
        }
    }
}

final class GoClick implements View.OnClickListener {
    final MainActivity a;
    final int action;
    GoClick(MainActivity a, int action) { this.a = a; this.action = action; }
    @Override public void onClick(View v) { a.onGo(action); }
}

final class StudentHomeClick implements View.OnClickListener {
    final MainActivity a;
    final String id;
    final boolean on;
    StudentHomeClick(MainActivity a, String id, boolean on) {
        this.a = a; this.id = id; this.on = on;
    }
    @Override public void onClick(View v) { a.toggleStudentHome(id, on); }
}

final class ButtonSpeakTopic implements View.OnClickListener {
    final MainActivity a;
    ButtonSpeakTopic(MainActivity a) { this.a = a; }
    @Override public void onClick(View v) {
        try {
            a.startActivityForResult(NovaSpeech.recognizeIntent(false), MainActivity.REQ_TOPIC_SPEECH);
        } catch (Exception e) {
            a.novaOpenSpeechInstall();
        }
    }
}

final class StartCodeOk implements android.content.DialogInterface.OnClickListener {
    final MainActivity a;
    final EditText field;
    StartCodeOk(MainActivity a, EditText field) { this.a = a; this.field = field; }
    @Override public void onClick(android.content.DialogInterface d, int w) {
        a.applyStartCode(field.getText().toString());
    }
}

final class OpenNovaBig implements Runnable {
    final MainActivity a;
    final NovaView nova;
    OpenNovaBig(MainActivity a, NovaView nova) { this.a = a; this.nova = nova; }
    @Override public void run() {
        try { nova.setBig(true); } catch (Exception ignored) { }
        try { nova.setExpanded(true); } catch (Exception ignored) { }
        try { nova.enterVoiceMode(); } catch (Exception ignored) { }
        if (a.pendingAsk != null && !a.pendingAsk.isEmpty()) {
            String q = a.pendingAsk;
            a.pendingAsk = null;
            try { nova.ask(q); } catch (Exception ignored) { }
        }
    }
}

final class PollTick implements Runnable {
    final MainActivity a;
    PollTick(MainActivity a) { this.a = a; }
    @Override public void run() {
        a.pullTick();
        a.handler.postDelayed(this, MainActivity.POLL_MS);
    }
}

// helper name used above — implemented as method on MainActivity via this wrapper
final class AfterPull implements ClassGate.Cb {
    final MainActivity a;
    final boolean showAfter;
    AfterPull(MainActivity a, boolean showAfter) { this.a = a; this.showAfter = showAfter; }
    @Override public void done(boolean ok, String error) {
        a.saveCache();
        if (showAfter) {
            a.routeFromPrefs();
            if (a.studentPinChanged()) {
                a.go(MainActivity.S_STUDENT_JOIN);
            } else if ("student".equals(a.prefs.getString(MainActivity.P_ROLE, ""))
                    && !a.prefs.getString(MainActivity.P_SNAME, "").isEmpty()) {
                a.go(a.studentAllowed() ? MainActivity.S_STUDENT_HOME : MainActivity.S_STUDENT_REST);
            } else {
                a.show();
            }
        } else {
            a.onPulled(ok);
        }
    }
}

final class AfterPush implements ClassGate.Cb {
    final MainActivity a;
    final String message;
    final int next;
    AfterPush(MainActivity a, String message, int next) {
        this.a = a; this.message = message; this.next = next;
    }
    @Override public void done(boolean ok, String error) {
        a.setBusy(false);
        a.saveCache();
        if (!ok) a.toast("Saved on this phone, but the class switch could not be updated. " + (error == null ? "" : error));
        else if (message != null) a.toast(message);
        a.go(next);
    }
}

final class AfterRoster implements ClassGate.Cb {
    final MainActivity a;
    final boolean redraw;
    AfterRoster(MainActivity a, boolean redraw) { this.a = a; this.redraw = redraw; }
    @Override public void done(boolean ok, String error) {
        if (redraw && a.screen == MainActivity.S_TEACHER_STUDENTS) a.showStudents();
    }
}

final class AfterJoin implements ClassGate.Cb {
    final MainActivity a;
    final String code;
    AfterJoin(MainActivity a, String code) { this.a = a; this.code = code; }
    @Override public void done(boolean ok, String error) {
        a.setBusy(false);
        ClassGate.pullControl(a.gate, new AfterPull(a, true));
    }
}

final class LessonJob implements Runnable {
    final MainActivity a;
    final String topic;
    LessonJob(MainActivity a, String topic) { this.a = a; this.topic = topic; }
    @Override public void run() {
        ClassLesson.Lesson lesson = null;
        String err = null;
        try { lesson = ClassLesson.generate(topic); }
        catch (Exception e) { err = "The lesson could not be prepared."; }
        final ClassLesson.Lesson done = lesson;
        final String error = err;
        a.runOnUiThread(new LessonDone(a, done, error));
    }
}

final class LessonDone implements Runnable {
    final MainActivity a;
    final ClassLesson.Lesson lesson;
    final String error;
    LessonDone(MainActivity a, ClassLesson.Lesson lesson, String error) {
        this.a = a; this.lesson = lesson; this.error = error;
    }
    @Override public void run() { a.onLessonReady(lesson, error); }
}

final class NewStudentPinOk implements android.content.DialogInterface.OnClickListener {
    final MainActivity a;
    NewStudentPinOk(MainActivity a) { this.a = a; }
    @Override public void onClick(android.content.DialogInterface d, int w) {
        a.regenerateStudentPin();
    }
}
