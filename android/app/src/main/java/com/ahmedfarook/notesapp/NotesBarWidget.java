package com.ahmedfarook.notesapp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.widget.RemoteViews;

public class NotesBarWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        boolean isDark = TodoWidget.isDarkTheme(context);

        for (int appWidgetId : appWidgetIds) {
            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            Intent newNoteIntent = new Intent(context, MainActivity.class);
            newNoteIntent.setAction(QuickNoteWidget.ACTION_NEW_NOTE);
            newNoteIntent.putExtra("action", "new_note");
            newNoteIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent mainPending = PendingIntent.getActivity(context, 1, mainIntent, flags);
            PendingIntent newNotePending = PendingIntent.getActivity(context, 2, newNoteIntent, flags);

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_notes_bar);
            views.setInt(R.id.widget_notes_bar_root, "setBackgroundResource",
                    isDark ? R.drawable.widget_quick_note_bg_dark : R.drawable.widget_quick_note_bg);
            views.setTextColor(R.id.widget_notes_bar_brand, Color.parseColor(isDark ? "#7EAAD0" : "#213A50"));
            views.setTextColor(R.id.widget_notes_bar_sub, Color.parseColor(isDark ? "#8AA0B4" : "#5A6E82"));
            views.setInt(R.id.widget_bar_btn_new, "setBackgroundResource",
                    isDark ? R.drawable.widget_circle_button_dark : R.drawable.widget_circle_button);
            views.setOnClickPendingIntent(R.id.widget_notes_bar_root, mainPending);
            views.setOnClickPendingIntent(R.id.widget_bar_btn_new, newNotePending);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}
