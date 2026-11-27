package com.ahmedfarook.notesapp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

public class QuickNoteWidget extends AppWidgetProvider {

    public static final String ACTION_NEW_NOTE = "com.ahmedfarook.notesapp.ACTION_NEW_NOTE";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        boolean isLightFg = WidgetAppearance.isLightForeground(context);

        for (int appWidgetId : appWidgetIds) {
            Intent intent = new Intent(context, MainActivity.class);
            intent.setAction(ACTION_NEW_NOTE);
            intent.putExtra("action", "new_note");
            intent.setData(Uri.parse("texo://new-note/quick/" + appWidgetId));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, flags);

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_note);
            WidgetAppearance.applyWidgetContainer(context, views, R.id.widget_quick_bg_fill, R.id.widget_quick_bg_border);
            views.setInt(R.id.widget_quick_note_icon, "setBackgroundResource",
                    isLightFg ? R.drawable.widget_circle_button_dark : R.drawable.widget_circle_button);
            views.setTextColor(R.id.widget_quick_note_label, WidgetAppearance.primaryTextColor(isLightFg));
            views.setOnClickPendingIntent(R.id.widget_quick_note_root, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}
