package com.codeaque.launcher;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Class access switch. The teacher app writes a small JSON page on a free
 * public host (rentry.co). Student phones read it. No accounts or API keys
 * are required from the teacher. Session codes still work if the page cannot
 * be reached.
 */
public final class ClassGate {
    public static final String CONTROL_URL = "https://rentry.co/nvafyepuunj719";
    public static final String CONTROL_EDIT = "yayytpjo226Aw6vSgvBo";
    public static final String ROSTER_URL = "https://rentry.co/nvcg3t8wg934fc";
    public static final String ROSTER_EDIT = "ng9hZGKDqibTp7Tv4Ryb";
    public static final String UA = "Mozilla/5.0 (Linux; Android 13) NovaClass/1.0";
    public static final String MARK = "NOVAJSON:";
    public static final long STALE_MS = 24L * 60L * 60L * 1000L;

    public interface Cb {
        void done(boolean ok, String error);
    }

    private ClassGate() {}

    public static boolean allowed(ClassAccess a, String studentId, long now,
            long codeUntil, long lastFetchAt) {
        if (a != null) {
            if (a.classActive(now)) return true;
            if (a.homeAllActive(now)) return true;
            if (a.homeAllowedFor(studentId, now)) return true;
        }
        if (codeUntil > now) return true;
        // If the last successful fetch is older than a day, do not keep a
        // stale "class is on" flag forever. Typed codes still use codeUntil.
        return false;
    }

    public static String reasonLocked(ClassAccess a, long now, long lastFetchAt, boolean offline) {
        if (offline && lastFetchAt > 0 && now - lastFetchAt > 60_000L) {
            return "Nova is resting until class";
        }
        return "Nova is resting until class";
    }

    public static ClassAccess fromJson(String json) {
        ClassAccess a = new ClassAccess();
        if (json == null || json.isEmpty()) return a;
        try {
            JSONObject o = new JSONObject(json);
            a.classCode = o.optString("classCode", "");
            a.pinHash = o.optString("pinHash", "");
            a.classOn = o.optBoolean("classOn", false);
            a.classUntil = o.optLong("classUntil", 0);
            a.homeUseAll = o.optBoolean("homeUseAll", false);
            a.homeUntil = o.optLong("homeUntil", 0);
            a.updatedAt = o.optLong("updatedAt", 0);
            JSONArray allow = o.optJSONArray("homeAllow");
            if (allow != null) {
                for (int i = 0; i < allow.length(); i++) {
                    String id = allow.optString(i, "");
                    if (!id.isEmpty()) a.homeAllow.add(id);
                }
            }
            if (o.has("lesson") && !o.isNull("lesson")) {
                a.lesson = ClassLesson.fromJson(o.optJSONObject("lesson"));
            }
        } catch (Exception ignored) { }
        return a;
    }

    public static String toJson(ClassAccess a) {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 1);
            o.put("classCode", a.classCode == null ? "" : a.classCode);
            o.put("pinHash", a.pinHash == null ? "" : a.pinHash);
            o.put("classOn", a.classOn);
            o.put("classUntil", a.classUntil);
            o.put("homeUseAll", a.homeUseAll);
            o.put("homeUntil", a.homeUntil);
            o.put("updatedAt", a.updatedAt);
            JSONArray allow = new JSONArray();
            for (int i = 0; i < a.homeAllow.size(); i++) allow.put(a.homeAllow.get(i));
            o.put("homeAllow", allow);
            if (a.lesson != null) o.put("lesson", ClassLesson.toJson(a.lesson));
            else o.put("lesson", JSONObject.NULL);
            return o.toString();
        } catch (Exception e) {
            return "{\"v\":1}";
        }
    }

    public static ArrayList<ClassStudent> rosterFromJson(String json) {
        ArrayList<ClassStudent> list = new ArrayList<ClassStudent>();
        if (json == null || json.isEmpty()) return list;
        try {
            JSONObject o = new JSONObject(json);
            JSONArray arr = o.optJSONArray("students");
            if (arr == null) return list;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject s = arr.optJSONObject(i);
                if (s == null) continue;
                ClassStudent st = new ClassStudent();
                st.id = s.optString("id", "");
                st.name = s.optString("name", "");
                st.lastSeen = s.optLong("lastSeen", 0);
                if (!st.id.isEmpty()) list.add(st);
            }
        } catch (Exception ignored) { }
        return list;
    }

    public static String rosterToJson(ArrayList<ClassStudent> list) {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 1);
            JSONArray arr = new JSONArray();
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    ClassStudent s = list.get(i);
                    JSONObject j = new JSONObject();
                    j.put("id", s.id == null ? "" : s.id);
                    j.put("name", s.name == null ? "" : s.name);
                    j.put("lastSeen", s.lastSeen);
                    arr.put(j);
                }
            }
            o.put("students", arr);
            return o.toString();
        } catch (Exception e) {
            return "{\"v\":1,\"students\":[]}";
        }
    }

    public static String extractJson(String html) {
        if (html == null) return null;
        int best = -1;
        int from = 0;
        while (true) {
            int i = html.indexOf(MARK, from);
            if (i < 0) break;
            int s = i + MARK.length();
            if (s < html.length() && html.charAt(s) == '{') best = s;
            from = i + 1;
        }
        if (best < 0) return null;
        String json = braceCut(html, best);
        return json;
    }

    static String braceCut(String s, int start) {
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') inStr = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return s.substring(start, i + 1);
            }
        }
        return null;
    }

    public static String fetchPage(String pageUrl) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(pageUrl).openConnection();
        try {
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "text/html");
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null || code >= 400) throw new Exception("Could not read the class page (" + code + ").");
            return readAll(in, 400_000);
        } finally {
            c.disconnect();
        }
    }

    public static void editPage(String pageUrl, String editCode, String text) throws Exception {
        String id = pageUrl;
        int slash = pageUrl.lastIndexOf('/');
        if (slash >= 0) id = pageUrl.substring(slash + 1);
        String api = "https://rentry.co/api/edit/" + id;
        String body = "edit_code=" + URLEncoder.encode(editCode, "UTF-8")
                + "&text=" + URLEncoder.encode(MARK + text, "UTF-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection c = (HttpURLConnection) new URL(api).openConnection();
        try {
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            c.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            OutputStream out = c.getOutputStream();
            out.write(bytes);
            out.flush();
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String resp = in == null ? "" : readAll(in, 20_000);
            if (code >= 400 || (resp != null && resp.contains("\"status\": \"4"))) {
                throw new Exception("Could not update the class page.");
            }
        } finally {
            c.disconnect();
        }
    }

    static String readAll(InputStream in, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n, total = 0;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            total += n;
            if (total > max) break;
        }
        return out.toString("UTF-8");
    }

    public static void pullControl(Holder h, Cb cb) {
        go(new PullControlJob(h, cb));
    }

    public static void pushControl(Holder h, Cb cb) {
        go(new PushControlJob(h, cb));
    }

    public static void pullRoster(Holder h, Cb cb) {
        go(new PullRosterJob(h, cb));
    }

    public static void checkIn(Holder h, String id, String name, Cb cb) {
        go(new CheckInJob(h, id, name, cb));
    }

    static void go(Runnable r) {
        new Thread(r, "nova-class").start();
    }

    static void ui(Runnable r) {
        new Handler(Looper.getMainLooper()).post(r);
    }

    /** Shared live state for the app process. */
    public static final class Holder {
        public ClassAccess access = new ClassAccess();
        public ArrayList<ClassStudent> roster = new ArrayList<ClassStudent>();
        public long lastFetchAt;
        public boolean online;
        public String lastError;
        public long codeUntil;
        public boolean codeHome;
    }
}

final class PullControlJob implements Runnable {
    final ClassGate.Holder h;
    final ClassGate.Cb cb;
    PullControlJob(ClassGate.Holder h, ClassGate.Cb cb) { this.h = h; this.cb = cb; }
    @Override public void run() {
        try {
            String html = ClassGate.fetchPage(ClassGate.CONTROL_URL);
            String json = ClassGate.extractJson(html);
            if (json == null) throw new Exception("The class page was empty.");
            ClassGate.ui(new PullControlOk(h, cb, ClassGate.fromJson(json)));
        } catch (Exception e) {
            ClassGate.ui(new GateFail(h, cb, e.getMessage(), true));
        }
    }
}

final class PullControlOk implements Runnable {
    final ClassGate.Holder h;
    final ClassGate.Cb cb;
    final ClassAccess access;
    PullControlOk(ClassGate.Holder h, ClassGate.Cb cb, ClassAccess access) {
        this.h = h; this.cb = cb; this.access = access;
    }
    @Override public void run() {
        h.access = access;
        h.lastFetchAt = System.currentTimeMillis();
        h.online = true;
        h.lastError = null;
        if (cb != null) cb.done(true, null);
    }
}

final class PushControlJob implements Runnable {
    final ClassGate.Holder h;
    final ClassGate.Cb cb;
    PushControlJob(ClassGate.Holder h, ClassGate.Cb cb) { this.h = h; this.cb = cb; }
    @Override public void run() {
        try {
            ClassAccess a = h.access;
            if (a == null) a = new ClassAccess();
            a.updatedAt = System.currentTimeMillis();
            ClassGate.editPage(ClassGate.CONTROL_URL, ClassGate.CONTROL_EDIT, ClassGate.toJson(a));
            ClassGate.ui(new PullControlOk(h, cb, a));
        } catch (Exception e) {
            ClassGate.ui(new GateFail(h, cb, e.getMessage(), true));
        }
    }
}

final class PullRosterJob implements Runnable {
    final ClassGate.Holder h;
    final ClassGate.Cb cb;
    PullRosterJob(ClassGate.Holder h, ClassGate.Cb cb) { this.h = h; this.cb = cb; }
    @Override public void run() {
        try {
            String html = ClassGate.fetchPage(ClassGate.ROSTER_URL);
            String json = ClassGate.extractJson(html);
            ClassGate.ui(new PullRosterOk(h, cb, ClassGate.rosterFromJson(json)));
        } catch (Exception e) {
            ClassGate.ui(new GateFail(h, cb, e.getMessage(), false));
        }
    }
}

final class PullRosterOk implements Runnable {
    final ClassGate.Holder h;
    final ClassGate.Cb cb;
    final ArrayList<ClassStudent> list;
    PullRosterOk(ClassGate.Holder h, ClassGate.Cb cb, ArrayList<ClassStudent> list) {
        this.h = h; this.cb = cb; this.list = list;
    }
    @Override public void run() {
        h.roster = list;
        if (cb != null) cb.done(true, null);
    }
}

final class CheckInJob implements Runnable {
    final ClassGate.Holder h;
    final String id;
    final String name;
    final ClassGate.Cb cb;
    CheckInJob(ClassGate.Holder h, String id, String name, ClassGate.Cb cb) {
        this.h = h; this.id = id; this.name = name; this.cb = cb;
    }
    @Override public void run() {
        try {
            String html = ClassGate.fetchPage(ClassGate.ROSTER_URL);
            String json = ClassGate.extractJson(html);
            ArrayList<ClassStudent> list = ClassGate.rosterFromJson(json);
            boolean found = false;
            long now = System.currentTimeMillis();
            for (int i = 0; i < list.size(); i++) {
                if (id.equals(list.get(i).id)) {
                    list.get(i).name = name;
                    list.get(i).lastSeen = now;
                    found = true;
                    break;
                }
            }
            if (!found) {
                ClassStudent s = new ClassStudent();
                s.id = id;
                s.name = name;
                s.lastSeen = now;
                list.add(s);
            }
            ClassGate.editPage(ClassGate.ROSTER_URL, ClassGate.ROSTER_EDIT, ClassGate.rosterToJson(list));
            ClassGate.ui(new PullRosterOk(h, cb, list));
        } catch (Exception e) {
            ClassGate.ui(new GateFail(h, cb, e.getMessage(), false));
        }
    }
}

final class GateFail implements Runnable {
    final ClassGate.Holder h;
    final ClassGate.Cb cb;
    final String error;
    final boolean markOffline;
    GateFail(ClassGate.Holder h, ClassGate.Cb cb, String error, boolean markOffline) {
        this.h = h; this.cb = cb; this.error = error; this.markOffline = markOffline;
    }
    @Override public void run() {
        if (markOffline) {
            h.online = false;
            h.lastError = error;
        }
        if (cb != null) cb.done(false, error);
    }
}
