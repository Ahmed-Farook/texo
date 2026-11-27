package com.ahmedfarook.notesapp;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.StrikethroughSpan;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TodoWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new TodoRemoteViewsFactory(this.getApplicationContext(), intent);
    }

    static class TodoRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
        private final Context mContext;
        private final int mAppWidgetId;
        private String mNoteId = "";
        private final List<TodoItem> mItems = new ArrayList<>();

        static class TodoItem {
            String text;
            boolean checked;

            TodoItem(String text, boolean checked) {
                this.text = text;
                this.checked = checked;
            }
        }

        public TodoRemoteViewsFactory(Context context, Intent intent) {
            mContext = context;
            mAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        @Override
        public void onCreate() {
            loadItems();
        }

        @Override
        public void onDataSetChanged() {
            loadItems();
        }

        private void loadItems() {
            mItems.clear();
            SharedPreferences prefs = mContext.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);

            mNoteId = prefs.getString(TodoWidgetConfigureActivity.PREF_PREFIX_KEY + mAppWidgetId, "");

            String allJson = prefs.getString(TodoWidget.KEY_ALL_CHECKLISTS_JSON, "[]");
            boolean found = false;

            try {
                JSONArray arr = new JSONArray(allJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String id = obj.optString("id", "");
                    if (id.equals(mNoteId) || (mNoteId.isEmpty() && i == 0)) {
                        mNoteId = id;
                        JSONArray itemsArr = obj.optJSONArray("items");
                        if (itemsArr != null) {
                            for (int j = 0; j < itemsArr.length(); j++) {
                                JSONObject itemObj = itemsArr.getJSONObject(j);
                                String text = itemObj.optString("text", "");
                                boolean checked = itemObj.optBoolean("checked", false);
                                mItems.add(new TodoItem(text, checked));
                            }
                        }
                        found = true;
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            String singleFallbackId = prefs.getString(TodoWidget.KEY_NOTE_ID, "");
            if (!found && mItems.isEmpty() && (mNoteId.isEmpty() || mNoteId.equals(singleFallbackId))) {
                String singleJson = prefs.getString(TodoWidget.KEY_ITEMS_JSON, "[]");
                try {
                    JSONArray singleArr = new JSONArray(singleJson);
                    for (int k = 0; k < singleArr.length(); k++) {
                        JSONObject itemObj = singleArr.getJSONObject(k);
                        mItems.add(new TodoItem(itemObj.optString("text", ""), itemObj.optBoolean("checked", false)));
                    }
                } catch (Exception ignored) {}
            }
        }

        @Override
        public void onDestroy() {
            mItems.clear();
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= mItems.size()) {
                return null;
            }

            boolean isLightFg = WidgetAppearance.isLightForeground(mContext);
            TodoItem item = mItems.get(position);
            RemoteViews views = new RemoteViews(mContext.getPackageName(), R.layout.widget_todo_item);

            if (item.checked) {
                views.setImageViewResource(R.id.widget_todo_item_check,
                        isLightFg ? R.drawable.widget_checkbox_checked_dark : R.drawable.widget_checkbox_checked);
                SpannableString span = new SpannableString(item.text);
                span.setSpan(new StrikethroughSpan(), 0, item.text.length(), 0);
                views.setTextViewText(R.id.widget_todo_item_text, span);
                views.setTextColor(R.id.widget_todo_item_text, WidgetAppearance.completedTextColor(isLightFg));
            } else {
                views.setImageViewResource(R.id.widget_todo_item_check,
                        isLightFg ? R.drawable.widget_checkbox_unchecked_dark : R.drawable.widget_checkbox_unchecked);
                views.setTextViewText(R.id.widget_todo_item_text, item.text);
                views.setTextColor(R.id.widget_todo_item_text, WidgetAppearance.primaryTextColor(isLightFg));
            }
            views.setInt(R.id.widget_todo_item_delete, "setColorFilter", WidgetAppearance.secondaryTextColor(isLightFg));

            // Fill-in intent for Toggle (clicking checkbox or task text)
            Intent toggleFillIn = new Intent();
            toggleFillIn.putExtra("widget_action", "toggle");
            toggleFillIn.putExtra("item_index", position);
            toggleFillIn.putExtra("note_id", mNoteId);
            toggleFillIn.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
            views.setOnClickFillInIntent(R.id.widget_todo_item_toggle_area, toggleFillIn);

            // Fill-in intent for Delete (clicking × button on the right)
            Intent deleteFillIn = new Intent();
            deleteFillIn.putExtra("widget_action", "delete");
            deleteFillIn.putExtra("item_index", position);
            deleteFillIn.putExtra("note_id", mNoteId);
            deleteFillIn.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
            views.setOnClickFillInIntent(R.id.widget_todo_item_delete, deleteFillIn);

            return views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
