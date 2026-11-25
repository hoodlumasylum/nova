package com.codeaque.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Reads GitHub android-tagged releases and picks a newer Nova.apk. */
public final class NovaUpdate {
    public static final String REPO = "hoodlumasylum/nova";
    public static final String API = "https://api.github.com/repos/" + REPO + "/releases?per_page=20";
    public static final String UA = "NovaAndroid/1.0.1";

    public static final class Offer {
        public String tag = "";
        public String versionName = "";
        public int versionCode;
        public String apkUrl = "";
        public long apkBytes;
    }

    private NovaUpdate() {}

    public static Offer bestOffer(String currentName, int currentCode) {
        try {
            String body = get(API);
            if (body == null || body.isEmpty()) return null;
            JSONArray arr = new JSONArray(body);
            Offer best = null;
            int bestCode = currentCode;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject rel = arr.optJSONObject(i);
                if (rel == null || rel.optBoolean("draft", false) || rel.optBoolean("prerelease", false)) continue;
                String tag = rel.optString("tag_name", "");
                if (!isAndroidTag(tag)) continue;
                JSONObject asset = findApk(rel.optJSONArray("assets"));
                if (asset == null) continue;
                String url = asset.optString("browser_download_url", "");
                if (url.isEmpty()) continue;
                int code = versionCodeOf(tag);
                if (code <= currentCode && !isNewerName(versionNameOf(tag), currentName)) continue;
                if (code < bestCode) continue;
                if (code == bestCode && best != null) continue;
                Offer o = new Offer();
                o.tag = tag;
                o.versionName = versionNameOf(tag);
                o.versionCode = code;
                o.apkUrl = url;
                o.apkBytes = asset.optLong("size", 0);
                best = o;
                bestCode = code;
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isAndroidTag(String tag) {
        if (tag == null) return false;
        String t = tag.trim();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        return t.matches("\\d+\\.\\d+\\.\\d+-android");
    }

    public static String versionNameOf(String tag) {
        if (tag == null) return "";
        String t = tag.trim();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        int dash = t.toLowerCase(Locale.US).indexOf("-android");
        if (dash > 0) t = t.substring(0, dash);
        return t;
    }

    public static int versionCodeOf(String tag) {
        String name = versionNameOf(tag);
        String[] p = name.split("\\.");
        int a = 0, b = 0, c = 0;
        try { if (p.length > 0) a = Integer.parseInt(p[0]); } catch (Exception ignored) {}
        try { if (p.length > 1) b = Integer.parseInt(p[1]); } catch (Exception ignored) {}
        try { if (p.length > 2) c = Integer.parseInt(p[2]); } catch (Exception ignored) {}
        return a * 10000 + b * 100 + c;
    }

    public static boolean isNewerName(String offer, String current) {
        return versionCodeOf(offer + "-android") > versionCodeOf(current + "-android");
    }

    static JSONObject findApk(JSONArray assets) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            String name = a.optString("name", "");
            if ("Nova.apk".equalsIgnoreCase(name)) return a;
        }
        return null;
    }

    public static String get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(12000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            if (in == null) return null;
            if (code < 200 || code >= 300) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
