package com.ahmedfarook.notesapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Centralized widget appearance configuration and RemoteViews styling helper
 * (background preset, 0-100% opacity, text contrast mode, and outline border visibility).
 */
public final class WidgetAppearance {

    public static final String KEY_WIDGET_BG_PRESET = "widget_bg_preset";
    public static final String KEY_WIDGET_OPACITY = "widget_opacity";
    public static final String KEY_WIDGET_TEXT_MODE = "widget_text_mode";
    public static final String KEY_WIDGET_SHOW_BORDER = "widget_show_border";

    private WidgetAppearance() {}

    public static String getBgPreset(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_WIDGET_BG_PRESET, "auto");
    }

    public static int getOpacity(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
        int raw = prefs.getInt(KEY_WIDGET_OPACITY, 100);
        return Math.max(0, Math.min(100, raw));
    }

    public static String getTextMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_WIDGET_TEXT_MODE, "auto");
    }

    public static boolean isShowBorder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_WIDGET_SHOW_BORDER, true);
    }

    public static void saveAppearance(Context context, String preset, int opacity, String textMode, boolean showBorder) {
        SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(KEY_WIDGET_BG_PRESET, preset != null && !preset.isEmpty() ? preset : "auto")
            .putInt(KEY_WIDGET_OPACITY, Math.max(0, Math.min(100, opacity)))
            .putString(KEY_WIDGET_TEXT_MODE, textMode != null && !textMode.isEmpty() ? textMode : "auto")
            .putBoolean(KEY_WIDGET_SHOW_BORDER, showBorder)
            .apply();
    }

    public static boolean isLightPresetSurface(Context context, String preset) {
        if ("paper".equals(preset) || "white".equals(preset) || "sand".equals(preset)) {
            return true;
        }
        if ("slate".equals(preset) || "black".equals(preset) || "ocean".equals(preset)
                || "forest".equals(preset) || "plum".equals(preset)) {
            return false;
        }
        return !TodoWidget.isDarkTheme(context);
    }

    /**
     * Returns true when text/checkboxes/icons should render bright for dark or translucent-over-dark surfaces.
     */
    public static boolean isLightForeground(Context context) {
        String textMode = getTextMode(context);
        if ("light".equals(textMode)) return true;
        if ("dark".equals(textMode)) return false;
        return !isLightPresetSurface(context, getBgPreset(context));
    }

    public static int resolveBackgroundColor(Context context, String preset) {
        if ("paper".equals(preset)) return Color.parseColor("#FAF6EF");
        if ("slate".equals(preset)) return Color.parseColor("#16202A");
        if ("white".equals(preset)) return Color.parseColor("#FFFFFF");
        if ("black".equals(preset)) return Color.parseColor("#0B0D10");
        if ("ocean".equals(preset)) return Color.parseColor("#142C42");
        if ("sand".equals(preset)) return Color.parseColor("#EFE3CE");
        if ("forest".equals(preset)) return Color.parseColor("#162920");
        if ("plum".equals(preset)) return Color.parseColor("#261B2E");
        return TodoWidget.isDarkTheme(context)
            ? Color.parseColor("#16202A")
            : Color.parseColor("#FAF6EF");
    }

    public static int resolveBorderColor(Context context, String preset, boolean lightForeground) {
        if ("paper".equals(preset)) return Color.parseColor("#DFD3C0");
        if ("slate".equals(preset)) return Color.parseColor("#223344");
        if ("white".equals(preset)) return Color.parseColor("#D1D5DB");
        if ("black".equals(preset)) return Color.parseColor("#2E343B");
        if ("ocean".equals(preset)) return Color.parseColor("#2C5275");
        if ("sand".equals(preset)) return Color.parseColor("#D2C0A4");
        if ("forest".equals(preset)) return Color.parseColor("#2A4B3B");
        if ("plum".equals(preset)) return Color.parseColor("#473457");
        return lightForeground
            ? Color.parseColor("#2E4458")
            : Color.parseColor("#DFD3C0");
    }

    public static int primaryTextColor(boolean lightForeground) {
        return Color.parseColor(lightForeground ? "#F2F6FA" : "#1C2B36");
    }

    public static int secondaryTextColor(boolean lightForeground) {
        return Color.parseColor(lightForeground ? "#B4C6D6" : "#5A6E82");
    }

    public static int completedTextColor(boolean lightForeground) {
        return Color.parseColor(lightForeground ? "#8FA4B8" : "#8AA0B4");
    }

    public static int dividerColor(Context context, boolean lightForeground) {
        int opacity = getOpacity(context);
        int alpha = opacity > 0 ? 185 : 95;
        return lightForeground
            ? Color.argb(alpha, 255, 255, 255)
            : Color.argb(alpha, 33, 58, 80);
    }

    public static void applyWidgetContainer(Context context, RemoteViews views, int fillViewId, int borderViewId) {
        String preset = getBgPreset(context);
        int opacity = getOpacity(context);
        boolean showBorder = isShowBorder(context);
        boolean lightFg = isLightForeground(context);

        int bgColor = resolveBackgroundColor(context, preset);
        int borderColor = resolveBorderColor(context, preset, lightFg);
        int fillAlpha = Math.max(0, Math.min(255, Math.round(opacity * 2.55f)));

        if (fillAlpha <= 0) {
            views.setViewVisibility(fillViewId, View.INVISIBLE);
        } else {
            views.setViewVisibility(fillViewId, View.VISIBLE);
            views.setInt(fillViewId, "setColorFilter", bgColor);
            views.setInt(fillViewId, "setImageAlpha", fillAlpha);
        }

        if (!showBorder) {
            views.setViewVisibility(borderViewId, View.GONE);
        } else {
            int borderAlpha = Math.max(95, fillAlpha);
            views.setViewVisibility(borderViewId, View.VISIBLE);
            views.setInt(borderViewId, "setColorFilter", borderColor);
            views.setInt(borderViewId, "setImageAlpha", borderAlpha);
        }
    }
}
