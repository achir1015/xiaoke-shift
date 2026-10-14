package com.achir.xiaokeshift;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 鬧鐘時間到 → 啟動響鈴服務；開機／改時間／更新 App 後 → 重排鬧鐘並更新小工具（原版 BootCompleteReceiver）。 */
public final class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (AlarmScheduler.ACTION_ALARM.equals(i.getAction())) {
            Intent s = new Intent(c, AlarmService.class).putExtras(i);
            if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
        }
        AlarmScheduler.schedule(c);
        XiaoKeWidget.refresh(c);
        MonthWidget.refresh(c);
    }
}
