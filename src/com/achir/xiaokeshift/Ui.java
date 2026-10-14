package com.achir.xiaokeshift;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 對話框用的小工具：直式容器、標籤＋輸入框、色塊按鈕。 */
final class Ui {
    static final int DECIMAL = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
    static final int TIME = InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME;
    static final int[] PALETTE = {0xFFFB8C00, 0xFF1E88E5, 0xFF5E35B1, 0xFF43A047, 0xFF00897B, 0xFFE53935,
            0xFFD81B60, 0xFF6D4C41, 0xFF546E7A, 0xFFFDD835, 0xFF00ACC1, 0xFF7CB342};

    static float dp(Context c) { return c.getResources().getDisplayMetrics().density; }

    static LinearLayout box(Context c) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * dp(c));
        b.setPadding(pad, (int) (8 * dp(c)), pad, 0);
        return b;
    }

    static ScrollView scroll(Context c, View v) {
        ScrollView s = new ScrollView(c);
        s.addView(v);
        return s;
    }

    static TextView label(LinearLayout box, String text) {
        TextView l = new TextView(box.getContext());
        l.setText(text);
        l.setTextSize(13);
        l.setPadding(0, (int) (8 * dp(box.getContext())), 0, 0);
        box.addView(l);
        return l;
    }

    static EditText field(LinearLayout box, String hint, String val, int type) {
        label(box, hint);
        EditText e = new EditText(box.getContext());
        e.setText(val);
        e.setTextSize(16);
        e.setInputType(type);
        e.setPadding(e.getPaddingLeft(), (int) (2 * dp(box.getContext())), e.getPaddingRight(), (int) (8 * dp(box.getContext())));
        box.addView(e);
        return e;
    }

    static CheckBox check(LinearLayout box, String text, boolean on) {
        CheckBox c = new CheckBox(box.getContext());
        c.setText(text);
        c.setChecked(on);
        box.addView(c);
        return c;
    }

    static float f(EditText e) {
        if (e == null) return 0;
        try { return Float.parseFloat(e.getText().toString().trim()); } catch (Exception x) { return 0; }
    }

    static int i(EditText e, int def) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception x) { return def; }
    }

    static Button colorButton(Context c, String text, int color) {
        float dp = dp(c);
        Button b = new Button(c);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(8 * dp);
        b.setBackground(g);
        // 頂端對齊（預設是基準線對齊，單行的「清除」會往下掉）
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, GridLayout.TOP), GridLayout.spec(GridLayout.UNDEFINED));
        lp.width = (int) (66 * dp);
        lp.height = (int) (54 * dp);
        lp.setMargins((int) (3 * dp), (int) (3 * dp), (int) (3 * dp), (int) (3 * dp));
        b.setLayoutParams(lp);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    /** 調色盤：點一下選顏色，回傳目前選的顏色（陣列第 0 格）。 */
    static GridLayout palette(Context c, final int[] color) {
        float dp = dp(c);
        GridLayout pal = new GridLayout(c);
        pal.setColumnCount(6);
        final java.util.List<View> sw = new java.util.ArrayList<>();
        for (final int col : PALETTE) {
            View v = new View(c);
            GradientDrawable g = new GradientDrawable();
            g.setColor(col);
            g.setCornerRadius(20 * dp);
            v.setBackground(g);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = lp.height = (int) (34 * dp);
            lp.setMargins((int) (4 * dp), (int) (4 * dp), (int) (4 * dp), (int) (4 * dp));
            v.setLayoutParams(lp);
            v.setTag(col);
            v.setOnClickListener(x -> {
                color[0] = col;
                for (View s : sw) s.setAlpha((int) s.getTag() == col ? 1f : 0.35f);
            });
            v.setAlpha(col == color[0] ? 1f : 0.35f);
            sw.add(v);
            pal.addView(v);
        }
        return pal;
    }
}
