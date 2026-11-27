package com.ahmedfarook.notesapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AppUpdaterPlugin.class);
        registerPlugin(BiometricAuthPlugin.class);
        registerPlugin(NotesWidgetPlugin.class);
        super.onCreate(savedInstanceState);
        applySavedSystemBarTheme();
        if (savedInstanceState == null) {
            handleWidgetIntent(getIntent());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        applySavedSystemBarTheme();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleWidgetIntent(intent);
    }

    private void applySavedSystemBarTheme() {
        applySystemBarAppearance(this, TodoWidget.isDarkTheme(this));
    }

    public static void applySystemBarAppearance(Activity activity, boolean isDark) {
        if (activity == null) return;
        try {
            Window window = activity.getWindow();
            if (window == null) return;
            View decorView = window.getDecorView();
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decorView);
            if (controller != null) {
                controller.setAppearanceLightStatusBars(!isDark);
                controller.setAppearanceLightNavigationBars(!isDark);
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                try {
                    window.setNavigationBarColor(Color.parseColor(isDark ? "#131B24" : "#F7F2EA"));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void handleWidgetIntent(Intent intent) {
        if (intent == null) return;
        // Ignore stale widget intents when Android reopens an old task from Recents
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            return;
        }

        String launchAction = "";
        String launchNoteId = "";

        String actionExtra = intent.getStringExtra("action");
        String intentAction = intent.getAction();

        if ("add_checklist_item".equals(actionExtra) || "com.ahmedfarook.notesapp.ADD_CHECKLIST_ITEM".equals(intentAction)) {
            launchAction = "add_checklist_item";
            String nid = intent.getStringExtra("note_id");
            if (nid != null) launchNoteId = nid;
        } else if (QuickNoteWidget.ACTION_NEW_NOTE.equals(intentAction) || "new_note".equals(actionExtra)) {
            launchAction = "new_note";
        } else if ("com.ahmedfarook.notesapp.OPEN_NOTE".equals(intentAction) || "open_note".equals(actionExtra) || intent.hasExtra("note_id")) {
            String nid = intent.getStringExtra("note_id");
            if (nid != null && !nid.isEmpty()) {
                launchAction = "open_note";
                launchNoteId = nid;
            }
        }

        if (launchAction.isEmpty()) return;

        // Persist pending launch so cold-started JS always consumes it once WebView/React finishes mounting
        SharedPreferences prefs = getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(TodoWidget.KEY_PENDING_LAUNCH_ACTION, launchAction)
            .putString(TodoWidget.KEY_PENDING_LAUNCH_NOTE_ID, launchNoteId)
            .apply();

        // Notify an already-mounted WebView immediately on warm start
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().postDelayed(() -> {
                if (getBridge() != null && getBridge().getWebView() != null) {
                    getBridge().getWebView().evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('action:widget-launch'));",
                        null
                    );
                }
            }, 120);
        }
    }
}
