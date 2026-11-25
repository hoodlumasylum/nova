package com.codeaque.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/** On-phone student progress only. Never uploaded. */
public final class NovaProgress {
    static final String PREFS = "nova-progress";
    static final String QUIZZES = "quizzesDone";
    static final String LAST = "lastScore";
    static final String LAST_TOTAL = "lastTotal";
    static final String BEST = "bestScore";
    static final String BEST_TOTAL = "bestTotal";
    static final String CARDS = "cardsPracticed";
    static final String TOPIC = "lastTopic";

    private NovaProgress() {}

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void recordQuiz(Context c, String topic, int score, int total) {
        SharedPreferences p = prefs(c);
        int quizzes = p.getInt(QUIZZES, 0) + 1;
        int best = p.getInt(BEST, 0);
        int bestTotal = p.getInt(BEST_TOTAL, 0);
        if (total > 0 && (bestTotal == 0 || score * bestTotal >= best * total)) {
            best = score;
            bestTotal = total;
        }
        p.edit()
                .putInt(QUIZZES, quizzes)
                .putInt(LAST, score)
                .putInt(LAST_TOTAL, total)
                .putInt(BEST, best)
                .putInt(BEST_TOTAL, bestTotal)
                .putString(TOPIC, topic == null ? "" : topic)
                .apply();
    }

    public static void recordCards(Context c, String topic, int seen) {
        SharedPreferences p = prefs(c);
        int n = p.getInt(CARDS, 0) + Math.max(0, seen);
        p.edit().putInt(CARDS, n).putString(TOPIC, topic == null ? "" : topic).apply();
    }

    public static String summary(Context c) {
        SharedPreferences p = prefs(c);
        int quizzes = p.getInt(QUIZZES, 0);
        int last = p.getInt(LAST, 0);
        int lastTotal = p.getInt(LAST_TOTAL, 0);
        int best = p.getInt(BEST, 0);
        int bestTotal = p.getInt(BEST_TOTAL, 0);
        int cards = p.getInt(CARDS, 0);
        String topic = p.getString(TOPIC, "");
        StringBuilder b = new StringBuilder();
        if (topic != null && !topic.isEmpty()) {
            b.append("Last topic practised: ").append(topic).append("\n\n");
        }
        b.append("Quizzes finished: ").append(quizzes).append('\n');
        if (lastTotal > 0) b.append("Last self-quiz: ").append(last).append(" out of ").append(lastTotal).append('\n');
        else b.append("Last self-quiz: not yet.\n");
        if (bestTotal > 0) b.append("Best score: ").append(best).append(" out of ").append(bestTotal).append('\n');
        else b.append("Best score: not yet.\n");
        b.append("Flashcards practised: ").append(cards);
        b.append("\n\nThis stays on this phone. Your teacher cannot see it from here.");
        return b.toString();
    }

    public static String parentSummary(Context c) {
        SharedPreferences p = prefs(c);
        int quizzes = p.getInt(QUIZZES, 0);
        int last = p.getInt(LAST, 0);
        int lastTotal = p.getInt(LAST_TOTAL, 0);
        int best = p.getInt(BEST, 0);
        int bestTotal = p.getInt(BEST_TOTAL, 0);
        int cards = p.getInt(CARDS, 0);
        String topic = p.getString(TOPIC, "");
        StringBuilder b = new StringBuilder();
        b.append("Child's progress (stored on this phone only)\n\n");
        if (topic != null && !topic.isEmpty()) {
            b.append("Last topic practised: ").append(topic).append("\n\n");
        }
        b.append("Quizzes finished: ").append(quizzes).append('\n');
        if (lastTotal > 0) b.append("Last self-quiz: ").append(last).append(" out of ").append(lastTotal).append('\n');
        else b.append("Last self-quiz: not yet.\n");
        if (bestTotal > 0) b.append("Best score: ").append(best).append(" out of ").append(bestTotal).append('\n');
        else b.append("Best score: not yet.\n");
        b.append("Flashcards practised: ").append(cards);
        return b.toString();
    }
}
