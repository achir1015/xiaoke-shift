package com.achir.xiaokeshift;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.UUID;

/**
 * 備註記錄（某一天）：文字說明、心情圖示、超連結、插入圖片、插入 Google 地圖。
 * 圖片縮到長邊 1600 px 存在 App 私有資料夾 files/notes/，相簿刪掉原圖也不影響。
 * 地圖用 Google 地圖嵌入網址（不需金鑰）顯示，點一下開 Google 地圖 App。
 */
public final class NoteActivity extends Activity {
    static final String[] MOODS = {"😀", "😊", "🥰", "😌", "😐", "😴", "😢", "😡", "🤒", "😷", "🥳", "💪", "☕", "🎉"};
    private static final int REQ_IMAGE = 1;

    private Store st;
    private String profile, key;
    private JSONObject note;
    private EditText text;
    private LinearLayout moods, links, images, places;
    private float dp;
    private boolean dark;

    static Intent intent(Activity a, String profile, String key) {
        return new Intent(a, NoteActivity.class).putExtra("profile", profile).putExtra("date", key);
    }

    @Override
    protected void onCreate(Bundle b) {
        st = Store.get(this);
        dark = st.flag("dark", false);
        if (dark) setTheme(android.R.style.Theme_Material);
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        profile = getIntent().getStringExtra("profile");
        key = getIntent().getStringExtra("date");
        if (profile == null) profile = Store.ME;
        if (key == null) key = Store.key(Calendar.getInstance());
        note = st.noteObj(profile, key);
        Calendar c = Store.parse(key);
        setTitle("備註記錄　" + (c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.DAY_OF_MONTH)
                + "（" + XiaoKeServer.WEEK[c.get(Calendar.DAY_OF_WEEK) - 1] + "）");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (14 * dp);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(dark ? 0xFF101522 : 0xFFF1F3F6);

        TextView head = new TextView(this);
        head.setText(st.profileName(profile) + "：" + st.describe(profile, key) + "\n農曆" + Almanac.lunarString(c));
        head.setTextColor(dark ? 0xFFB0BEC5 : 0xFF546E7A);
        root.addView(head);

        section(root, "😊 心情");
        HorizontalScrollView hs = new HorizontalScrollView(this);
        moods = new LinearLayout(this);
        hs.addView(moods);
        root.addView(hs);

        section(root, "📝 文字說明（網址可直接點開）");
        text = new EditText(this);
        text.setText(note.optString("text"));
        text.setMinLines(4);
        text.setGravity(Gravity.TOP);
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        text.setBackground(card());
        text.setPadding(pad, pad, pad, pad);
        text.setTextColor(dark ? Color.WHITE : 0xFF212121);
        root.addView(text);
        final TextView urls = new TextView(this);      // 文字裡的網址，點了直接開
        urls.setTextSize(14);
        urls.setPadding((int) (4 * dp), (int) (4 * dp), 0, 0);
        root.addView(urls);
        Runnable showUrls = () -> {
            java.util.regex.Matcher mt = android.util.Patterns.WEB_URL.matcher(text.getText());
            StringBuilder sb = new StringBuilder();
            while (mt.find()) sb.append(sb.length() == 0 ? "" : "\n").append(mt.group());
            urls.setText(sb);
            linkify(urls);
            urls.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
        };
        showUrls.run();
        text.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c2) { }
            public void onTextChanged(CharSequence s, int a, int b2, int c2) { }
            public void afterTextChanged(android.text.Editable e) { showUrls.run(); }
        });

        section(root, "🔗 超連結");
        links = new LinearLayout(this);
        links.setOrientation(LinearLayout.VERTICAL);
        root.addView(links);
        root.addView(addButton("＋ 加入連結", v -> addLink()));

        section(root, "🖼️ 圖片（點一下放大，長按刪除）");
        HorizontalScrollView is = new HorizontalScrollView(this);
        images = new LinearLayout(this);
        is.addView(images);
        root.addView(is);
        root.addView(addButton("＋ 插入圖片", v -> pickImage()));

        section(root, "🗺️ Google 地圖（點地圖開啟導航，長按刪除）");
        places = new LinearLayout(this);
        places.setOrientation(LinearLayout.VERTICAL);
        root.addView(places);
        root.addView(addButton("＋ 插入地圖", v -> addPlace()));

        Button save = addButton("儲存", v -> { save(); finish(); });
        save.setBackgroundColor(0xFF1E88E5);
        save.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int) (52 * dp));
        lp.topMargin = (int) (20 * dp);
        root.addView(save, lp);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
        renderAll();
    }

    private GradientDrawable card() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(dark ? 0xFF1E2433 : Color.WHITE);
        g.setCornerRadius(10 * dp);
        return g;
    }

    private void section(LinearLayout root, String title) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(dark ? Color.WHITE : 0xFF101522);
        t.setPadding(0, (int) (16 * dp), 0, (int) (6 * dp));
        root.addView(t);
    }

    private Button addButton(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private JSONArray arr(String k) {
        JSONArray a = note.optJSONArray(k);
        if (a == null) { a = new JSONArray(); try { note.put(k, a); } catch (Exception ignored) { } }
        return a;
    }

    private static JSONArray without(JSONArray a, int idx) {
        JSONArray b = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (i != idx) b.put(a.opt(i));
        return b;
    }

    private void renderAll() { renderMoods(); renderLinks(); renderImages(); renderPlaces(); }

    // ---------------------------------------------------------------- 心情
    private void renderMoods() {
        moods.removeAllViews();
        String cur = note.optString("mood");
        for (final String m : MOODS) {
            TextView t = new TextView(this);
            t.setText(m);
            t.setTextSize(28);
            t.setGravity(Gravity.CENTER);
            int s = (int) (50 * dp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.rightMargin = (int) (4 * dp);
            if (m.equals(cur)) {
                GradientDrawable g = new GradientDrawable();
                g.setColor(0xFFFFE082);
                g.setCornerRadius(25 * dp);
                t.setBackground(g);
            }
            t.setOnClickListener(v -> {
                try { note.put("mood", m.equals(note.optString("mood")) ? "" : m); } catch (Exception ignored) { }
                renderMoods();
            });
            moods.addView(t, lp);
        }
    }

    // ---------------------------------------------------------------- 超連結
    private void renderLinks() {
        links.removeAllViews();
        final JSONArray a = arr("links");
        for (int i = 0; i < a.length(); i++) {
            final JSONObject o = a.optJSONObject(i);
            final int idx = i;
            TextView t = new TextView(this);
            String title = o.optString("title");
            t.setText("🔗 " + (title.isEmpty() ? o.optString("url") : title + "\n　" + o.optString("url")));
            t.setTextColor(0xFF1E88E5);
            t.setTextSize(15);
            t.setBackground(card());
            t.setPadding((int) (12 * dp), (int) (10 * dp), (int) (12 * dp), (int) (10 * dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int) (6 * dp);
            t.setOnClickListener(v -> open(o.optString("url")));
            t.setOnLongClickListener(v -> {
                confirmDelete("刪除這個連結？", () -> { try { note.put("links", without(a, idx)); } catch (Exception ignored) { } renderLinks(); });
                return true;
            });
            links.addView(t, lp);
        }
    }

    private void addLink() {
        LinearLayout box = Ui.box(this);
        final EditText title = Ui.field(box, "標題（可空白）", "", InputType.TYPE_CLASS_TEXT);
        final EditText url = Ui.field(box, "網址", "https://", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("加入連結").setView(box)
                .setPositiveButton("加入", (d, w) -> {
                    String u = url.getText().toString().trim();
                    if (u.isEmpty() || u.equals("https://")) return;
                    if (!u.contains("://")) u = "https://" + u;
                    try { arr("links").put(new JSONObject().put("title", title.getText().toString().trim()).put("url", u)); } catch (Exception ignored) { }
                    renderLinks();
                }).setNegativeButton("取消", null).show();
    }

    private void open(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "無法開啟：" + url, Toast.LENGTH_SHORT).show(); }
    }

    // ---------------------------------------------------------------- 圖片
    static File imageDir(Activity a) {
        File d = new File(a.getFilesDir(), "notes");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private void renderImages() {
        images.removeAllViews();
        final JSONArray a = arr("images");
        for (int i = 0; i < a.length(); i++) {
            final File f = new File(imageDir(this), a.optString(i));
            final int idx = i;
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(decode(f, 300));
            int s = (int) (110 * dp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.rightMargin = (int) (6 * dp);
            iv.setOnClickListener(v -> showImage(f));
            iv.setOnLongClickListener(v -> {
                confirmDelete("刪除這張圖片？", () -> {
                    f.delete();
                    try { note.put("images", without(a, idx)); } catch (Exception ignored) { }
                    renderImages();
                });
                return true;
            });
            images.addView(iv, lp);
        }
    }

    private static Bitmap decode(File f, int max) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    private void showImage(File f) {
        ImageView iv = new ImageView(this);
        iv.setAdjustViewBounds(true);
        iv.setImageBitmap(decode(f, 1600));
        new AlertDialog.Builder(this).setView(iv).setPositiveButton("關閉", null).show();
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, REQ_IMAGE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req != REQ_IMAGE || res != RESULT_OK || data == null) return;
        if (data.getClipData() != null)
            for (int i = 0; i < data.getClipData().getItemCount(); i++) importImage(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null) importImage(data.getData());
        renderImages();
    }

    /** 讀入圖片、縮到長邊 1600 px、存成 JPEG。 */
    private void importImage(Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= 1600) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            Bitmap bm;
            try (InputStream in = getContentResolver().openInputStream(uri)) { bm = BitmapFactory.decodeStream(in, null, o2); }
            if (bm == null) throw new Exception("不是圖片");
            String name = UUID.randomUUID().toString() + ".jpg";
            try (FileOutputStream out = new FileOutputStream(new File(imageDir(this), name))) { bm.compress(Bitmap.CompressFormat.JPEG, 85, out); }
            arr("images").put(name);
        } catch (Exception e) {
            Toast.makeText(this, "插入圖片失敗：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- Google 地圖
    private void renderPlaces() {
        places.removeAllViews();
        final JSONArray a = arr("places");
        for (int i = 0; i < a.length(); i++) {
            final String name = a.optJSONObject(i).optString("name");
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setBackground(card());
            item.setPadding((int) (8 * dp), (int) (8 * dp), (int) (8 * dp), (int) (8 * dp));
            TextView t = new TextView(this);
            t.setText("📍 " + name + "　（點這裡開 Google 地圖）");
            t.setTextColor(0xFF1E88E5);
            t.setTextSize(15);
            t.setPadding(0, 0, 0, (int) (6 * dp));
            item.addView(t);
            WebView wv = new WebView(this);
            WebSettings ws = wv.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView v, String url) { openMap(name); return true; }
            });
            String q;
            try { q = URLEncoder.encode(name, "UTF-8"); } catch (Exception e) { q = name; }
            wv.loadUrl("https://maps.google.com/maps?q=" + q + "&z=15&hl=zh-TW&output=embed");
            item.addView(wv, new LinearLayout.LayoutParams(-1, (int) (200 * dp)));
            View.OnClickListener go = v -> openMap(name);
            View.OnLongClickListener del = v -> {
                confirmDelete("刪除這個地點？", () -> { try { note.put("places", without(a, idx)); } catch (Exception ignored) { } renderPlaces(); });
                return true;
            };
            t.setOnClickListener(go);
            t.setOnLongClickListener(del);
            item.setOnLongClickListener(del);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = (int) (8 * dp);
            places.addView(item, lp);
        }
    }

    private void addPlace() {
        LinearLayout box = Ui.box(this);
        final EditText name = Ui.field(box, "地名或地址（例如：台北車站、醫院名稱）", "", InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("插入地圖").setView(box)
                .setPositiveButton("插入", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) return;
                    try { arr("places").put(new JSONObject().put("name", n)); } catch (Exception ignored) { }
                    renderPlaces();
                }).setNegativeButton("取消", null).show();
    }

    private void openMap(String name) {
        try {
            String q = URLEncoder.encode(name, "UTF-8");
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + q)).setPackage("com.google.android.apps.maps");
            if (i.resolveActivity(getPackageManager()) == null)
                i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + q));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "無法開啟地圖", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- 共用
    private void confirmDelete(String msg, Runnable r) {
        new AlertDialog.Builder(this).setMessage(msg).setPositiveButton("刪除", (d, w) -> r.run())
                .setNegativeButton("取消", null).show();
    }

    private void save() {
        try { note.put("text", text.getText().toString().trim()); } catch (Exception ignored) { }
        st.setNoteObj(profile, key, note);
        st.save();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        m.add(0, 1, 0, "儲存").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) { save(); finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {   // 返回鍵也自動儲存
        save();
        super.onBackPressed();
    }

    /** 給月曆以外的地方用：把備註文字裡的網址變成可點的連結。 */
    static void linkify(TextView t) { Linkify.addLinks(t, Linkify.WEB_URLS); }
}
