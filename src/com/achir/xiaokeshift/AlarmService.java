package com.achir.xiaokeshift;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * 鬧鐘響鈴（前景服務）：播鈴聲、震動、發全螢幕通知開 AlarmActivity；通知上有「貪睡」「關閉」。
 * 10 分鐘沒人理自動停止（原版 AlarmRingingService / AlarmClockNotificationService）。
 */
public final class AlarmService extends Service {
    static final String CHANNEL = "alarm", ACTION_SNOOZE = "snooze", ACTION_DISMISS = "dismiss";
    private static final int NOTIFY_ID = 7;
    static volatile boolean ringing;
    private MediaPlayer player;
    private Vibrator vib;
    private final Handler h = new Handler(Looper.getMainLooper());
    private String day = "", profile = Store.ME;

    @Override
    public IBinder onBind(Intent i) { return null; }

    static void channel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "小柯班表助手鬧鐘", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);       // 聲音由服務自己播
        ch.setBypassDnd(true);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        String act = i == null ? null : i.getAction();
        if (ACTION_SNOOZE.equals(act) || ACTION_DISMISS.equals(act)) {
            if (ACTION_SNOOZE.equals(act)) AlarmScheduler.snooze(this, i.getStringExtra("day"), i.getStringExtra("profile"));
            stopSelf();
            return START_NOT_STICKY;
        }
        if (i != null) {
            day = i.getStringExtra("day") == null ? "" : i.getStringExtra("day");
            profile = i.getStringExtra("profile") == null ? Store.ME : i.getStringExtra("profile");
        }
        channel(this);
        Notification n = notification();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIFY_ID, n);
        ring();
        h.postDelayed(this::stopSelf, 10 * 60000L);
        if (Store.get(this).flag("fullScreen", true)) {
            try { startActivity(activityIntent()); } catch (Exception ignored) { }   // 背景限制時靠全螢幕通知
        }
        return START_NOT_STICKY;
    }

    private Intent activityIntent() {
        return new Intent(this, AlarmActivity.class).putExtra("day", day).putExtra("profile", profile)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
    }

    private PendingIntent action(String act, int req) {
        Intent i = new Intent(this, AlarmService.class).setAction(act).putExtra("day", day).putExtra("profile", profile);
        return PendingIntent.getService(this, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification notification() {
        Store s = Store.get(this);
        PendingIntent full = PendingIntent.getActivity(this, 3, activityIntent(), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("小柯叫你起床囉")
                .setContentText(s.profileName(profile) + "：" + s.describe(profile, day))
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true)
                .setContentIntent(full)
                .addAction(new Notification.Action.Builder(null, "關閉", action(ACTION_DISMISS, 4)).build());
        if (!s.flag("disableSnooze", false))
            b.addAction(new Notification.Action.Builder(null, "貪睡 " + s.num("snoozeMin", 5) + " 分", action(ACTION_SNOOZE, 5)).build());
        if (s.flag("fullScreen", true)) b.setFullScreenIntent(full, true);
        return b.build();
    }

    private void ring() {
        Store s = Store.get(this);
        ringing = true;
        try {
            String u = s.pref("alarmSound", "");
            Uri uri = u.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : Uri.parse(u);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int vol = s.num("alarmVolume", -1);
            if (vol >= 0) am.setStreamVolume(AudioManager.STREAM_ALARM,
                    Math.round(am.getStreamMaxVolume(AudioManager.STREAM_ALARM) * vol / 100f), 0);
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception ignored) { }
        if (s.flag("vibrate", true)) {
            vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            long[] pat = {0, 800, 600};
            if (vib != null) {
                if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createWaveform(pat, 0));
                else vib.vibrate(pat, 0);
            }
        }
    }

    @Override
    public void onDestroy() {
        ringing = false;
        h.removeCallbacksAndMessages(null);
        if (player != null) { try { player.stop(); } catch (Exception ignored) { } player.release(); }
        if (vib != null) vib.cancel();
        stopForeground(true);
        sendBroadcast(new Intent(AlarmActivity.ACTION_CLOSE).setPackage(getPackageName()));
        super.onDestroy();
    }
}
