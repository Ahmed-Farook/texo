package com.ahmedfarook.notesapp;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TodoWidgetConfigureActivity extends Activity {

    public static final String PREF_PREFIX_KEY = "widget_note_id_";
    public static final String PREF_TITLE_PREFIX_KEY = "widget_note_title_";

    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean showingAppearance = false;

    private String selectedPreset = "auto";
    private int selectedOpacity = 100;
    private String selectedTextMode = "auto";
    private boolean selectedBorder = true;

    private final List<Button> presetButtons = new ArrayList<>();
    private final List<Button> textModeButtons = new ArrayList<>();

    private static final String[][] PRESET_OPTIONS = new String[][] {
        {"auto", "Match App"},
        {"paper", "Paper"},
        {"slate", "Dark"},
        {"white", "White"},
        {"black", "AMOLED"},
        {"ocean", "Ocean"},
        {"sand", "Sand"},
        {"forest", "Forest"}
    };

    private static final String[][] TEXT_MODE_OPTIONS = new String[][] {
        {"auto", "Auto"},
        {"light", "Light Ink"},
        {"dark", "Dark Ink"}
    };

    private static class ChecklistOption {
        String id;
        String title;
        int taskCount;

        ChecklistOption(String id, String title, int taskCount) {
            this.id = id;
            this.title = title;
            this.taskCount = taskCount;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_widget_configure);

        boolean isDark = TodoWidget.isDarkTheme(this);
        MainActivity.applySystemBarAppearance(this, isDark);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
            );
        }

        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        View root = findViewById(R.id.config_root);
        TextView heading = findViewById(R.id.config_heading);
        TextView sub = findViewById(R.id.config_sub);
        View divider = findViewById(R.id.config_divider);
        TextView emptyText = findViewById(R.id.config_empty_text);
        ListView listView = findViewById(R.id.config_checklist_list);
        View emptyState = findViewById(R.id.config_empty_state);
        ScrollView appearancePanel = findViewById(R.id.config_appearance_panel);
        Button btnToggleAppearance = findViewById(R.id.btn_toggle_appearance);
        Button btnOpenTexo = findViewById(R.id.btn_open_texo);
        Button btnCancel = findViewById(R.id.btn_cancel_config);

        if (isDark) {
            if (root != null) root.setBackgroundColor(Color.parseColor("#131B24"));
            if (heading != null) heading.setTextColor(Color.parseColor("#E8EEF3"));
            if (sub != null) sub.setTextColor(Color.parseColor("#8AA0B4"));
            if (divider != null) divider.setBackgroundColor(Color.parseColor("#223344"));
            if (emptyText != null) emptyText.setTextColor(Color.parseColor("#8AA0B4"));
            if (btnOpenTexo != null) btnOpenTexo.setBackgroundResource(R.drawable.widget_btn_action_bg_dark);
            if (btnToggleAppearance != null) btnToggleAppearance.setBackgroundResource(R.drawable.widget_btn_action_bg_dark);
            if (btnCancel != null) {
                btnCancel.setTextColor(Color.parseColor("#CBD8E3"));
                btnCancel.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1C2733")));
            }
            if (listView != null) {
                listView.setDivider(new ColorDrawable(Color.parseColor("#223344")));
                listView.setDividerHeight(1);
            }
        }

        SharedPreferences prefs = getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
        String allJson = prefs.getString(TodoWidget.KEY_ALL_CHECKLISTS_JSON, "[]");

        List<ChecklistOption> options = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(allJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String id = obj.optString("id", "");
                String title = obj.optString("title", "To-Do List");
                JSONArray items = obj.optJSONArray("items");
                int count = items != null ? items.length() : 0;
                if (!id.isEmpty()) {
                    options.add(new ChecklistOption(id, title, count));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (options.isEmpty()) {
            String defaultId = prefs.getString(TodoWidget.KEY_NOTE_ID, "");
            String defaultTitle = prefs.getString(TodoWidget.KEY_NOTE_TITLE, "To-Do List");
            if (!defaultId.isEmpty()) {
                options.add(new ChecklistOption(defaultId, defaultTitle, 0));
            }
        }

        Runnable syncListVisibility = () -> {
            if (showingAppearance) {
                if (appearancePanel != null) appearancePanel.setVisibility(View.VISIBLE);
                listView.setVisibility(View.GONE);
                emptyState.setVisibility(View.GONE);
                btnToggleAppearance.setText("Pick Checklist");
                if (heading != null) heading.setText("Widget Appearance");
            } else {
                if (appearancePanel != null) appearancePanel.setVisibility(View.GONE);
                btnToggleAppearance.setText("Style & Opacity");
                if (heading != null) heading.setText("Select a Checklist");
                if (options.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                    listView.setVisibility(View.GONE);
                } else {
                    emptyState.setVisibility(View.GONE);
                    listView.setVisibility(View.VISIBLE);
                }
            }
        };

        setupAppearanceControls(isDark);
        syncListVisibility.run();

        btnToggleAppearance.setOnClickListener(v -> {
            showingAppearance = !showingAppearance;
            syncListVisibility.run();
        });

        btnCancel.setOnClickListener(v -> finish());
        btnOpenTexo.setOnClickListener(v -> {
            Intent launch = new Intent(this, MainActivity.class);
            launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
            finish();
        });

        if (!options.isEmpty()) {
            ChecklistAdapter adapter = new ChecklistAdapter(this, options, isDark);
            listView.setAdapter(adapter);

            listView.setOnItemClickListener((parent, view, position, id) -> {
                ChecklistOption chosen = options.get(position);

                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(PREF_PREFIX_KEY + mAppWidgetId, chosen.id);
                editor.putString(PREF_TITLE_PREFIX_KEY + mAppWidgetId, chosen.title);
                editor.apply();

                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
                TodoWidget.updateAppWidget(this, appWidgetManager, mAppWidgetId);

                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
                setResult(RESULT_OK, resultValue);
                finish();
            });
        }
    }

    private void setupAppearanceControls(boolean isDark) {
        selectedPreset = WidgetAppearance.getBgPreset(this);
        selectedOpacity = WidgetAppearance.getOpacity(this);
        selectedTextMode = WidgetAppearance.getTextMode(this);
        selectedBorder = WidgetAppearance.isShowBorder(this);

        TextView lblOpacity = findViewById(R.id.lbl_opacity);
        TextView txtOpacityVal = findViewById(R.id.txt_opacity_value);
        SeekBar seekOpacity = findViewById(R.id.seek_widget_opacity);
        TextView lblBg = findViewById(R.id.lbl_bg_style);
        TextView lblText = findViewById(R.id.lbl_text_mode);
        TextView lblBorder = findViewById(R.id.lbl_show_border);
        CheckBox chkBorder = findViewById(R.id.chk_show_border);
        Button btnApply = findViewById(R.id.btn_apply_appearance);
        LinearLayout row1 = findViewById(R.id.bg_preset_row_1);
        LinearLayout row2 = findViewById(R.id.bg_preset_row_2);
        LinearLayout textRow = findViewById(R.id.text_mode_row);

        int titleColor = Color.parseColor(isDark ? "#E8EEF3" : "#1C2B36");
        int accentColor = Color.parseColor(isDark ? "#7EAAD0" : "#213A50");
        if (lblOpacity != null) lblOpacity.setTextColor(titleColor);
        if (txtOpacityVal != null) txtOpacityVal.setTextColor(accentColor);
        if (lblBg != null) lblBg.setTextColor(titleColor);
        if (lblText != null) lblText.setTextColor(titleColor);
        if (lblBorder != null) lblBorder.setTextColor(titleColor);
        if (btnApply != null) {
            btnApply.setBackgroundResource(isDark ? R.drawable.widget_btn_action_bg_dark : R.drawable.widget_btn_action_bg);
        }

        if (seekOpacity != null && txtOpacityVal != null) {
            seekOpacity.setProgress(selectedOpacity);
            txtOpacityVal.setText(selectedOpacity + "%");
            seekOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    selectedOpacity = progress;
                    txtOpacityVal.setText(progress + "%");
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {
                    WidgetAppearance.saveAppearance(TodoWidgetConfigureActivity.this, selectedPreset, selectedOpacity, selectedTextMode, selectedBorder);
                    NotesWidgetPlugin.refreshAllWidgetsStatic(TodoWidgetConfigureActivity.this);
                }
            });
        }

        if (chkBorder != null) {
            chkBorder.setChecked(selectedBorder);
            chkBorder.setOnCheckedChangeListener((buttonView, isChecked) -> {
                selectedBorder = isChecked;
                WidgetAppearance.saveAppearance(this, selectedPreset, selectedOpacity, selectedTextMode, selectedBorder);
                NotesWidgetPlugin.refreshAllWidgetsStatic(this);
            });
        }

        presetButtons.clear();
        if (row1 != null && row2 != null) {
            row1.removeAllViews();
            row2.removeAllViews();
            for (int i = 0; i < PRESET_OPTIONS.length; i++) {
                final String code = PRESET_OPTIONS[i][0];
                final String label = PRESET_OPTIONS[i][1];
                Button btn = createChipButton(label, isDark);
                btn.setTag(code);
                btn.setOnClickListener(v -> {
                    selectedPreset = code;
                    updateChipSelections(isDark);
                    WidgetAppearance.saveAppearance(this, selectedPreset, selectedOpacity, selectedTextMode, selectedBorder);
                    NotesWidgetPlugin.refreshAllWidgetsStatic(this);
                });
                presetButtons.add(btn);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins(4, 0, 4, 0);
                (i < 4 ? row1 : row2).addView(btn, lp);
            }
        }

        textModeButtons.clear();
        if (textRow != null) {
            textRow.removeAllViews();
            for (String[] opt : TEXT_MODE_OPTIONS) {
                final String code = opt[0];
                final String label = opt[1];
                Button btn = createChipButton(label, isDark);
                btn.setTag(code);
                btn.setOnClickListener(v -> {
                    selectedTextMode = code;
                    updateChipSelections(isDark);
                    WidgetAppearance.saveAppearance(this, selectedPreset, selectedOpacity, selectedTextMode, selectedBorder);
                    NotesWidgetPlugin.refreshAllWidgetsStatic(this);
                });
                textModeButtons.add(btn);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins(4, 0, 4, 0);
                textRow.addView(btn, lp);
            }
        }

        updateChipSelections(isDark);

        if (btnApply != null) {
            btnApply.setOnClickListener(v -> {
                WidgetAppearance.saveAppearance(this, selectedPreset, selectedOpacity, selectedTextMode, selectedBorder);
                NotesWidgetPlugin.refreshAllWidgetsStatic(this);
                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
                setResult(RESULT_OK, resultValue);
                finish();
            });
        }
    }

    private Button createChipButton(String label, boolean isDark) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(11.5f);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(6, 12, 6, 12);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        return btn;
    }

    private void updateChipSelections(boolean isDark) {
        for (Button btn : presetButtons) {
            boolean active = selectedPreset.equals(btn.getTag());
            styleChip(btn, active, isDark);
        }
        for (Button btn : textModeButtons) {
            boolean active = selectedTextMode.equals(btn.getTag());
            styleChip(btn, active, isDark);
        }
    }

    private void styleChip(Button btn, boolean active, boolean isDark) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16f);
        if (active) {
            bg.setColor(Color.parseColor(isDark ? "#345A7D" : "#213A50"));
            btn.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.parseColor(isDark ? "#1C2733" : "#EFE7D8"));
            bg.setStroke(2, Color.parseColor(isDark ? "#2C4054" : "#DFD3C0"));
            btn.setTextColor(Color.parseColor(isDark ? "#CBD8E3" : "#1C2B36"));
        }
        btn.setBackground(bg);
    }

    private static class ChecklistAdapter extends BaseAdapter {
        private final Context context;
        private final List<ChecklistOption> items;
        private final boolean isDark;

        ChecklistAdapter(Context context, List<ChecklistOption> items, boolean isDark) {
            this.context = context;
            this.items = items;
            this.isDark = isDark;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_configure_checklist, parent, false);
            }

            ChecklistOption item = items.get(position);
            TextView titleView = convertView.findViewById(R.id.item_checklist_title);
            TextView subtitleView = convertView.findViewById(R.id.item_checklist_subtitle);

            titleView.setText(item.title);
            subtitleView.setText(item.taskCount + (item.taskCount == 1 ? " task" : " tasks"));
            titleView.setTextColor(Color.parseColor(isDark ? "#E8EEF3" : "#1C2B36"));
            subtitleView.setTextColor(Color.parseColor(isDark ? "#8AA0B4" : "#6E7E8E"));

            return convertView;
        }
    }
}
