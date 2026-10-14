package com.achir.xiaokeshift;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/**
 * 鬧鐘畫面（小柯模式）：小柯臉、時間、今天的班；關閉後小柯用語音唸出今天的班＋農民曆。
 * 可選「翻面關閉鬧鐘」、「點對話框外面關閉」（原版 AlarmRingingDialog / AlarmClockDialogLockScreen）。
 */
public final class AlarmActivity extends Activity implements SensorEventListener {
    static final String ACTION_CLOSE = "com.achir.xiaokeshift.ALARM_CLOSED";
    private String day, profile;
    private SensorManager sm;
    private boolean faceUpSeen, done;
    private TextToSpeech tts;
    private final BroadcastReceiver closed = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) { if (!done) finish(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        day = getIntent().getStringExtra("day");
        profile = getIntent().getStringExtra("profile");
        if (day == null) day = Store.key(Calendar.getInstance());
        if (profile == null) profile = Store.ME;
        Store s = Store.get(this);
        float dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF101522);
        root.setPadding((int) (24 * dp), (int) (24 * dp), (int) (24 * dp), (int) (24 * dp));

        ImageView face = new ImageView(this);
        face.setImageResource(R.mipmap.ic_launcher);
        root.addView(face, new LinearLayout.LayoutParams((int) (160 * dp), (int) (160 * dp)));

        Calendar now = Calendar.getInstance();
        TextView time = text(String.format(Locale.US, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)), 56, Color.WHITE);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(time);
        root.addView(text("「小柯班表助手」鬧鐘提醒", 16, 0xFFB0BEC5));
        Calendar d = Store.parse(day);
        String when = d == null ? day : (d.get(Calendar.MONTH) + 1) + "/" + d.get(Calendar.DAY_OF_MONTH) + "（" + XiaoKeServer.WEEK[d.get(Calendar.DAY_OF_WEEK) - 1] + "）";
        TextView shift = text(s.profileName(profile) + "　" + when + "\n" + s.describe(profile, day), 22, 0xFFFFE082);
        shift.setPadding(0, (int) (16 * dp), 0, (int) (24 * dp));
        root.addView(shift);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        if (!s.flag("disableSnooze", false)) {
            Button sn = button("貪睡 " + s.num("snoozeMin", 5) + " 分", 0xFF546E7A, dp);
            sn.setOnClickListener(v -> stop(AlarmService.ACTION_SNOOZE));
            row.addView(sn);
        }
        Button off = button("關閉", 0xFFE53935, dp);
        off.setOnClickListener(v -> stop(AlarmService.ACTION_DISMISS));
        row.addView(off);
        root.addView(row);
        if (s.flag("flipDismiss", false)) root.addView(text("\n翻面（螢幕朝下）也可以關閉鬧鐘", 13, 0xFF90A4AE));
        setContentView(root);
        setFinishOnTouchOutside(s.flag("tapOutside", false));

        if (s.flag("flipDismiss", false)) {
            sm = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor acc = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (acc != null) sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(closed, new IntentFilter(ACTION_CLOSE), RECEIVER_NOT_EXPORTED);
        else registerReceiver(closed, new IntentFilter(ACTION_CLOSE));
    }

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private Button button(String s, int color, float dp) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(18);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(28 * dp);
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (130 * dp), (int) (56 * dp));
        lp.setMargins((int) (10 * dp), 0, (int) (10 * dp), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void stop(String action) {
        if (done) return;
        done = true;
        startService(new Intent(this, AlarmService.class).setAction(action).putExtra("day", day).putExtra("profile", profile));
        if (action.equals(AlarmService.ACTION_DISMISS) && Store.get(this).flag("xiaokeSpeak", true)) speak();
        else finish();
    }

    /** 小柯唸今天的班。 */
    private void speak() {
        final String msg = "早安！" + XiaoKeServer.say(Store.get(this), Store.parse(day) == null ? Calendar.getInstance() : Store.parse(day));
        tts = new TextToSpeech(this, st -> {
            if (st != TextToSpeech.SUCCESS) { finish(); return; }
            tts.setLanguage(Locale.TAIWAN);
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                public void onStart(String id) { }
                public void onDone(String id) { runOnUiThread(AlarmActivity.this::finish); }
                public void onError(String id) { runOnUiThread(AlarmActivity.this::finish); }
            });
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "xiaoke");
        });
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float z = e.values[2];
        if (z > 7) faceUpSeen = true;
        else if (z < -7 && faceUpSeen) stop(AlarmService.ACTION_DISMISS);
    }

    @Override
    public void onAccuracyChanged(Sensor s, int a) { }

    @Override
    public void onBackPressed() { /* 鬧鐘畫面不能用返回鍵關掉 */ }

    @Override
    protected void onDestroy() {
        if (sm != null) sm.unregisterListener(this);
        try { unregisterReceiver(closed); } catch (Exception ignored) { }
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
