package com.achir.xiaokeshift;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.Calendar;

/** 桌面小工具（小柯模式）：小柯臉＋今天班別＋明天班別＋農曆。 */
public final class XiaoKeWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, views(c));
    }

    /** 班表改了就呼叫，讓桌面小工具立刻更新。 */
    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, XiaoKeWidget.class));
        for (int id : ids) m.updateAppWidget(id, views(c));
    }

    private static RemoteViews views(Context c) {
        Store st = Store.get(c);
        Calendar today = Calendar.getInstance(), tm = Calendar.getInstance();
        tm.add(Calendar.DAY_OF_MONTH, 1);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        v.setTextViewText(R.id.w_date, (today.get(Calendar.MONTH) + 1) + "/" + today.get(Calendar.DAY_OF_MONTH)
                + "（" + XiaoKeServer.WEEK[today.get(Calendar.DAY_OF_WEEK) - 1] + "）農曆" + Almanac.lunarString(today));
        v.setTextViewText(R.id.w_today, "今天：" + st.describe(Store.ME, Store.key(today)));
        v.setTextViewText(R.id.w_tomorrow, "明天：" + st.describe(Store.ME, Store.key(tm)));
        ShiftType t = st.classify(st.code(Store.ME, Store.key(today)));
        v.setTextColor(R.id.w_today, t == null ? 0xFFFFFFFF : lighten(t.color));
        Intent open = new Intent(c, MainActivity.class);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    private static int lighten(int c) {   // 深底上看得清楚
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return 0xFF000000 | ((r + 255) / 2 << 16) | ((g + 255) / 2 << 8) | ((b + 255) / 2);
    }
}
