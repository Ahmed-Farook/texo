package com.ahmedfarook.notesapp;

import android.app.Activity;
import android.content.Intent;
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
        handleWidgetIntent(getIntent());
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

        if (getBridge() != null && getBridge().getWebView() != null) {
            if ("add_checklist_item".equals(intent.getStringExtra("action")) || "com.ahmedfarook.notesapp.ADD_CHECKLIST_ITEM".equals(intent.getAction())) {
                String noteId = intent.getStringExtra("note_id");
                getBridge().getWebView().postDelayed(() -> {
                    getBridge().getWebView().evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('action:add-checklist-item', { detail: { noteId: '" + (noteId != null ? noteId : "") + "' } }));",
                        null
                    );
                }, 400);
            } else if (QuickNoteWidget.ACTION_NEW_NOTE.equals(intent.getAction())) {
                getBridge().getWebView().postDelayed(() -> {
                    getBridge().getWebView().evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('action:new-note'));",
                        null
                    );
                }, 400);
            } else if (intent.hasExtra("note_id")) {
                String noteId = intent.getStringExtra("note_id");
                if (noteId != null && !noteId.isEmpty()) {
                    getBridge().getWebView().postDelayed(() -> {
                        getBridge().getWebView().evaluateJavascript(
                            "window.dispatchEvent(new CustomEvent('action:open-note', { detail: { noteId: '" + noteId + "' } }));",
                            null
                        );
                    }, 400);
                }
            }
        }
    }
}
