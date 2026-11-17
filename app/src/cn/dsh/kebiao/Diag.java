package cn.dsh.kebiao;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** beta 版用的简易诊断日志：内存 + 缓存文件，保留最近 60KB */
public class Diag {
    private static final StringBuffer BUF = new StringBuffer();
    private static final int MAX = 60000;
    private static File file;

    public static synchronized void init(Context c) {
        if (file != null) return;
        try {
            file = new File(c.getCacheDir(), "diag.log");
            if (file.exists() && file.length() > 0) {
                byte[] b = new byte[(int) file.length()];
                FileInputStream in = new FileInputStream(file);
                int n = in.read(b);
                in.close();
                if (n > 0) BUF.append(new String(b, 0, n, "UTF-8"));
            }
        } catch (Throwable ignored) {
        }
    }

    public static synchronized void add(String tag, String msg) {
        String line = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())
                + " [" + tag + "] " + msg + "\n";
        BUF.append(line);
        if (BUF.length() > MAX) BUF.delete(0, BUF.length() - MAX);
        if (file != null) {
            try {
                FileOutputStream out = new FileOutputStream(file, false);
                out.write(BUF.toString().getBytes("UTF-8"));
                out.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public static synchronized void addErr(String tag, String what, Throwable t) {
        StringBuilder sb = new StringBuilder(what);
        if (t != null) {
            sb.append(": ").append(t.getClass().getSimpleName()).append(" ").append(t.getMessage());
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < st.length && i < 6; i++) sb.append("\n    at ").append(st[i]);
        }
        add(tag, sb.toString());
    }

    public static synchronized String dump() {
        return BUF.length() == 0 ? "(空)" : BUF.toString();
    }

    public static synchronized void clear() {
        BUF.setLength(0);
        if (file != null) file.delete();
    }
}
