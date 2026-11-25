package com.codeaque.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/** A short explanation plus three quiz questions for Class mode. */
public final class ClassLesson {
    public static final class Item {
        public String q = "";
        public String a = "";
        public Item() {}
        public Item(String q, String a) { this.q = q; this.a = a; }
    }

    public static final class Lesson {
        public String topic = "";
        public String explain = "";
        public final ArrayList<Item> questions = new ArrayList<Item>();
        public long startedAt;
    }

    private ClassLesson() {}

    public static JSONObject toJson(Lesson l) {
        try {
            JSONObject o = new JSONObject();
            o.put("topic", l.topic == null ? "" : l.topic);
            o.put("explain", l.explain == null ? "" : l.explain);
            o.put("startedAt", l.startedAt);
            JSONArray qs = new JSONArray();
            for (int i = 0; i < l.questions.size(); i++) {
                Item it = l.questions.get(i);
                JSONObject q = new JSONObject();
                q.put("q", it.q == null ? "" : it.q);
                q.put("a", it.a == null ? "" : it.a);
                qs.put(q);
            }
            o.put("qs", qs);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static Lesson fromJson(JSONObject o) {
        if (o == null) return null;
        Lesson l = new Lesson();
        l.topic = o.optString("topic", "");
        l.explain = o.optString("explain", "");
        l.startedAt = o.optLong("startedAt", 0);
        JSONArray qs = o.optJSONArray("qs");
        if (qs != null) {
            for (int i = 0; i < qs.length(); i++) {
                JSONObject q = qs.optJSONObject(i);
                if (q == null) continue;
                l.questions.add(new Item(q.optString("q", ""), q.optString("a", "")));
            }
        }
        return l;
    }

    /** Off the UI thread. Uses the on-phone brain if ready, else Wikipedia, else a simple fallback. */
    public static Lesson generate(String topic) {
        String t = topic == null ? "" : topic.trim();
        if (t.length() > 80) t = t.substring(0, 80);
        if (t.isEmpty()) return null;
        Lesson l = fromModel(t);
        if (l == null) l = fromWiki(t);
        if (l == null) l = fallback(t);
        if (l != null) {
            l.topic = t;
            l.startedAt = System.currentTimeMillis();
            l.explain = safeText(l.explain);
            for (int i = 0; i < l.questions.size(); i++) {
                Item it = l.questions.get(i);
                it.q = safeText(it.q);
                it.a = safeText(it.a);
            }
            while (l.questions.size() < 3) {
                l.questions.add(fallbackQuestion(t, l.questions.size()));
            }
            if (l.questions.size() > 3) {
                while (l.questions.size() > 3) l.questions.remove(l.questions.size() - 1);
            }
        }
        return l;
    }

    static Lesson fromModel(String topic) {
        if (!NovaLlm.isLoaded()) return null;
        String prompt = NovaLlmFilter.SYSTEM + "\n\n"
                + "A teacher asked you to teach this topic to a class: " + topic + "\n"
                + "Write a short, simple explanation in 3 or 4 sentences, then exactly 3 quiz questions "
                + "with short answers. Use this exact shape:\n"
                + "EXPLAIN: ...\nQ1: ...\nA1: ...\nQ2: ...\nA2: ...\nQ3: ...\nA3: ...\n"
                + "No religion, god, or conspiracies. Easy English. Do not use other assistant names.";
        String raw = NovaLlm.complete(prompt, 220);
        if (raw == null) return null;
        Lesson l = parseBlock(raw);
        return l != null && !isBlank(l.explain) ? l : null;
    }

    static Lesson fromWiki(String topic) {
        String fact = NovaWiki.fetchSummary(topic);
        if (fact == null || fact.isEmpty()) return null;
        if (NovaHelp.kidsUnsafe(fact)) return null;
        Lesson l = new Lesson();
        l.explain = fact;
        l.questions.add(new Item("What is " + topic + " about, in your own words?", fact));
        l.questions.add(fallbackQuestion(topic, 1));
        l.questions.add(new Item("Name one fact you just heard about " + topic + ".", firstClause(fact)));
        return l;
    }

    static Lesson fallback(String topic) {
        Lesson l = new Lesson();
        l.explain = "Let's talk about " + topic + ". Think about what you already know, "
                + "and I will ask three short questions to check your thinking.";
        l.questions.add(fallbackQuestion(topic, 0));
        l.questions.add(fallbackQuestion(topic, 1));
        l.questions.add(fallbackQuestion(topic, 2));
        return l;
    }

    static Item fallbackQuestion(String topic, int i) {
        if (i == 0) return new Item("In one sentence, what is " + topic + "?", "A short, clear meaning of " + topic + ".");
        if (i == 1) return new Item("Name one important fact about " + topic + ".", "Any true, simple fact about " + topic + ".");
        return new Item("Why might people need to know about " + topic + "?", "A simple, real-world reason.");
    }

    static Lesson parseBlock(String raw) {
        if (raw == null) return null;
        String t = raw.replace('\r', '\n');
        Lesson l = new Lesson();
        l.explain = after(t, "EXPLAIN:");
        String q1 = after(t, "Q1:");
        String a1 = after(t, "A1:");
        String q2 = after(t, "Q2:");
        String a2 = after(t, "A2:");
        String q3 = after(t, "Q3:");
        String a3 = after(t, "A3:");
        if (l.explain == null) l.explain = "";
        if (!isBlank(q1)) l.questions.add(new Item(q1, a1 == null ? "" : a1));
        if (!isBlank(q2)) l.questions.add(new Item(q2, a2 == null ? "" : a2));
        if (!isBlank(q3)) l.questions.add(new Item(q3, a3 == null ? "" : a3));
        if (l.explain.isEmpty() && l.questions.isEmpty()) return null;
        return l;
    }

    static String after(String t, String key) {
        int i = indexIgnore(t, key);
        if (i < 0) return "";
        int s = i + key.length();
        int end = t.length();
        String[] next = new String[]{"\nQ1:", "\nA1:", "\nQ2:", "\nA2:", "\nQ3:", "\nA3:", "\nEXPLAIN:"};
        for (int k = 0; k < next.length; k++) {
            int n = indexIgnore(t.substring(s), next[k].trim());
            // search remaining from s
        }
        String rest = t.substring(s);
        int cut = rest.length();
        String[] marks = new String[]{"Q1:", "A1:", "Q2:", "A2:", "Q3:", "A3:", "EXPLAIN:"};
        for (int k = 0; k < marks.length; k++) {
            if (marks[k].equalsIgnoreCase(key)) continue;
            int n = indexIgnore(rest, marks[k]);
            if (n >= 0 && n < cut) cut = n;
        }
        return rest.substring(0, cut).replace('\n', ' ').trim();
    }

    static int indexIgnore(String t, String key) {
        if (t == null || key == null) return -1;
        String low = t.toLowerCase();
        return low.indexOf(key.toLowerCase());
    }

    static String safeText(String s) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').trim();
        if (t.length() > 400) t = t.substring(0, 400).trim();
        t = NovaKidsTalk.sanitizeReply(t);
        if (NovaHelp.kidsUnsafe(t)) return "";
        t = stripOtherNames(t);
        return t;
    }

    static String stripOtherNames(String t) {
        if (t == null) return "";
        String[] names = new String[]{
                "ChatGPT", "Chat GPT", "GPT-4", "GPT-3", "OpenAI", "Gemini", "Claude",
                "Copilot", "Grok", "LLaMA", "Llama", "Gemma", "Bard"
        };
        String out = t;
        for (int i = 0; i < names.length; i++) {
            out = out.replace(names[i], "Nova");
        }
        return out;
    }

    static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    static String firstClause(String s) {
        if (s == null) return "";
        int i = s.indexOf('.');
        if (i > 12) return s.substring(0, i + 1).trim();
        return s;
    }
}
