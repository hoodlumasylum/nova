package com.codeaque.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/** A class topic pack: explanation, quiz, flashcards, steps and homework. */
public final class ClassLesson {
    public static final class Item {
        public String q = "";
        public String a = "";
        public Item() {}
        public Item(String q, String a) { this.q = q; this.a = a; }
    }

    public static final class Card {
        public String front = "";
        public String back = "";
        public Card() {}
        public Card(String front, String back) { this.front = front; this.back = back; }
    }

    public static final class Lesson {
        public String topic = "";
        public String explain = "";
        public final ArrayList<Item> questions = new ArrayList<Item>();
        public final ArrayList<Card> cards = new ArrayList<Card>();
        public final ArrayList<String> steps = new ArrayList<String>();
        public String homework = "";
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
            JSONArray cards = new JSONArray();
            for (int i = 0; i < l.cards.size(); i++) {
                Card c = l.cards.get(i);
                JSONObject j = new JSONObject();
                j.put("f", c.front == null ? "" : c.front);
                j.put("b", c.back == null ? "" : c.back);
                cards.put(j);
            }
            o.put("cards", cards);
            JSONArray steps = new JSONArray();
            for (int i = 0; i < l.steps.size(); i++) {
                steps.put(l.steps.get(i) == null ? "" : l.steps.get(i));
            }
            o.put("steps", steps);
            o.put("hw", l.homework == null ? "" : l.homework);
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
        JSONArray cards = o.optJSONArray("cards");
        if (cards != null) {
            for (int i = 0; i < cards.length(); i++) {
                JSONObject c = cards.optJSONObject(i);
                if (c == null) continue;
                l.cards.add(new Card(c.optString("f", ""), c.optString("b", "")));
            }
        }
        JSONArray steps = o.optJSONArray("steps");
        if (steps != null) {
            for (int i = 0; i < steps.length(); i++) {
                String s = steps.optString(i, "");
                if (!s.isEmpty()) l.steps.add(s);
            }
        }
        l.homework = o.optString("hw", "");
        if ((l.cards.isEmpty() || l.steps.isEmpty() || isBlank(l.homework)) && !isBlank(l.topic)) {
            enrichForTools(l);
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
            while (l.questions.size() < 5) {
                l.questions.add(fallbackQuestion(t, l.questions.size()));
            }
            if (l.questions.size() > 8) {
                while (l.questions.size() > 8) l.questions.remove(l.questions.size() - 1);
            }
            for (int i = 0; i < l.questions.size(); i++) {
                l.questions.get(i).q = safeText(l.questions.get(i).q);
                l.questions.get(i).a = safeText(l.questions.get(i).a);
            }
            enrichForTools(l);
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
                + "and I will ask short questions to check your thinking.";
        l.questions.add(fallbackQuestion(topic, 0));
        l.questions.add(fallbackQuestion(topic, 1));
        l.questions.add(fallbackQuestion(topic, 2));
        return l;
    }

    static Item fallbackQuestion(String topic, int i) {
        if (i == 0) return new Item("In one sentence, what is " + topic + "?", "A short, clear meaning of " + topic + ".");
        if (i == 1) return new Item("Name one important fact about " + topic + ".", "Any true, simple fact about " + topic + ".");
        if (i == 2) return new Item("Why might people need to know about " + topic + "?", "A simple, real-world reason.");
        if (i == 3) return new Item("Can you give an example of " + topic + "?", "Any clear, real example.");
        if (i == 4) return new Item("What would you tell a friend about " + topic + "?", "One or two simple sentences in your own words.");
        if (i == 5) return new Item("What is one question you still have about " + topic + "?", "A thoughtful question, or 'I understand it for now.'");
        return new Item("True or false: learning about " + topic + " can help us in everyday life. Why?", "True, if you can give a simple reason.");
    }

    static void enrichForTools(Lesson l) {
        if (l == null) return;
        if (l.cards.isEmpty()) buildCards(l);
        while (l.cards.size() < 5) {
            int i = l.cards.size();
            l.cards.add(new Card("Remember this about " + l.topic, fallbackQuestion(l.topic, i % 6).a));
        }
        if (l.cards.size() > 10) {
            while (l.cards.size() > 10) l.cards.remove(l.cards.size() - 1);
        }
        for (int i = 0; i < l.cards.size(); i++) {
            l.cards.get(i).front = safeText(l.cards.get(i).front);
            l.cards.get(i).back = safeText(l.cards.get(i).back);
            if (l.cards.get(i).front.isEmpty()) l.cards.get(i).front = "Card " + (i + 1);
        }
        if (l.steps.isEmpty()) buildSteps(l);
        while (l.steps.size() < 3) l.steps.add("Think of one new thing you now know about " + l.topic + ".");
        if (l.steps.size() > 5) {
            while (l.steps.size() > 5) l.steps.remove(l.steps.size() - 1);
        }
        for (int i = 0; i < l.steps.size(); i++) {
            l.steps.set(i, safeText(l.steps.get(i)));
            if (l.steps.get(i).isEmpty()) l.steps.set(i, "Step " + (i + 1) + " of the lesson on " + l.topic + ".");
        }
        if (isBlank(l.homework)) l.homework = buildHomework(l);
        l.homework = safeMultiline(l.homework);
    }

    static void buildCards(Lesson l) {
        String topic = l.topic == null ? "this topic" : l.topic;
        if (!isBlank(l.explain)) {
            l.cards.add(new Card("What is " + topic + "?", firstClause(l.explain)));
            String[] bits = splitSentences(l.explain);
            for (int i = 0; i < bits.length && l.cards.size() < 8; i++) {
                String s = bits[i].trim();
                if (s.length() < 12) continue;
                l.cards.add(new Card("Fact " + l.cards.size() + " about " + topic, s));
            }
        }
        for (int i = 0; i < l.questions.size() && l.cards.size() < 10; i++) {
            Item it = l.questions.get(i);
            if (isBlank(it.q)) continue;
            l.cards.add(new Card(it.q, isBlank(it.a) ? "Say it in your own words." : it.a));
        }
        if (l.cards.isEmpty()) {
            l.cards.add(new Card("Today's topic", topic));
        }
    }

    static void buildSteps(Lesson l) {
        String topic = l.topic == null ? "this topic" : l.topic;
        l.steps.add("Today we are learning about " + topic + ".");
        if (!isBlank(l.explain)) {
            String[] bits = splitSentences(l.explain);
            for (int i = 0; i < bits.length && l.steps.size() < 4; i++) {
                String s = bits[i].trim();
                if (s.length() < 8) continue;
                l.steps.add(s);
            }
        }
        l.steps.add("Now think of one example of " + topic + ", or tell a friend one thing you learned.");
        if (l.steps.size() < 3) {
            l.steps.add("Ask Nova if any part of " + topic + " still feels unclear.");
        }
    }

    public static String buildHomework(Lesson l) {
        String topic = l.topic == null ? "today's topic" : l.topic;
        StringBuilder b = new StringBuilder();
        b.append("Nova homework: ").append(topic).append("\n\n");
        if (!isBlank(l.explain)) b.append("Remember:\n").append(l.explain).append("\n\n");
        b.append("Try these at home (write short answers):\n");
        int n = Math.min(4, l.questions.size());
        for (int i = 0; i < n; i++) {
            b.append(i + 1).append(". ").append(l.questions.get(i).q).append("\n");
        }
        if (n == 0) {
            b.append("1. What is ").append(topic).append("?\n");
            b.append("2. Name one fact about it.\n");
            b.append("3. Give one example.\n");
        }
        b.append("\nYou may ask a teacher or a grown-up if you get stuck. Keep it simple and in your own words.");
        return b.toString();
    }

    static String[] splitSentences(String s) {
        if (s == null || s.isEmpty()) return new String[0];
        ArrayList<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            cur.append(ch);
            if (ch == '.' || ch == '!' || ch == '?') {
                String bit = cur.toString().trim();
                if (!bit.isEmpty()) out.add(bit);
                cur.setLength(0);
            }
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) out.add(tail);
        return out.toArray(new String[0]);
    }

    static String safeMultiline(String s) {
        if (s == null) return "";
        String t = s.replace('\r', '\n');
        String[] lines = splitLines(t);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = safeText(raw);
            if (line.isEmpty() && raw.trim().isEmpty()) {
                if (b.length() > 0) b.append('\n');
                continue;
            }
            if (line.isEmpty()) continue;
            if (b.length() > 0) b.append('\n');
            b.append(line);
        }
        return b.toString();
    }

    static String[] splitLines(String t) {
        ArrayList<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == '\n') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
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
