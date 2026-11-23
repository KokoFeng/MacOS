package com.macos.launcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.view.Gravity;
import android.widget.FrameLayout;
import java.security.MessageDigest;

/** Verifies that this APK is signed with the original Feng-Dev key and is not debuggable. */
public class Guard {
    private static final String[] P = {"98cdfecaccb7cde44e3d944a17503e7c", "87c0482911a82dacfc4509027b9c2f3b"};

    public static boolean ok(Context c) {
        try {
            String pkg = c.getPackageName();
            if (!"com.macos.launcher".equals(pkg)) return false;
            if ((c.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) return false;
            PackageManager pm = c.getPackageManager();
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
                SigningInfo si = pi.signingInfo;
                if (si == null || si.hasMultipleSigners()) return false;
                sigs = si.getApkContentsSigners();
            } else {
                sigs = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures;
            }
            if (sigs == null || sigs.length != 1) return false;
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray());
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString().equals(P[0] + P[1]);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Replaces the whole UI with a blocking "modified" alert. */
    public static void block(final MainActivity a) {
        a.blocked = true;
        Theme.load(a);
        FrameLayout f = new FrameLayout(a);
        f.setBackgroundColor(0xFF000000);
        a.setContentView(f);
        MacAlert.show(a, f, "This App Has Been Modified",
                "This app has been modified and can no longer be used. Please get the original version from Feng-Dev / Feng iOS on Droid.",
                new String[]{"Quit"}, new Runnable[]{() -> a.finishAndRemoveTask()}, false);
    }
}
