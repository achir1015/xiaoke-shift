package com.achir.xiaokeshift;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * 月曆格子：日期、農曆／節氣／假日、班別色塊（原代號）、備註點、發薪日、Google 行事曆事件點。
 * 依設定：每週第一天、週數、今日樣式、字型大小、顯示班別時間、隱藏休假、週末紅字、深色。
 * 批次編輯模式：點選多天；長按從上一次點的日子選到這天（大範圍選取）。
 */
final class MonthView extends View {
    interface OnDayClick { void onDay(Calendar day); }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float dp;
    private int year, month;              // month: 0–11
    private String selected = "", profile = Store.ME, lastTapped = null;
    private OnDayClick listener;
    private boolean multi;
    final Set<String> picked = new HashSet<>();
    Set<String> eventDays = new HashSet<>();
    private int fixedHeight = -1;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean longFired;
    private float downX, downY;

    MonthView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH);
        selected = Store.key(now);
    }

    void setMonth(int y, int m) { year = y; month = m; requestLayout(); invalidate(); }
    void setSelected(String key) { selected = key; invalidate(); }
    void setProfile(String p) { profile = p; invalidate(); }
    void setOnDayClick(OnDayClick l) { listener = l; }
    void setMulti(boolean on) { multi = on; picked.clear(); lastTapped = null; invalidate(); }
    boolean isMulti() { return multi; }

    private Store st() { return Store.get(getContext()); }
    private int firstDow() { return st().num("firstDow", Calendar.SUNDAY); }
    private boolean weekNo() { return st().flag("weekNumber", false); }
    private boolean dark() { return st().flag("dark", false); }
    private float scale() { return st().num("fontScale", 100) / 100f; }

    private Calendar first() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, 1);
        return c;
    }

    private int lead(Calendar first) { return (first.get(Calendar.DAY_OF_WEEK) - firstDow() + 7) % 7; }

    private int rows() {
        Calendar c = first();
        return (lead(c) + c.getActualMaximum(Calendar.DAY_OF_MONTH) + 6) / 7;
    }

    private float left() { return weekNo() ? 18 * dp : 0; }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        if (fixedHeight > 0) { setMeasuredDimension(width, fixedHeight); return; }
        int cell = Math.max((int) (64 * dp), (int) ((width - left()) / 7));
        setMeasuredDimension(width, (int) (24 * dp) + cell * rows());
    }

    /** 給桌面月曆小工具：畫成指定大小的圖。 */
    Bitmap render(int w, int hgt) {
        fixedHeight = hgt;
        measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(hgt, MeasureSpec.EXACTLY));
        layout(0, 0, w, hgt);
        Bitmap b = Bitmap.createBitmap(w, hgt, Bitmap.Config.ARGB_8888);
        draw(new Canvas(b));
        return b;
    }

    private boolean weekend(int dow) { return dow == Calendar.SUNDAY || dow == Calendar.SATURDAY; }

    @Override
    protected void onDraw(Canvas cv) {
        Store st = st();
        boolean dk = dark();
        int cellBg = dk ? 0xFF1E2433 : 0xFFFFFFFF, text = dk ? 0xFFECEFF1 : 0xFF212121, sub = dk ? 0xFF90A4AE : 0xFF9E9E9E;
        int red = st.flag("weekendRed", true) ? 0xFFE53935 : text;
        float L = left(), W = (getWidth() - L) / 7f, head = 24 * dp, sc = scale();
        float H = (getHeight() - head) / rows();
        String[] names = {"日", "一", "二", "三", "四", "五", "六"};
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(13 * dp);
        p.setFakeBoldText(false);
        for (int i = 0; i < 7; i++) {
            int dow = (firstDow() - 1 + i) % 7 + 1;
            p.setColor(weekend(dow) ? red : sub);
            cv.drawText(names[dow - 1], L + W * i + W / 2, head - 7 * dp, p);
        }

        Calendar c = first();
        int lead = lead(c), max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        String today = Store.key(Calendar.getInstance());
        String todayStyle = st.pref("todayStyle", "border");
        boolean showTime = st.flag("showTime", false), hideOff = st.flag("hideOff", false);
        for (int d = 1; d <= max; d++) {
            c.set(Calendar.DAY_OF_MONTH, d);
            int idx = lead + d - 1, col = idx % 7, row = idx / 7;
            float x = L + W * col, y = head + H * row;
            String k = Store.key(c);
            int dow = c.get(Calendar.DAY_OF_WEEK);
            String hol = Holidays.name(st, k);

            if (weekNo() && (col == 0 || d == 1)) {
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(9 * dp);
                p.setColor(sub);
                Calendar wk = (Calendar) c.clone();
                wk.setFirstDayOfWeek(firstDow());
                wk.setMinimalDaysInFirstWeek(4);
                cv.drawText(String.valueOf(wk.get(Calendar.WEEK_OF_YEAR)), L / 2, y + H / 2, p);
            }

            r.set(x + 1.5f * dp, y + 1.5f * dp, x + W - 1.5f * dp, y + H - 1.5f * dp);
            p.setStyle(Paint.Style.FILL);
            p.setColor(picked.contains(k) ? 0xFFFFE082 : k.equals(selected) && !multi ? (dk ? 0xFF283593 : 0xFFE3F2FD) : cellBg);
            cv.drawRoundRect(r, 6 * dp, 6 * dp, p);
            if (k.equals(today) && todayStyle.equals("border")) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * dp);
                p.setColor(0xFF1E88E5);
                cv.drawRoundRect(r, 6 * dp, 6 * dp, p);
                p.setStyle(Paint.Style.FILL);
            }

            // 日期
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(14 * dp * sc);
            p.setFakeBoldText(true);
            float dx = x + 5 * dp, dy = y + 16 * dp * sc;
            if (k.equals(today) && todayStyle.equals("circle")) {
                p.setColor(0xFF1E88E5);
                cv.drawCircle(dx + p.measureText(String.valueOf(d)) / 2, dy - 5 * dp * sc, 10 * dp * sc, p);
                p.setColor(Color.WHITE);
            } else p.setColor(weekend(dow) || !hol.isEmpty() ? red : text);
            cv.drawText(String.valueOf(d), dx, dy, p);
            p.setFakeBoldText(false);

            // 農曆 / 節氣 / 假日
            String lunar = hol.isEmpty() ? Almanac.cellText(c) : hol;
            p.setTextAlign(Paint.Align.RIGHT);
            float ts = 9.5f * dp * sc;
            p.setTextSize(ts);
            while (p.measureText(lunar) > W - 26 * dp && ts > 6 * dp) p.setTextSize(ts -= dp / 2);
            p.setColor(!hol.isEmpty() ? 0xFFE53935 : !Almanac.lunarString(c).contains(lunar) ? 0xFF2E7D32
                    : lunar.endsWith("月") ? 0xFF1E88E5 : sub);
            cv.drawText(lunar, x + W - 4 * dp, y + 15 * dp, p);

            // 班別色塊
            String raw = st.code(profile, k);
            ShiftType t = st.classify(raw);
            if (!raw.isEmpty() && !(hideOff && t != null && t.off)) {
                boolean auto = st.explicit(profile, k).isEmpty();      // 自動重覆樣式產生的，畫淡一點
                int color = t == null ? 0xFF9E9E9E : t.color;
                float bh = Math.min(H * 0.42f, 26 * dp);
                r.set(x + 4 * dp, y + H - bh - 5 * dp, x + W - 4 * dp, y + H - 5 * dp);
                p.setColor(color);
                p.setAlpha(auto ? 150 : 255);
                cv.drawRoundRect(r, 5 * dp, 5 * dp, p);
                p.setAlpha(255);
                p.setColor(Color.WHITE);
                p.setTextAlign(Paint.Align.CENTER);
                p.setFakeBoldText(true);
                String label = showTime && t != null && !t.off ? raw + " " + t.start : raw;
                float bs = 13 * dp * sc;
                p.setTextSize(bs);
                while (p.measureText(label) > r.width() - 4 * dp && bs > 7 * dp) p.setTextSize(bs -= dp);
                cv.drawText(label, r.centerX(), r.centerY() + p.getTextSize() * 0.36f, p);
                p.setFakeBoldText(false);
            }
            // 備註紅點、發薪日 $、Google 事件藍點
            float iy = y + 25 * dp * sc, ix = x + 9 * dp;
            if (!st.note(profile, k).isEmpty()) {
                p.setColor(Color.parseColor(st.pref("noteColor", "#E53935")));
                cv.drawCircle(ix, iy, 3 * dp * (st.flag("bigNoteDot", false) ? 1.6f : 1f), p);
                ix += 9 * dp;
            }
            if (Holidays.isPayday(st, k)) {
                p.setColor(0xFF2E7D32);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(10 * dp);
                p.setFakeBoldText(true);
                cv.drawText("$", ix, iy + 3.5f * dp, p);
                p.setFakeBoldText(false);
                ix += 9 * dp;
            }
            if (eventDays.contains(k)) {
                p.setColor(0xFF1E88E5);
                cv.drawCircle(ix, iy, 3 * dp, p);
            }
        }
    }

    private Calendar dayAt(float ex, float ey) {
        float L = left(), W = (getWidth() - L) / 7f, head = 24 * dp, H = (getHeight() - head) / rows();
        if (ey < head || ex < L) return null;
        int col = (int) ((ex - L) / W), row = (int) ((ey - head) / H);
        Calendar c = first();
        int d = row * 7 + col - lead(c) + 1;
        if (d < 1 || d > c.getActualMaximum(Calendar.DAY_OF_MONTH)) return null;
        c.set(Calendar.DAY_OF_MONTH, d);
        return c;
    }

    private final Runnable longPress = () -> {
        longFired = true;
        Calendar c = dayAt(downX, downY);
        if (c == null || !multi) return;
        String k = Store.key(c);
        if (lastTapped == null) { picked.add(k); lastTapped = k; invalidate(); return; }
        Calendar a = Store.parse(lastTapped.compareTo(k) < 0 ? lastTapped : k);
        String end = lastTapped.compareTo(k) < 0 ? k : lastTapped;
        while (a != null && Store.key(a).compareTo(end) <= 0) { picked.add(Store.key(a)); a.add(Calendar.DAY_OF_MONTH, 1); }
        lastTapped = k;
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        invalidate();
        if (listener != null) listener.onDay(null);
    };

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                longFired = false;
                h.postDelayed(longPress, 500);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getX() - downX) > 20 * dp || Math.abs(e.getY() - downY) > 20 * dp) h.removeCallbacks(longPress);
                return true;
            case MotionEvent.ACTION_CANCEL:
                h.removeCallbacks(longPress);
                return true;
            case MotionEvent.ACTION_UP:
                h.removeCallbacks(longPress);
                if (longFired) return true;
                Calendar c = dayAt(e.getX(), e.getY());
                if (c == null) return true;
                performClick();
                if (multi) {
                    String k = Store.key(c);
                    if (!picked.remove(k)) picked.add(k);
                    lastTapped = k;
                    invalidate();
                    if (listener != null) listener.onDay(null);   // 通知更新選取數
                } else if (listener != null) listener.onDay(c);
                return true;
        }
        return true;
    }

    @Override
    public boolean performClick() { return super.performClick(); }
}
