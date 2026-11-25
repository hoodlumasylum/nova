package com.codeaque.launcher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Typable class/home session codes. No Android types, so they can be checked
 * with plain Java. A start code carries its own expiry; the online switch can
 * still turn access off sooner when the phone can reach the class page.
 */
public final class ClassCodes {
    static final String ALPH = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final String SECRET_HEX = "99b361b1826aad14b90fe7c4c180f4fd3975de34ca449ab75fdd36456a8ffa1a";

    private ClassCodes() {}

    public static String randomClassCode() {
        SecureRandom r = new SecureRandom();
        char[] c = new char[4];
        for (int i = 0; i < 4; i++) c[i] = ALPH.charAt(r.nextInt(ALPH.length()));
        return new String(c);
    }

    /** 5 or 6 digits, first digit 1-9. Shown only on the teacher phone. */
    public static String randomStudentPin() {
        SecureRandom r = new SecureRandom();
        int len = r.nextBoolean() ? 5 : 6;
        char[] c = new char[len];
        c[0] = (char) ('1' + r.nextInt(9));
        for (int i = 1; i < len; i++) c[i] = (char) ('0' + r.nextInt(10));
        return new String(c);
    }

    public static boolean studentPinOk(String pin) {
        if (pin == null) return false;
        String p = pin.trim();
        if (p.length() < 5 || p.length() > 6) return false;
        for (int i = 0; i < p.length(); i++) {
            if (!Character.isDigit(p.charAt(i))) return false;
        }
        return true;
    }

    /**
     * Teacher PIN: letters and numbers, case-sensitive, 6 to 16 characters.
     * Leading and trailing spaces are ignored; the letters themselves are not changed.
     */
    public static boolean pinOk(String pin) {
        if (pin == null) return false;
        String p = pin.trim();
        if (p.length() < 6 || p.length() > 16) return false;
        for (int i = 0; i < p.length(); i++) {
            if (!Character.isLetterOrDigit(p.charAt(i))) return false;
        }
        return true;
    }

    public static String newSaltHex() {
        byte[] s = new byte[16];
        new SecureRandom().nextBytes(s);
        return hex(s);
    }

    /** SHA-256(salt bytes + PIN as typed). The salt is stored next to the hash. */
    public static String saltedPinHash(String pin, String saltHex) {
        if (pin == null || saltHex == null || saltHex.isEmpty()) return "";
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            d.update(hexBytes(saltHex));
            d.update(pin.trim().getBytes(StandardCharsets.UTF_8));
            return hex(d.digest());
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean saltedPinMatches(String pin, String saltHex, String hashHex) {
        String got = saltedPinHash(pin, saltHex);
        return hexEquals(got, hashHex);
    }

    /** Local-only check for a 1.0.0 on-phone hash. Never publish this form. */
    public static boolean legacyUnsaltedMatches(String pin, String storedHex) {
        if (pin == null || storedHex == null || storedHex.length() != 64) return false;
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            String got = hex(d.digest(pin.trim().getBytes(StandardCharsets.UTF_8)));
            return hexEquals(got, storedHex);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean hexEquals(String a, String b) {
        if (a == null || b == null || a.length() == 0 || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= Character.toLowerCase(a.charAt(i)) ^ Character.toLowerCase(b.charAt(i));
        }
        return r == 0;
    }

    /**
     * Class start code, e.g. C03P7KQ — class is on for 3 hours.
     * Home pass, e.g. H07P7KQ — home use for 7 days.
     */
    public static String make(boolean home, int amount, String classCode) {
        String type = home ? "H" : "C";
        int n = amount;
        if (home) {
            if (n < 1) n = 1;
            if (n > 14) n = 14;
        } else {
            if (n < 1) n = 1;
            if (n > 8) n = 8;
        }
        String body = type + two(n) + (classCode == null ? "" : classCode.trim());
        String sum = checksum(body).substring(0, 4);
        return type + two(n) + sum;
    }

    public static final class Grant {
        public final boolean home;
        public final int amount;
        public final long untilMs;
        public Grant(boolean home, int amount, long untilMs) {
            this.home = home;
            this.amount = amount;
            this.untilMs = untilMs;
        }
    }

    /** Returns a grant if the code matches this class, or null. */
    public static Grant parse(String code, String classCode, long nowMs) {
        if (code == null || classCode == null || classCode.isEmpty()) return null;
        String c = normalize(code);
        if (c.length() != 7) return null;
        char type = c.charAt(0);
        if (type != 'C' && type != 'H') return null;
        int amount;
        try { amount = Integer.parseInt(c.substring(1, 3)); }
        catch (Exception e) { return null; }
        String sum = c.substring(3);
        String body = type + two(amount) + classCode.trim();
        if (!checksum(body).substring(0, 4).equals(sum)) return null;
        boolean home = type == 'H';
        long span = home ? amount * 24L * 60L * 60L * 1000L : amount * 60L * 60L * 1000L;
        return new Grant(home, amount, nowMs + span);
    }

    public static String normalize(String code) {
        if (code == null) return "";
        StringBuilder b = new StringBuilder();
        String s = code.trim().toUpperCase();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == 'O') ch = '0';
            if (ch == 'I') ch = '1';
            if (Character.isLetterOrDigit(ch)) b.append(ch);
        }
        return b.toString();
    }

    static String two(int n) {
        if (n < 10) return "0" + n;
        return String.valueOf(n);
    }

    static String checksum(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hexBytes(SECRET_HEX), "HmacSHA256"));
            return hex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8))).toUpperCase();
        } catch (Exception e) {
            return "00000000";
        }
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (int i = 0; i < b.length; i++) {
            s.append(String.format("%02x", b[i] & 0xff));
        }
        return s.toString();
    }

    static byte[] hexBytes(String hex) {
        int n = hex.length() / 2;
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }
}
