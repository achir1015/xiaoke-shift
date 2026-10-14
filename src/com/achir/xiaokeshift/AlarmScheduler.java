package com.achir.xiaokeshift;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/**
 * 依班別鬧鐘設定排下一次鬧鐘（原版 alarmclock 模組）：往後找 45 天，取最近的一個，
 * 用 setAlarmClock（準時、狀態列顯示鬧鐘圖示、省電模式也會響）。每次班表或設定改變都重排。
 */
final class AlarmScheduler {
    static final String ACTION_ALARM = "com.achir.xiaokeshift.ALARM";

    static final class Next {
        long at;
        String day, profile;
    }

    static Next find(Context c) {
        Store s = Store.get(c);
        long now = System.currentTimeMillis();
        Next best = null;
        Calendar day = Calendar.getInstance();
        day.add(Calendar.DAY_OF_MONTH, -1);         // 「前日」鬧鐘：明天的班可能今天就要響
        for (int i = 0; i < 46; i++, day.add(Calendar.DAY_OF_MONTH, 1)) {
            for (String prof : Store.JOBS) {
                if (!prof.equals(Store.ME) && s.flag("ignoreJob2Alarm", false)) continue;
                String k = Store.key(day);
                ShiftType t = s.classify(s.code(prof, k));
                if (t == null || !t.alarmOn) continue;
                int min = ShiftType.minutes(t.alarmTime);
                if (min < 0) continue;
                Calendar at = (Calendar) day.clone();
                at.set(Calendar.HOUR_OF_DAY, min / 60);
                at.set(Calendar.MINUTE, min % 60);
                at.set(Calendar.SECOND, 0);
                at.set(Calendar.MILLISECOND, 0);
                if (t.alarmBefore) at.add(Calendar.DAY_OF_MONTH, -1);
                long ms = at.getTimeInMillis();
                if (ms <= now + 1000) continue;
                if (best == null || ms < best.at) {
                    best = new Next();
                    best.at = ms;
                    best.day = k;
                    best.profile = prof;
                }
            }
        }
        return best;
    }

    private static PendingIntent pi(Context c, int req, String day, String profile) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION_ALARM)
                .putExtra("day", day).putExtra("profile", profile);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean canExact(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
    }

    private static void set(Context c, long at, PendingIntent p) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent show = PendingIntent.getActivity(c, 9, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        if (canExact(c)) am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), p);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);   // 沒有「鬧鐘和提醒」權限時退而求其次
    }

    /** 重排下一次鬧鐘；回傳說明文字。 */
    static String schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p = pi(c, 1, "", "");
        am.cancel(p);
        Next n = find(c);
        if (n == null) return "沒有設定鬧鐘的班別";
        set(c, n.at, pi(c, 1, n.day, n.profile));
        return "下次喚醒時間：" + format(n.at);
    }

    static void snooze(Context c, String day, String profile) {
        int min = Store.get(c).num("snoozeMin", 5);
        set(c, System.currentTimeMillis() + min * 60000L, pi(c, 2, day, profile));
    }

    static String format(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return (c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.DAY_OF_MONTH) + "（" + XiaoKeServer.WEEK[c.get(Calendar.DAY_OF_WEEK) - 1]
                + "）" + String.format(java.util.Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }
}
