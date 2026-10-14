package com.achir.xiaokeshift;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.util.Calendar;

/** 桌面月曆小工具（原版 BigWidgetProvider 4x4 / 5x5）：可切換上/下月，點月曆開 App。 */
public final class MonthWidget extends AppWidgetProvider {
    private static final String ACTION_MOVE = "com.achir.xiaokeshift.WIDGET_MOVE";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) { update(c, m, id); }

    @Override
    public void onReceive(Context c, Intent i) {
        if (ACTION_MOVE.equals(i.getAction())) {
            Store s = Store.get(c);
            int d = i.getIntExtra("delta", 0);
            s.setNum("widgetOffset", d == 0 ? 0 : s.num("widgetOffset", 0) + d);
            refresh(c);
            return;
        }
        super.onReceive(c, i);
    }

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int id : m.getAppWidgetIds(new ComponentName(c, MonthWidget.class))) update(c, m, id);
    }

    private static PendingIntent move(Context c, int delta) {
        Intent i = new Intent(c, MonthWidget.class).setAction(ACTION_MOVE).putExtra("delta", delta);
        return PendingIntent.getBroadcast(c, 100 + delta, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void update(Context c, AppWidgetManager m, int id) {
        Store s = Store.get(c);
        Calendar mon = Calendar.getInstance();
        mon.set(Calendar.DAY_OF_MONTH, 1);
        mon.add(Calendar.MONTH, s.num("widgetOffset", 0));
        float dp = c.getResources().getDisplayMetrics().density;
        Bundle o = m.getAppWidgetOptions(id);
        int wdp = Math.max(250, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250));
        int hdp = Math.max(220, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 250)) - 48;
        int w = Math.min((int) (wdp * dp), 1100), h = Math.min((int) (hdp * dp), 1100);

        MonthView mv = new MonthView(c);
        mv.setMonth(mon.get(Calendar.YEAR), mon.get(Calendar.MONTH));
        mv.setSelected("");
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_month);
        v.setTextViewText(R.id.m_title, mon.get(Calendar.YEAR) + " 年 " + (mon.get(Calendar.MONTH) + 1) + " 月");
        v.setImageViewBitmap(R.id.m_grid, mv.render(w, h));
        boolean buttons = s.flag("widgetButtons", true);
        int vis = buttons ? android.view.View.VISIBLE : android.view.View.GONE;
        v.setViewVisibility(R.id.m_prev, vis);
        v.setViewVisibility(R.id.m_today, vis);
        v.setViewVisibility(R.id.m_next, vis);
        v.setOnClickPendingIntent(R.id.m_prev, move(c, -1));
        v.setOnClickPendingIntent(R.id.m_next, move(c, 1));
        v.setOnClickPendingIntent(R.id.m_today, move(c, 0));
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.m_grid, open);
        v.setOnClickPendingIntent(R.id.m_face, open);
        m.updateAppWidget(id, v);
    }
}
