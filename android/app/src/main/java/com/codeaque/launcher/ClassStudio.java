package com.codeaque.launcher;

import android.content.Intent;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Teacher and student topic tools: flashcards, quiz, steps, homework, progress. */
public final class ClassStudio {
    static final int TIMER_SECS = 20;

    static int cardIndex;
    static boolean cardFlipped;
    static int cardsSeen;
    static int stepIndex;
    static int quizIndex;
    static int quizScore;
    static boolean quizRevealed;
    static int quizSeconds;
    static boolean teacherQuick;
    static int spokenQuiz = -2;
    static boolean spokenReveal;
    static final Handler timer = new Handler();
    static final Runnable tick = new QuickTick();
    static MainActivity host;

    private ClassStudio() {}

    static void stopTimer() {
        timer.removeCallbacks(tick);
    }

    static ClassLesson.Lesson lesson(MainActivity a) {
        if (a.draftLesson != null) return a.draftLesson;
        if (a.gate.access != null && a.gate.access.lesson != null) return a.gate.access.lesson;
        return null;
    }

    static boolean hasTopic(ClassLesson.Lesson l) {
        return l != null && l.topic != null && !l.topic.isEmpty();
    }

    static void speak(MainActivity a, String s) {
        if (s == null || s.trim().isEmpty()) return;
        a.ensureNovaQuiet();
        try { a.nova.speak(s); } catch (Exception ignored) { }
    }

    static void showStudentHome(MainActivity a) {
        stopTimer();
        LinearLayout col = ClassUi.col(a);
        a.addUpdateBanner(col);
        col.addView(ClassUi.title(a, "Your class"));
        ClassLesson.Lesson l = lesson(a);
        if (hasTopic(l)) {
            col.addView(ClassUi.hint(a, "Today's topic: " + l.topic));
        } else {
            col.addView(ClassUi.hint(a, "Your teacher has not pushed a topic yet. You can still talk with Nova while class is on."));
        }
        col.addView(ClassUi.primary(a, "Talk with Nova", new GoClick(a, 14)));
        if (hasTopic(l)) {
            col.addView(ClassUi.primary(a, "Today's topic", new GoClick(a, 40)));
            col.addView(ClassUi.secondary(a, "Practice flashcards", new GoClick(a, 41)));
            col.addView(ClassUi.secondary(a, "Self-quiz", new GoClick(a, 42)));
            col.addView(ClassUi.secondary(a, "Ask Nova about the lesson", new GoClick(a, 43)));
        }
        col.addView(ClassUi.secondary(a, "My progress", new GoClick(a, 44)));
        a.statusLine = ClassUi.hint(a, a.statusText());
        col.addView(a.statusLine);
        a.setContentView(ClassUi.screen(a, col));
    }

    static void showToday(MainActivity a) {
        LinearLayout col = ClassUi.col(a);
        ClassLesson.Lesson l = lesson(a);
        col.addView(ClassUi.title(a, hasTopic(l) ? l.topic : "Today's topic"));
        if (l != null && l.explain != null && !l.explain.isEmpty()) {
            col.addView(ClassUi.body(a, l.explain));
            speak(a, l.explain);
        } else {
            col.addView(ClassUi.body(a, "There is no lesson on this phone yet."));
        }
        if (hasTopic(l)) {
            col.addView(ClassUi.secondary(a, "Practice flashcards", new GoClick(a, 41)));
            col.addView(ClassUi.secondary(a, "Self-quiz", new GoClick(a, 42)));
            col.addView(ClassUi.secondary(a, "Ask Nova about the lesson", new GoClick(a, 43)));
        }
        col.addView(ClassUi.primary(a, "Back", new GoClick(a, 45)));
        a.setContentView(ClassUi.screen(a, col));
    }

    static void beginCards(MainActivity a) {
        ClassLesson.Lesson l = lesson(a);
        if (l == null || l.cards.isEmpty()) {
            a.toast("There are no flashcards for this topic yet.");
            return;
        }
        cardIndex = 0;
        cardFlipped = false;
        cardsSeen = 0;
        a.go(a.isTeacher() ? MainActivity.S_TEACHER_CARDS : MainActivity.S_STUDENT_CARDS);
    }

    static void showCards(MainActivity a, boolean teacher) {
        ClassLesson.Lesson l = lesson(a);
        LinearLayout col = ClassUi.col(a);
        if (l == null || l.cards.isEmpty()) {
            col.addView(ClassUi.title(a, "Flashcards"));
            col.addView(ClassUi.body(a, "There are no flashcards yet."));
            col.addView(ClassUi.primary(a, "Back", new GoClick(a, teacher ? 13 : 45)));
            a.setContentView(ClassUi.screen(a, col));
            return;
        }
        if (cardIndex < 0) cardIndex = 0;
        if (cardIndex >= l.cards.size()) cardIndex = l.cards.size() - 1;
        ClassLesson.Card c = l.cards.get(cardIndex);
        col.addView(ClassUi.title(a, "Flashcards"));
        col.addView(ClassUi.hint(a, "Card " + (cardIndex + 1) + " of " + l.cards.size()
                + (teacher ? ". Nova will read each side aloud." : ". Practise, then flip to check.")));
        String shown = cardFlipped ? (c.back == null || c.back.isEmpty() ? "(no answer yet)" : c.back)
                : (c.front == null ? "" : c.front);
        col.addView(ClassUi.huge(a, shown));
        col.addView(ClassUi.primary(a, cardFlipped ? "Show the question" : "Flip card", new GoClick(a, 50)));
        col.addView(ClassUi.secondary(a, "Next card", new GoClick(a, 51)));
        if (cardIndex > 0) col.addView(ClassUi.secondary(a, "Previous card", new GoClick(a, 52)));
        col.addView(ClassUi.primary(a, "Back", new GoClick(a, teacher ? 13 : 45)));
        a.setContentView(ClassUi.screen(a, col));
        speak(a, shown);
        if (cardsSeen < cardIndex + 1) cardsSeen = cardIndex + 1;
    }

    static void flipCard(MainActivity a) {
        cardFlipped = !cardFlipped;
        showCards(a, a.isTeacher());
    }

    static void nextCard(MainActivity a) {
        ClassLesson.Lesson l = lesson(a);
        if (l == null) return;
        if (cardIndex + 1 < l.cards.size()) {
            cardIndex++;
            cardFlipped = false;
            showCards(a, a.isTeacher());
            return;
        }
        if (!a.isTeacher()) {
            NovaProgress.recordCards(a, l.topic, Math.max(1, cardsSeen));
        }
        a.toast("That was the last card.");
        a.go(a.isTeacher() ? MainActivity.S_TEACHER_CLASS : MainActivity.S_STUDENT_HOME);
    }

    static void prevCard(MainActivity a) {
        if (cardIndex > 0) {
            cardIndex--;
            cardFlipped = false;
        }
        showCards(a, a.isTeacher());
    }

    static void beginSteps(MainActivity a) {
        ClassLesson.Lesson l = lesson(a);
        if (l == null || l.steps.isEmpty()) {
            a.toast("There is no step-by-step lesson yet.");
            return;
        }
        stepIndex = 0;
        a.go(a.isTeacher() ? MainActivity.S_TEACHER_STEPS : MainActivity.S_TODAY);
        if (a.isTeacher()) showSteps(a, true);
        else showToday(a);
    }

    static void showSteps(MainActivity a, boolean teacher) {
        ClassLesson.Lesson l = lesson(a);
        LinearLayout col = ClassUi.col(a);
        col.addView(ClassUi.title(a, "Step-by-step"));
        if (l == null || l.steps.isEmpty()) {
            col.addView(ClassUi.body(a, "There are no steps yet."));
            col.addView(ClassUi.primary(a, "Back", new GoClick(a, 13)));
            a.setContentView(ClassUi.screen(a, col));
            return;
        }
        if (stepIndex < 0) stepIndex = 0;
        if (stepIndex >= l.steps.size()) stepIndex = l.steps.size() - 1;
        String step = l.steps.get(stepIndex);
        col.addView(ClassUi.hint(a, "Step " + (stepIndex + 1) + " of " + l.steps.size()));
        col.addView(ClassUi.huge(a, step));
        if (stepIndex + 1 < l.steps.size()) {
            col.addView(ClassUi.primary(a, "Next", new GoClick(a, 54)));
        } else {
            col.addView(ClassUi.primary(a, "Finish", new GoClick(a, 13)));
        }
        if (stepIndex > 0) col.addView(ClassUi.secondary(a, "Back one step", new GoClick(a, 55)));
        col.addView(ClassUi.secondary(a, "Back to class mode", new GoClick(a, 13)));
        a.setContentView(ClassUi.screen(a, col));
        speak(a, "Step " + (stepIndex + 1) + ". " + step);
    }

    static void nextStep(MainActivity a) {
        ClassLesson.Lesson l = lesson(a);
        if (l != null && stepIndex + 1 < l.steps.size()) stepIndex++;
        showSteps(a, true);
    }

    static void prevStep(MainActivity a) {
        if (stepIndex > 0) stepIndex--;
        showSteps(a, true);
    }

    static void beginQuick(MainActivity a, boolean teacher) {
        ClassLesson.Lesson l = lesson(a);
        if (l == null || l.questions.isEmpty()) {
            a.toast("There is no quiz for this topic yet.");
            return;
        }
        quizIndex = 0;
        quizScore = 0;
        quizRevealed = false;
        quizSeconds = TIMER_SECS;
        teacherQuick = teacher;
        spokenQuiz = -2;
        spokenReveal = false;
        host = a;
        a.go(teacher ? MainActivity.S_TEACHER_QUICK : MainActivity.S_SELF_QUIZ);
    }

    static void showQuick(MainActivity a, boolean teacher) {
        ClassLesson.Lesson l = lesson(a);
        LinearLayout col = ClassUi.col(a);
        col.addView(ClassUi.title(a, teacher ? "Quick-fire quiz" : "Self-quiz"));
        if (l == null || l.questions.isEmpty()) {
            col.addView(ClassUi.body(a, "There is no quiz yet."));
            col.addView(ClassUi.primary(a, "Back", new GoClick(a, teacher ? 13 : 45)));
            a.setContentView(ClassUi.screen(a, col));
            return;
        }
        if (quizIndex >= l.questions.size()) {
            stopTimer();
            col.addView(ClassUi.huge(a, "Score: " + quizScore + " out of " + l.questions.size()));
            if (teacher) {
                col.addView(ClassUi.body(a, "That is the class tally for this round."));
            } else {
                NovaProgress.recordQuiz(a, l.topic, quizScore, l.questions.size());
                col.addView(ClassUi.body(a, "Saved on this phone in My progress."));
            }
            col.addView(ClassUi.primary(a, "Back", new GoClick(a, teacher ? 13 : 45)));
            a.setContentView(ClassUi.screen(a, col));
            speak(a, "The score is " + quizScore + " out of " + l.questions.size() + ".");
            return;
        }
        ClassLesson.Item it = l.questions.get(quizIndex);
        col.addView(ClassUi.hint(a, "Question " + (quizIndex + 1) + " of " + l.questions.size()
                + "    Score: " + quizScore));
        if (teacher && !quizRevealed) {
            col.addView(ClassUi.banner(a, quizSeconds + " seconds", quizSeconds > 5));
        }
        col.addView(ClassUi.huge(a, it.q == null ? "" : it.q));
        if (quizRevealed) {
            String ans = it.a == null || it.a.isEmpty() ? "Talk it through as a class." : it.a;
            col.addView(ClassUi.heading(a, "Answer"));
            col.addView(ClassUi.huge(a, ans));
            col.addView(ClassUi.primary(a, teacher ? "They got it" : "I got it", new GoClick(a, 56)));
            col.addView(ClassUi.secondary(a, teacher ? "Not this time" : "Not yet", new GoClick(a, 57)));
        } else {
            col.addView(ClassUi.primary(a, "Reveal answer", new GoClick(a, 58)));
        }
        col.addView(ClassUi.secondary(a, "Back", new GoClick(a, teacher ? 13 : 45)));
        a.setContentView(ClassUi.screen(a, col));
        if (!quizRevealed) {
            if (spokenQuiz != quizIndex) {
                speak(a, it.q);
                spokenQuiz = quizIndex;
            }
            if (teacher) {
                stopTimer();
                timer.postDelayed(tick, 1000);
            }
        }
    }

    static void reveal(MainActivity a) {
        stopTimer();
        if (quizRevealed) return;
        quizRevealed = true;
        showQuick(a, teacherQuick);
        ClassLesson.Lesson l = lesson(a);
        if (!spokenReveal) {
            spokenReveal = true;
            if (l != null && quizIndex < l.questions.size()) {
                String ans = l.questions.get(quizIndex).a;
                if (ans != null && !ans.isEmpty()) speak(a, ans);
            }
        }
    }

    static void mark(MainActivity a, boolean correct) {
        if (correct) quizScore++;
        quizIndex++;
        quizRevealed = false;
        spokenReveal = false;
        quizSeconds = TIMER_SECS;
        showQuick(a, teacherQuick);
    }

    static void onTick() {
        MainActivity a = host;
        if (a == null) return;
        if (a.screen != MainActivity.S_TEACHER_QUICK) {
            stopTimer();
            return;
        }
        if (quizRevealed) return;
        quizSeconds--;
        if (quizSeconds <= 0) {
            quizSeconds = 0;
            reveal(a);
            return;
        }
        showQuick(a, true);
    }

    static void shareHomework(MainActivity a) {
        ClassLesson.Lesson l = lesson(a);
        if (l == null) {
            a.toast("Make a lesson first, then you can share the homework.");
            return;
        }
        String text = l.homework;
        if (text == null || text.trim().isEmpty()) text = ClassLesson.buildHomework(l);
        if (text == null || text.trim().isEmpty()) {
            a.toast("There is no homework sheet yet.");
            return;
        }
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "Homework: " + (l.topic == null ? "Nova" : l.topic));
            send.putExtra(Intent.EXTRA_TEXT, text);
            a.startActivity(Intent.createChooser(send, "Share homework"));
        } catch (Exception e) {
            a.toast("I could not open the Android share sheet.");
        }
    }

    static void showProgress(MainActivity a) {
        LinearLayout col = ClassUi.col(a);
        col.addView(ClassUi.title(a, "My progress"));
        col.addView(ClassUi.body(a, NovaProgress.summary(a)));
        col.addView(ClassUi.primary(a, "Back", new GoClick(a, 45)));
        a.setContentView(ClassUi.screen(a, col));
    }

    static void askAboutLesson(MainActivity a) {
        ClassLesson.Lesson l = lesson(a);
        if (!hasTopic(l)) {
            a.toast("There is no lesson to ask about yet.");
            return;
        }
        String recap = l.explain == null ? "" : l.explain;
        if (recap.length() > 220) recap = recap.substring(0, 220);
        a.pendingAsk = "Please help me with today's lesson about " + l.topic + ". " + recap
                + " Keep it simple, and wait for my question if I have one.";
        a.go(MainActivity.S_STUDENT_NOVA);
    }
}

final class QuickTick implements Runnable {
    @Override public void run() { ClassStudio.onTick(); }
}
