package com.codeaque.launcher;

import java.util.ArrayList;

/** Live class switch as stored on the free class page and cached on the phone. */
public final class ClassAccess {
    public String classCode = "";
    /** @deprecated Teacher PIN never leaves the phone. Kept only to ignore old pages. */
    public String pinHash = "";
    public String studentPinHash = "";
    public String studentPinSalt = "";
    public boolean classOn;
    public long classUntil;
    public boolean homeUseAll;
    public long homeUntil;
    public final ArrayList<String> homeAllow = new ArrayList<String>();
    public long updatedAt;
    public ClassLesson.Lesson lesson;

    public boolean classActive(long now) {
        if (!classOn) return false;
        return classUntil <= 0 || now < classUntil;
    }

    public boolean homeAllActive(long now) {
        if (!homeUseAll) return false;
        return homeUntil <= 0 || now < homeUntil;
    }

    public boolean homeAllowedFor(String studentId, long now) {
        if (studentId == null || studentId.isEmpty()) return false;
        if (homeUntil > 0 && now >= homeUntil) return false;
        for (int i = 0; i < homeAllow.size(); i++) {
            if (studentId.equals(homeAllow.get(i))) return true;
        }
        return false;
    }

    public void setHome(String studentId, boolean on) {
        if (studentId == null || studentId.isEmpty()) return;
        int at = homeAllow.indexOf(studentId);
        if (on && at < 0) homeAllow.add(studentId);
        if (!on && at >= 0) homeAllow.remove(at);
    }
}
