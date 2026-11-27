package com.ahmedfarook.notesapp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

public class TodoWidget extends AppWidgetProvider {

    public static final String ACTION_TOGGLE_TODO = "com.ahmedfarook.notesapp.ACTION_TOGGLE_TODO";
    public static final String PREFS_NAME = "texo_widget_prefs";
    public static final String KEY_NOTE_ID = "widget_note_id";
    public static final String KEY_NOTE_TITLE = "widget_note_title";
    public static final String KEY_ITEMS_JSON = "widget_items_json";
    public static final String KEY_ALL_CHECKLISTS_JSON = "widget_all_checklists_json";
    public static final String KEY_PENDING_TOGGLES = "widget_pending_toggles";
    public static final String KEY_PENDING_ADDS = "widget_pending_adds";
    public static final String KEY_PENDING_WIDGET_OPS = "widget_pending_ops";
    public static final String KEY_WIDGET_THEME = "widget_theme";

    public static boolean isDarkTheme(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String theme = prefs.getString(KEY_WIDGET_THEME, "");
        if ("dark".equals(theme)) return true;
        if ("light".equals(theme)) return false;
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_TOGGLE_TODO.equals(intent.getAction())) {
            int itemIndex = intent.getIntExtra("item_index", -1);
            String noteId = intent.getStringExtra("note_id");
            String widgetAction = intent.getStringExtra("widget_action");
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

            if (itemIndex >= 0) {
                if ("delete".equals(widgetAction)) {
                    deleteTodoItem(context, noteId, itemIndex, appWidgetId);
                } else {
                    toggleTodoItem(context, noteId, itemIndex, appWidgetId);
                }
            }
        }
    }

    /**
     * Reorders a JSONArray of task items synchronously after toggling one item:
     * - Checked item moves to top of Completed group (right after all remaining active items)
     * - Unchecked item moves to end of Active group (right before any completed items)
     */
    private JSONArray toggleAndReorderItems(JSONArray source, int itemIndex, boolean[] outToggledState) {
        JSONArray result = new JSONArray();
        if (source == null || itemIndex < 0 || itemIndex >= source.length()) return source;

        try {
            JSONObject target = source.getJSONObject(itemIndex);
            boolean newChecked = !target.optBoolean("checked", false);
            outToggledState[0] = newChecked;
            target.put("checked", newChecked);

            JSONArray activeList = new JSONArray();
            JSONArray completedList = new JSONArray();

            for (int i = 0; i < source.length(); i++) {
                if (i == itemIndex) continue;
                JSONObject it = source.getJSONObject(i);
                if (it.optBoolean("checked", false)) {
                    completedList.put(it);
                } else {
                    activeList.put(it);
                }
            }

            for (int i = 0; i < activeList.length(); i++) {
                result.put(activeList.get(i));
            }
            // Places newly unchecked item at end of active, OR newly checked item at top of completed
            result.put(target);
            for (int i = 0; i < completedList.length(); i++) {
                result.put(completedList.get(i));
            }
        } catch (Exception e) {
            return source;
        }
        return result;
    }

    private void recordPendingOp(SharedPreferences prefs, SharedPreferences.Editor editor, String op, String noteId, int itemIndex, boolean checked, String text) {
        try {
            String opsStr = prefs.getString(KEY_PENDING_WIDGET_OPS, "[]");
            JSONArray ops = new JSONArray(opsStr);
            JSONObject ev = new JSONObject();
            ev.put("op", op);
            ev.put("noteId", noteId != null ? noteId : "");
            if (itemIndex >= 0) ev.put("itemIndex", itemIndex);
            if ("toggle".equals(op)) ev.put("checked", checked);
            if (text != null) ev.put("text", text);
            ev.put("timestamp", System.currentTimeMillis());
            ops.put(ev);
            editor.putString(KEY_PENDING_WIDGET_OPS, ops.toString());
        } catch (Exception ignored) {}
    }

    private void notifyWidgetListChanged(Context context, int appWidgetId) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_todo_listview);
        } else {
            ComponentName thisWidget = new ComponentName(context, TodoWidget.class);
            int[] ids = appWidgetManager.getAppWidgetIds(thisWidget);
            if (ids != null && ids.length > 0) {
                appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.widget_todo_listview);
            }
        }
    }

    private void toggleTodoItem(Context context, String noteId, int itemIndex, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String allJson = prefs.getString(KEY_ALL_CHECKLISTS_JSON, "[]");

        try {
            JSONArray arr = new JSONArray(allJson);
            boolean[] toggledState = new boolean[]{false};
            boolean updatedInAll = false;
            String resolvedNoteId = noteId != null ? noteId : "";

            for (int i = 0; i < arr.length(); i++) {
                JSONObject noteObj = arr.getJSONObject(i);
                String objId = noteObj.optString("id", "");
                if (objId.equals(resolvedNoteId) || resolvedNoteId.isEmpty()) {
                    resolvedNoteId = objId;
                    JSONArray itemsArr = noteObj.optJSONArray("items");
                    if (itemsArr != null && itemIndex < itemsArr.length()) {
                        JSONArray reordered = toggleAndReorderItems(itemsArr, itemIndex, toggledState);
                        noteObj.put("items", reordered);
                        updatedInAll = true;
                    }
                    break;
                }
            }

            SharedPreferences.Editor editor = prefs.edit();
            if (updatedInAll) {
                editor.putString(KEY_ALL_CHECKLISTS_JSON, arr.toString());
            }

            // Update fallback single items json
            String singleJson = prefs.getString(KEY_ITEMS_JSON, "[]");
            try {
                JSONArray singleArr = new JSONArray(singleJson);
                if (itemIndex < singleArr.length()) {
                    JSONArray reorderedSingle = toggleAndReorderItems(singleArr, itemIndex, toggledState);
                    editor.putString(KEY_ITEMS_JSON, reorderedSingle.toString());
                }
            } catch (Exception ignored) {}

            recordPendingOp(prefs, editor, "toggle", resolvedNoteId, itemIndex, toggledState[0], null);
            editor.apply();

            notifyWidgetListChanged(context, appWidgetId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteTodoItem(Context context, String noteId, int itemIndex, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String allJson = prefs.getString(KEY_ALL_CHECKLISTS_JSON, "[]");

        try {
            JSONArray arr = new JSONArray(allJson);
            boolean updatedInAll = false;
            String resolvedNoteId = noteId != null ? noteId : "";

            for (int i = 0; i < arr.length(); i++) {
                JSONObject noteObj = arr.getJSONObject(i);
                String objId = noteObj.optString("id", "");
                if (objId.equals(resolvedNoteId) || resolvedNoteId.isEmpty()) {
                    resolvedNoteId = objId;
                    JSONArray itemsArr = noteObj.optJSONArray("items");
                    if (itemsArr != null && itemIndex < itemsArr.length()) {
                        JSONArray remaining = new JSONArray();
                        for (int j = 0; j < itemsArr.length(); j++) {
                            if (j != itemIndex) {
                                remaining.put(itemsArr.get(j));
                            }
                        }
                        noteObj.put("items", remaining);
                        updatedInAll = true;
                    }
                    break;
                }
            }

            SharedPreferences.Editor editor = prefs.edit();
            if (updatedInAll) {
                editor.putString(KEY_ALL_CHECKLISTS_JSON, arr.toString());
            }

            // Update fallback single items json
            String singleJson = prefs.getString(KEY_ITEMS_JSON, "[]");
            try {
                JSONArray singleArr = new JSONArray(singleJson);
                if (itemIndex < singleArr.length()) {
                    JSONArray remainingSingle = new JSONArray();
                    for (int k = 0; k < singleArr.length(); k++) {
                        if (k != itemIndex) {
                            remainingSingle.put(singleArr.get(k));
                        }
                    }
                    editor.putString(KEY_ITEMS_JSON, remainingSingle.toString());
                }
            } catch (Exception ignored) {}

            recordPendingOp(prefs, editor, "delete", resolvedNoteId, itemIndex, false, null);
            editor.apply();

            notifyWidgetListChanged(context, appWidgetId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (int appWidgetId : appWidgetIds) {
            editor.remove(TodoWidgetConfigureActivity.PREF_PREFIX_KEY + appWidgetId);
            editor.remove(TodoWidgetConfigureActivity.PREF_TITLE_PREFIX_KEY + appWidgetId);
        }
        editor.apply();
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String noteId = prefs.getString(TodoWidgetConfigureActivity.PREF_PREFIX_KEY + appWidgetId, "");
        String noteTitle = prefs.getString(TodoWidgetConfigureActivity.PREF_TITLE_PREFIX_KEY + appWidgetId, "");

        String allJson = prefs.getString(KEY_ALL_CHECKLISTS_JSON, "[]");
        try {
            JSONArray arr = new JSONArray(allJson);
            if (noteId.isEmpty() && arr.length() > 0) {
                JSONObject first = arr.getJSONObject(0);
                noteId = first.optString("id", "");
                noteTitle = first.optString("title", "To-Do List");
            } else if (!noteId.isEmpty()) {
                // Keep title in sync with latest renamed title
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    if (noteId.equals(obj.optString("id", ""))) {
                        String latestTitle = obj.optString("title", "");
                        if (!latestTitle.isEmpty()) noteTitle = latestTitle;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}

        if (noteId.isEmpty()) {
            noteId = prefs.getString(KEY_NOTE_ID, "");
            if (noteTitle.isEmpty()) {
                noteTitle = prefs.getString(KEY_NOTE_TITLE, "To-Do List");
            }
        }

        boolean isDark = isDarkTheme(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_todo_list);

        // Apply exact Kyoto Light / Dark palette (zero orange)
        views.setInt(R.id.widget_todo_root, "setBackgroundResource",
                isDark ? R.drawable.widget_todo_card_bg_dark : R.drawable.widget_todo_card_bg);
        views.setTextColor(R.id.widget_todo_brand, Color.parseColor(isDark ? "#7EAAD0" : "#213A50"));
        views.setTextColor(R.id.widget_todo_title, Color.parseColor(isDark ? "#E8EEF3" : "#1C2B36"));
        views.setImageViewResource(R.id.widget_todo_btn_switch,
                isDark ? R.drawable.ic_widget_switch_list_dark : R.drawable.ic_widget_switch_list);
        views.setInt(R.id.widget_todo_btn_add, "setBackgroundResource",
                isDark ? R.drawable.widget_circle_button_dark : R.drawable.widget_circle_button);
        views.setInt(R.id.widget_todo_divider, "setBackgroundColor",
                Color.parseColor(isDark ? "#223344" : "#DFD3C0"));
        views.setTextColor(R.id.widget_todo_empty, Color.parseColor(isDark ? "#8AA0B4" : "#5A6E82"));

        views.setTextViewText(R.id.widget_todo_title, (noteTitle == null || noteTitle.isEmpty()) ? "To-Do List" : noteTitle);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        // Tapping header title opens that checklist inside texō
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (!noteId.isEmpty()) {
            openIntent.putExtra("note_id", noteId);
            openIntent.setAction("com.ahmedfarook.notesapp.OPEN_NOTE");
        }
        PendingIntent openPending = PendingIntent.getActivity(context, 100 + appWidgetId, openIntent, flags);
        views.setOnClickPendingIntent(R.id.widget_todo_title_area, openPending);

        // Switch list button opens checklist chooser directly for this widget
        Intent switchIntent = new Intent(context, TodoWidgetConfigureActivity.class);
        switchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        switchIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent switchPending = PendingIntent.getActivity(context, 300 + appWidgetId, switchIntent, flags);
        views.setOnClickPendingIntent(R.id.widget_todo_btn_switch, switchPending);

        // Quick "+" button & Empty state: opens Google Tasks-style QuickAddTaskActivity sheet right over home screen
        Intent addIntent = new Intent(context, QuickAddTaskActivity.class);
        addIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        addIntent.putExtra("note_id", noteId);
        addIntent.putExtra("note_title", (noteTitle == null || noteTitle.isEmpty()) ? "To-Do List" : noteTitle);
        addIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent addPending = PendingIntent.getActivity(context, 200 + appWidgetId, addIntent, flags);
        views.setOnClickPendingIntent(R.id.widget_todo_btn_add, addPending);
        views.setOnClickPendingIntent(R.id.widget_todo_empty, addPending);

        // Connect scrollable ListView via RemoteViewsService
        Intent serviceIntent = new Intent(context, TodoWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_todo_listview, serviceIntent);
        views.setEmptyView(R.id.widget_todo_listview, R.id.widget_todo_empty);

        // PendingIntent template for per-row Toggle and Delete buttons inside ListView
        Intent rowActionIntent = new Intent(context, TodoWidget.class);
        rowActionIntent.setAction(ACTION_TOGGLE_TODO);
        rowActionIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        int templateFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            templateFlags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent rowActionPending = PendingIntent.getBroadcast(context, appWidgetId, rowActionIntent, templateFlags);
        views.setPendingIntentTemplate(R.id.widget_todo_listview, rowActionPending);

        appWidgetManager.updateAppWidget(appWidgetId, views);
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_todo_listview);
    }
}
