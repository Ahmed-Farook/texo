package com.ahmedfarook.notesapp;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;

@CapacitorPlugin(name = "NotesWidget")
public class NotesWidgetPlugin extends Plugin {

    @PluginMethod
    public void setWidgetTheme(PluginCall call) {
        try {
            String theme = call.getString("theme", "light");
            boolean isDark = "dark".equals(theme);

            Context context = getContext();
            SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(TodoWidget.KEY_WIDGET_THEME, isDark ? "dark" : "light").apply();

            // Immediately update native Android status bar & navigation bar icon colors on UI thread
            final Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> MainActivity.applySystemBarAppearance(activity, isDark));
            }

            // Refresh all 3 home screen widgets to match active theme
            refreshAllWidgets(context);

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to set widget theme: " + e.getMessage(), e);
        }
    }

    private void refreshAllWidgets(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);

        int[] todoIds = mgr.getAppWidgetIds(new ComponentName(context, TodoWidget.class));
        if (todoIds != null && todoIds.length > 0) {
            mgr.notifyAppWidgetViewDataChanged(todoIds, R.id.widget_todo_listview);
            for (int id : todoIds) {
                TodoWidget.updateAppWidget(context, mgr, id);
            }
        }

        int[] quickIds = mgr.getAppWidgetIds(new ComponentName(context, QuickNoteWidget.class));
        if (quickIds != null && quickIds.length > 0) {
            Intent intent = new Intent(context, QuickNoteWidget.class);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, quickIds);
            context.sendBroadcast(intent);
        }

        int[] barIds = mgr.getAppWidgetIds(new ComponentName(context, NotesBarWidget.class));
        if (barIds != null && barIds.length > 0) {
            Intent intent = new Intent(context, NotesBarWidget.class);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, barIds);
            context.sendBroadcast(intent);
        }
    }

    @PluginMethod
    public void updateWidgetData(PluginCall call) {
        try {
            String noteId = call.getString("noteId", "");
            String title = call.getString("title", "To-Do List");
            JSArray itemsArray = call.getArray("items", new JSArray());

            Context context = getContext();
            SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            editor.putString(TodoWidget.KEY_NOTE_ID, noteId);
            editor.putString(TodoWidget.KEY_NOTE_TITLE, title);
            editor.putString(TodoWidget.KEY_ITEMS_JSON, itemsArray.toString());
            editor.apply();

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, TodoWidget.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

            if (appWidgetIds != null && appWidgetIds.length > 0) {
                Intent updateIntent = new Intent(context, TodoWidget.class);
                updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
                context.sendBroadcast(updateIntent);
            }

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to update widget data: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void syncAllChecklists(PluginCall call) {
        try {
            JSArray checklistsArray = call.getArray("checklists", new JSArray());
            Context context = getContext();
            SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(TodoWidget.KEY_ALL_CHECKLISTS_JSON, checklistsArray.toString()).apply();

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, TodoWidget.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            if (appWidgetIds != null && appWidgetIds.length > 0) {
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_todo_listview);
                for (int id : appWidgetIds) {
                    TodoWidget.updateAppWidget(context, appWidgetManager, id);
                }
            }

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to sync all checklists: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void getPendingWidgetOps(PluginCall call) {
        try {
            Context context = getContext();
            SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
            String opsStr = prefs.getString(TodoWidget.KEY_PENDING_WIDGET_OPS, "[]");

            JSONArray ops = new JSONArray(opsStr);
            JSArray retArray = new JSArray();
            for (int i = 0; i < ops.length(); i++) {
                retArray.put(ops.get(i));
            }

            prefs.edit().putString(TodoWidget.KEY_PENDING_WIDGET_OPS, "[]").apply();

            JSObject result = new JSObject();
            result.put("ops", retArray);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("Failed to get pending widget ops: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void getPendingToggles(PluginCall call) {
        try {
            Context context = getContext();
            SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
            String pendingStr = prefs.getString(TodoWidget.KEY_PENDING_TOGGLES, "[]");

            JSONArray pending = new JSONArray(pendingStr);
            JSArray retArray = new JSArray();
            for (int i = 0; i < pending.length(); i++) {
                retArray.put(pending.get(i));
            }

            prefs.edit().putString(TodoWidget.KEY_PENDING_TOGGLES, "[]").apply();

            JSObject result = new JSObject();
            result.put("toggles", retArray);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("Failed to get pending toggles: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void getPendingAdds(PluginCall call) {
        try {
            Context context = getContext();
            SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
            String pendingStr = prefs.getString(TodoWidget.KEY_PENDING_ADDS, "[]");

            JSONArray pending = new JSONArray(pendingStr);
            JSArray retArray = new JSArray();
            for (int i = 0; i < pending.length(); i++) {
                retArray.put(pending.get(i));
            }

            prefs.edit().putString(TodoWidget.KEY_PENDING_ADDS, "[]").apply();

            JSObject result = new JSObject();
            result.put("adds", retArray);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("Failed to get pending adds: " + e.getMessage(), e);
        }
    }
}
