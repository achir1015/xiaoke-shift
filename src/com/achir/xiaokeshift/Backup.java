package com.achir.xiaokeshift;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 備份／還原（原版「ZIP 備份檔」）：data.json＋notes/ 備註記錄的圖片。
 * 還原同時接受 ZIP 與第 1 版的純 JSON 備份。
 */
final class Backup {
    private Backup() { }

    static File imageDir(Context c) {
        File d = new File(c.getFilesDir(), "notes");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** 寫出 ZIP，回傳包含的圖片數。 */
    static int export(Context c, Uri uri) throws Exception {
        int n = 0;
        try (OutputStream os = c.getContentResolver().openOutputStream(uri);
             ZipOutputStream z = new ZipOutputStream(os)) {
            z.putNextEntry(new ZipEntry("data.json"));
            z.write(Store.get(c).exportAll().toString(2).getBytes("UTF-8"));
            z.closeEntry();
            File[] files = imageDir(c).listFiles();
            if (files != null)
                for (File f : files) {
                    if (!f.isFile()) continue;
                    z.putNextEntry(new ZipEntry("notes/" + f.getName()));
                    try (InputStream in = new FileInputStream(f)) { copy(in, z); }
                    z.closeEntry();
                    n++;
                }
        }
        return n;
    }

    /** 讀好但還沒套用的備份：資料＋暫存的圖片。 */
    static final class Pending {
        JSONObject data;
        File tmp;
        int images;
    }

    static Pending read(Context c, Uri uri) throws Exception {
        Pending p = new Pending();
        p.tmp = new File(c.getCacheDir(), "restore");
        deleteTree(p.tmp);
        p.tmp.mkdirs();
        try (InputStream raw = new BufferedInputStream(c.getContentResolver().openInputStream(uri))) {
            raw.mark(4);
            int b0 = raw.read(), b1 = raw.read();
            raw.reset();
            if (b0 == 'P' && b1 == 'K') {                       // ZIP
                ZipInputStream z = new ZipInputStream(raw);
                for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                    String name = e.getName();
                    if (e.isDirectory()) continue;
                    if (name.equals("data.json")) p.data = new JSONObject(readAll(z));
                    else if (name.startsWith("notes/")) {
                        String base = new File(name).getName();    // 只取檔名，避免路徑跳脫
                        if (base.isEmpty() || base.startsWith(".")) continue;
                        try (OutputStream out = new FileOutputStream(new File(p.tmp, base))) { copy(z, out); }
                        p.images++;
                    }
                }
            } else p.data = new JSONObject(readAll(raw));       // 第 1 版純 JSON
        }
        if (p.data == null) throw new Exception("這不是小柯班表助手的備份檔");
        return p;
    }

    /** 套用：資料取代現有資料，圖片寫回 notes/（同名覆蓋）。 */
    static void apply(Context c, Pending p) throws Exception {
        Store.get(c).importAll(p.data);
        File dir = imageDir(c);
        File[] files = p.tmp.listFiles();
        if (files != null)
            for (File f : files) {
                File dst = new File(dir, f.getName());
                if (!f.renameTo(dst))
                    try (InputStream in = new FileInputStream(f); OutputStream out = new FileOutputStream(dst)) { copy(in, out); }
            }
        deleteTree(p.tmp);
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        copy(in, bo);
        return bo.toString("UTF-8");
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] b = new byte[16384];
        for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
