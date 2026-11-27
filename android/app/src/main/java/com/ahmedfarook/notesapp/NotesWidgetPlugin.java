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

    public static void refreshAllWidgetsStatic(Context context) {
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

    private void refreshAllWidgets(Context context) {
        refreshAllWidgetsStatic(context);
    }

    @PluginMethod
    public void setWidgetAppearance(PluginCall call) {
        try {
            Context context = getContext();
            String preset = call.getString("preset", WidgetAppearance.getBgPreset(context));
            Integer opacityObj = call.getInt("opacity", WidgetAppearance.getOpacity(context));
            int opacity = opacityObj != null ? opacityObj : WidgetAppearance.getOpacity(context);
            String textMode = call.getString("textMode", WidgetAppearance.getTextMode(context));
            boolean showBorder = Boolean.TRUE.equals(call.getBoolean("showBorder", WidgetAppearance.isShowBorder(context)));

            WidgetAppearance.saveAppearance(context, preset, opacity, textMode, showBorder);
            refreshAllWidgets(context);

            JSObject ret = new JSObject();
            ret.put("preset", WidgetAppearance.getBgPreset(context));
            ret.put("opacity", WidgetAppearance.getOpacity(context));
            ret.put("textMode", WidgetAppearance.getTextMode(context));
            ret.put("showBorder", WidgetAppearance.isShowBorder(context));
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to save widget appearance: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void getWidgetAppearance(PluginCall call) {
        try {
            Context context = getContext();
            JSObject ret = new JSObject();
            ret.put("preset", WidgetAppearance.getBgPreset(context));
            ret.put("opacity", WidgetAppearance.getOpacity(context));
            ret.put("textMode", WidgetAppearance.getTextMode(context));
            ret.put("showBorder", WidgetAppearance.isShowBorder(context));
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to read widget appearance: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void setCloudConfig(PluginCall call) {
        try {
            String supabaseUrl = call.getString("supabaseUrl", "");
            String supabaseAnonKey = call.getString("supabaseAnonKey", "");
            String accessToken = call.getString("accessToken", "");
            String refreshToken = call.getString("refreshToken", "");
            Long expiresAtObj = call.getLong("expiresAt", 0L);
            long expiresAt = expiresAtObj != null ? expiresAtObj : 0L;
            String userId = call.getString("userId", "");
            String e2eeKey = call.getString("e2eeKey", "");

            WidgetCloudSyncHelper.saveCloudCredentials(
                getContext(),
                supabaseUrl,
                supabaseAnonKey,
                accessToken,
                refreshToken,
                expiresAt,
                userId,
                e2eeKey
            );

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to store cloud sync config: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void clearCloudConfig(PluginCall call) {
        try {
            WidgetCloudSyncHelper.clearCloudCredentials(getContext());
            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to clear cloud sync config: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void getRefreshedCloudSession(PluginCall call) {
        try {
            Context context = getContext();
            SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
            boolean refreshed = prefs.getBoolean(WidgetCloudSyncHelper.KEY_CLOUD_SESSION_REFRESHED, false);
            JSObject ret = new JSObject();
            ret.put("refreshed", refreshed);
            if (refreshed) {
                ret.put("accessToken", prefs.getString(WidgetCloudSyncHelper.KEY_CLOUD_ACCESS_TOKEN, ""));
                ret.put("refreshToken", prefs.getString(WidgetCloudSyncHelper.KEY_CLOUD_REFRESH_TOKEN, ""));
                prefs.edit().putBoolean(WidgetCloudSyncHelper.KEY_CLOUD_SESSION_REFRESHED, false).apply();
            }
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to get refreshed session: " + e.getMessage(), e);
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
    public synchronized void consumePendingLaunch(PluginCall call) {
        try {
            Context context = getContext();
            SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
            String action = prefs.getString(TodoWidget.KEY_PENDING_LAUNCH_ACTION, "");
            String noteId = prefs.getString(TodoWidget.KEY_PENDING_LAUNCH_NOTE_ID, "");

            if (!action.isEmpty() || !noteId.isEmpty()) {
                prefs.edit()
                    .remove(TodoWidget.KEY_PENDING_LAUNCH_ACTION)
                    .remove(TodoWidget.KEY_PENDING_LAUNCH_NOTE_ID)
                    .apply();
            }

            JSObject result = new JSObject();
            result.put("action", action);
            result.put("noteId", noteId);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("Failed to consume pending widget launch: " + e.getMessage(), e);
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
