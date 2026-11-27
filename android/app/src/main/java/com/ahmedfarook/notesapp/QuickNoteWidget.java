package com.ahmedfarook.notesapp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.widget.RemoteViews;

public class QuickNoteWidget extends AppWidgetProvider {

    public static final String ACTION_NEW_NOTE = "com.ahmedfarook.notesapp.ACTION_NEW_NOTE";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        boolean isDark = TodoWidget.isDarkTheme(context);

        for (int appWidgetId : appWidgetIds) {
            Intent intent = new Intent(context, MainActivity.class);
            intent.setAction(ACTION_NEW_NOTE);
            intent.putExtra("action", "new_note");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, flags);

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_note);
            views.setInt(R.id.widget_quick_note_root, "setBackgroundResource",
                    isDark ? R.drawable.widget_quick_note_bg_dark : R.drawable.widget_quick_note_bg);
            views.setInt(R.id.widget_quick_note_icon, "setBackgroundResource",
                    isDark ? R.drawable.widget_circle_button_dark : R.drawable.widget_circle_button);
            views.setTextColor(R.id.widget_quick_note_label, Color.parseColor(isDark ? "#E8EEF3" : "#1C2B36"));
            views.setOnClickPendingIntent(R.id.widget_quick_note_root, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}
