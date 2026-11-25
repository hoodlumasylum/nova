package com.codeaque.launcher;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Checks GitHub for a newer Nova.apk and installs it with PackageInstaller. */
public final class NovaUpdateGo {
    public static final String ACTION_STATUS = "com.codeaque.nova.INSTALL_STATUS";
    static NovaUpdate.Offer pending;
    static long lastCheckAt;
    static boolean installing;

    private NovaUpdateGo() {}

    public static NovaUpdate.Offer pending() { return pending; }

    public static void check(MainActivity a) {
        long now = System.currentTimeMillis();
        if (now - lastCheckAt < 20_000L) return;
        lastCheckAt = now;
        new Thread(new CheckJob(a), "nova-update-check").start();
    }

    public static void startInstall(MainActivity a) {
        NovaUpdate.Offer o = pending;
        if (o == null || o.apkUrl.isEmpty()) {
            a.toast("No update is ready just now.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            if (!a.getPackageManager().canRequestPackageInstalls()) {
                a.toast("Android needs permission to install the update. Allow Nova to install apps, then try again.");
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    i.setData(Uri.parse("package:" + a.getPackageName()));
                    a.startActivity(i);
                } catch (Exception e) {
                    a.toast("Open Settings, then allow Nova to install unknown apps.");
                }
                return;
            }
        }
        if (installing) {
            a.toast("The update is already downloading.");
            return;
        }
        installing = true;
        a.toast("Downloading the update…");
        new Thread(new InstallJob(a, o), "nova-update-install").start();
    }

    public static void handle(MainActivity a, Intent intent) {
        if (intent == null || !ACTION_STATUS.equals(intent.getAction())) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = null;
            if (Build.VERSION.SDK_INT >= 33) {
                confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            if (confirm != null) {
                try {
                    a.startActivity(confirm);
                } catch (Exception e) {
                    a.toast("Please allow the Android installer to finish the update.");
                }
            }
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            a.toast("Update installed.");
            pending = null;
            return;
        }
        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        a.toast("The update could not be installed" + (msg == null || msg.isEmpty() ? "." : ": " + msg));
    }

    static boolean safeToRedraw(int screen) {
        return screen == MainActivity.S_ROLE
                || screen == MainActivity.S_TEACHER_HOME
                || screen == MainActivity.S_TEACHER_PIN
                || screen == MainActivity.S_TEACHER_STUDENTS
                || screen == MainActivity.S_STUDENT_JOIN
                || screen == MainActivity.S_STUDENT_HOME
                || screen == MainActivity.S_STUDENT_REST;
    }

    static final class CheckJob implements Runnable {
        final MainActivity a;
        CheckJob(MainActivity a) { this.a = a; }
        @Override public void run() {
            String name;
            int code;
            try {
                name = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
                code = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionCode;
            } catch (PackageManager.NameNotFoundException e) {
                return;
            }
            final NovaUpdate.Offer offer = NovaUpdate.bestOffer(name, code);
            if (offer == null) return;
            pending = offer;
            a.runOnUiThread(new ShowOffer(a, offer));
        }
    }

    static final class ShowOffer implements Runnable {
        final MainActivity a;
        final NovaUpdate.Offer offer;
        ShowOffer(MainActivity a, NovaUpdate.Offer offer) { this.a = a; this.offer = offer; }
        @Override public void run() {
            a.toast("Update ready. You will see a button on the home screen.");
            if (safeToRedraw(a.screen)) a.show();
        }
    }

    static final class InstallJob implements Runnable {
        final MainActivity a;
        final NovaUpdate.Offer offer;
        InstallJob(MainActivity a, NovaUpdate.Offer offer) { this.a = a; this.offer = offer; }
        @Override public void run() {
            String err = null;
            try {
                downloadAndCommit(a, offer);
            } catch (Exception e) {
                err = e.getMessage();
            }
            installing = false;
            if (err != null) {
                final String message = err;
                a.runOnUiThread(new InstallFail(a, message));
            }
        }
    }

    static void downloadAndCommit(Context c, NovaUpdate.Offer offer) throws Exception {
        HttpURLConnection conn = null;
        PackageInstaller.Session session = null;
        try {
            conn = (HttpURLConnection) new URL(offer.apkUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", NovaUpdate.UA);
            int http = conn.getResponseCode();
            if (http < 200 || http >= 300) throw new Exception("The download could not start.");
            long length = conn.getContentLength();
            if (length <= 0) length = offer.apkBytes;
            PackageInstaller installer = c.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(c.getPackageName());
            int sessionId = installer.createSession(params);
            session = installer.openSession(sessionId);
            InputStream in = conn.getInputStream();
            OutputStream out = session.openWrite("Nova.apk", 0, length > 0 ? length : -1);
            byte[] buf = new byte[16 * 1024];
            int n;
            long written = 0;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                written += n;
            }
            session.fsync(out);
            out.close();
            in.close();
            if (written < 1000) throw new Exception("The download was too small.");
            Intent callback = new Intent(c, MainActivity.class);
            callback.setAction(ACTION_STATUS);
            callback.putExtra("session", sessionId);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pi = PendingIntent.getActivity(c, sessionId, callback, flags);
            session.commit(pi.getIntentSender());
        } finally {
            if (session != null) session.close();
            if (conn != null) conn.disconnect();
        }
    }

    static final class InstallFail implements Runnable {
        final MainActivity a;
        final String message;
        InstallFail(MainActivity a, String message) { this.a = a; this.message = message; }
        @Override public void run() {
            a.toast("The update could not be downloaded. " + (message == null ? "" : message));
        }
    }
}
