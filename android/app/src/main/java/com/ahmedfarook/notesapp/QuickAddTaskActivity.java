package com.ahmedfarook.notesapp;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

public class QuickAddTaskActivity extends AppCompatActivity {

    private String noteId = "";
    private String noteTitle = "To-Do List";
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_add_task);

        Window win = getWindow();
        if (win != null) {
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        noteId = getIntent().getStringExtra("note_id");
        noteTitle = getIntent().getStringExtra("note_title");
        appWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        SharedPreferences prefs = getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);

        if (noteId == null || noteId.isEmpty()) {
            String allJson = prefs.getString(TodoWidget.KEY_ALL_CHECKLISTS_JSON, "[]");
            try {
                JSONArray arr = new JSONArray(allJson);
                if (arr.length() > 0) {
                    JSONObject first = arr.getJSONObject(0);
                    noteId = first.optString("id", "");
                    noteTitle = first.optString("title", "To-Do List");
                }
            } catch (Exception ignored) {}

            if (noteId == null || noteId.isEmpty()) {
                noteId = prefs.getString(TodoWidget.KEY_NOTE_ID, "");
                noteTitle = prefs.getString(TodoWidget.KEY_NOTE_TITLE, "To-Do List");
            }
        }

        boolean isDark = TodoWidget.isDarkTheme(this);
        MainActivity.applySystemBarAppearance(this, isDark);

        View backdrop = findViewById(R.id.quick_add_backdrop);
        View sheet = findViewById(R.id.quick_add_sheet);
        TextView titleView = findViewById(R.id.quick_add_title);
        final EditText taskInput = findViewById(R.id.edit_quick_task);
        Button btnAdd = findViewById(R.id.btn_quick_add);
        Button btnCancel = findViewById(R.id.btn_quick_cancel);
        ImageButton btnClose = findViewById(R.id.btn_quick_close);

        // Apply Kyoto Light / Dark colors seamlessly
        if (sheet != null) {
            sheet.setBackgroundResource(isDark ? R.drawable.widget_sheet_bg_dark : R.drawable.widget_sheet_bg);
        }
        if (titleView != null) {
            titleView.setTextColor(Color.parseColor(isDark ? "#E8EEF3" : "#1C2B36"));
            if (noteTitle != null && !noteTitle.isEmpty()) {
                titleView.setText("Add to " + noteTitle);
            }
        }
        if (taskInput != null) {
            taskInput.setBackgroundResource(isDark ? R.drawable.widget_input_bg_dark : R.drawable.widget_input_bg);
            taskInput.setTextColor(Color.parseColor(isDark ? "#E8EEF3" : "#1C2B36"));
            taskInput.setHintTextColor(Color.parseColor(isDark ? "#627788" : "#8AA0B4"));
            // Allow smooth word wrapping while keeping IME ActionDone button functional
            taskInput.setHorizontallyScrolling(false);
            taskInput.setMaxLines(5);
        }
        if (btnCancel != null) {
            btnCancel.setTextColor(Color.parseColor(isDark ? "#8AA0B4" : "#5A6E82"));
        }
        if (btnAdd != null) {
            btnAdd.setBackgroundResource(isDark ? R.drawable.widget_btn_action_bg_dark : R.drawable.widget_btn_action_bg);
        }

        View.OnClickListener closeListener = v -> finishWithoutAnimation();
        if (backdrop != null) backdrop.setOnClickListener(closeListener);
        if (btnCancel != null) btnCancel.setOnClickListener(closeListener);
        if (btnClose != null) btnClose.setOnClickListener(closeListener);

        View.OnClickListener addListener = v -> {
            if (taskInput != null) {
                String text = taskInput.getText().toString().replaceAll("[\\r\\n]+", " ").trim();
                if (!text.isEmpty()) {
                    addTaskAndDismiss(text);
                }
            }
        };

        if (btnAdd != null) btnAdd.setOnClickListener(addListener);

        if (taskInput != null) {
            taskInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    String text = taskInput.getText().toString().replaceAll("[\\r\\n]+", " ").trim();
                    if (!text.isEmpty()) {
                        addTaskAndDismiss(text);
                        return true;
                    }
                }
                return false;
            });

            taskInput.requestFocus();
            taskInput.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(taskInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 80);
        }
    }

    private JSONArray insertAtEndOfActive(JSONArray source, JSONObject newItem) {
        JSONArray result = new JSONArray();
        boolean inserted = false;
        try {
            if (source != null) {
                for (int i = 0; i < source.length(); i++) {
                    JSONObject it = source.getJSONObject(i);
                    if (!inserted && it.optBoolean("checked", false)) {
                        result.put(newItem);
                        inserted = true;
                    }
                    result.put(it);
                }
            }
            if (!inserted) {
                result.put(newItem);
            }
        } catch (Exception e) {
            result.put(newItem);
        }
        return result;
    }

    private void addTaskAndDismiss(String text) {
        try {
            SharedPreferences prefs = getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            JSONObject newItem = new JSONObject();
            newItem.put("text", text);
            newItem.put("checked", false);

            // 1. Insert at end of active items in KEY_ALL_CHECKLISTS_JSON
            String allJson = prefs.getString(TodoWidget.KEY_ALL_CHECKLISTS_JSON, "[]");
            JSONArray arr = new JSONArray(allJson);
            boolean addedToAll = false;
            String resolvedNoteId = noteId != null ? noteId : "";

            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject noteObj = arr.getJSONObject(i);
                String objId = noteObj.optString("id", "");
                if (objId.equals(resolvedNoteId) || resolvedNoteId.isEmpty()) {
                    resolvedNoteId = objId;
                    JSONArray itemsArr = noteObj.optJSONArray("items");
                    noteObj.put("items", insertAtEndOfActive(itemsArr, newItem));
                    noteObj.put("updatedAt", now);
                    addedToAll = true;
                    break;
                }
            }

            if (addedToAll) {
                editor.putString(TodoWidget.KEY_ALL_CHECKLISTS_JSON, arr.toString());
            }

            // 2. Also insert into fallback KEY_ITEMS_JSON
            String singleJson = prefs.getString(TodoWidget.KEY_ITEMS_JSON, "[]");
            try {
                JSONArray singleArr = new JSONArray(singleJson);
                editor.putString(TodoWidget.KEY_ITEMS_JSON, insertAtEndOfActive(singleArr, newItem).toString());
            } catch (Exception ignored) {}

            // 3. Record chronological widget add op
            String opsStr = prefs.getString(TodoWidget.KEY_PENDING_WIDGET_OPS, "[]");
            JSONArray ops;
            try {
                ops = new JSONArray(opsStr);
            } catch (Exception e) {
                ops = new JSONArray();
            }
            JSONObject addOp = new JSONObject();
            addOp.put("op", "add");
            addOp.put("noteId", resolvedNoteId);
            addOp.put("text", text);
            addOp.put("timestamp", now);
            ops.put(addOp);
            editor.putString(TodoWidget.KEY_PENDING_WIDGET_OPS, ops.toString());

            editor.apply();

            // 4. Refresh widget list immediately
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_todo_listview);
            } else {
                ComponentName thisWidget = new ComponentName(this, TodoWidget.class);
                int[] ids = appWidgetManager.getAppWidgetIds(thisWidget);
                if (ids != null && ids.length > 0) {
                    appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.widget_todo_listview);
                }
            }

            // 5. Immediately sync this task addition to cloud in background via TodoWidget receiver
            if (resolvedNoteId != null && !resolvedNoteId.isEmpty()) {
                Intent syncIntent = new Intent(this, TodoWidget.class);
                syncIntent.setAction(TodoWidget.ACTION_SYNC_CLOUD);
                syncIntent.putExtra("note_id", resolvedNoteId);
                sendBroadcast(syncIntent);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        finishWithoutAnimation();
    }

    private void finishWithoutAnimation() {
        finish();
        overridePendingTransition(0, 0);
    }
}
