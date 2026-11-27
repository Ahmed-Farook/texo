package com.ahmedfarook.notesapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Synchronizes Android home-screen widget checklist edits directly to Supabase Cloud
 * with Zero-Knowledge AES-256-GCM encryption in the background — without requiring the app to be opened.
 */
public final class WidgetCloudSyncHelper {

    private static final String TAG = "WidgetCloudSync";
    private static final String ENCRYPTION_PREFIX = "enc:v1:";

    public static final String KEY_CLOUD_URL = "cloud_supabase_url";
    public static final String KEY_CLOUD_ANON_KEY = "cloud_supabase_anon_key";
    public static final String KEY_CLOUD_ACCESS_TOKEN = "cloud_access_token";
    public static final String KEY_CLOUD_REFRESH_TOKEN = "cloud_refresh_token";
    public static final String KEY_CLOUD_EXPIRES_AT = "cloud_expires_at";
    public static final String KEY_CLOUD_USER_ID = "cloud_user_id";
    public static final String KEY_CLOUD_E2EE_KEY = "cloud_e2ee_key_b64";
    public static final String KEY_CLOUD_DIRTY_NOTES = "cloud_dirty_note_ids";
    public static final String KEY_CLOUD_SESSION_REFRESHED = "cloud_session_refreshed";

    private static final ExecutorService SYNC_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private WidgetCloudSyncHelper() {}

    public static void saveCloudCredentials(
            Context context,
            String supabaseUrl,
            String supabaseAnonKey,
            String accessToken,
            String refreshToken,
            long expiresAt,
            String userId,
            String e2eeKeyBase64
    ) {
        SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(KEY_CLOUD_URL, supabaseUrl != null ? supabaseUrl : "")
            .putString(KEY_CLOUD_ANON_KEY, supabaseAnonKey != null ? supabaseAnonKey : "")
            .putString(KEY_CLOUD_ACCESS_TOKEN, accessToken != null ? accessToken : "")
            .putString(KEY_CLOUD_REFRESH_TOKEN, refreshToken != null ? refreshToken : "")
            .putLong(KEY_CLOUD_EXPIRES_AT, expiresAt)
            .putString(KEY_CLOUD_USER_ID, userId != null ? userId : "")
            .putString(KEY_CLOUD_E2EE_KEY, e2eeKeyBase64 != null ? e2eeKeyBase64 : "")
            .putBoolean(KEY_CLOUD_SESSION_REFRESHED, false)
            .apply();
    }

    public static void clearCloudCredentials(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .remove(KEY_CLOUD_ACCESS_TOKEN)
            .remove(KEY_CLOUD_REFRESH_TOKEN)
            .remove(KEY_CLOUD_EXPIRES_AT)
            .remove(KEY_CLOUD_USER_ID)
            .remove(KEY_CLOUD_E2EE_KEY)
            .remove(KEY_CLOUD_DIRTY_NOTES)
            .remove(KEY_CLOUD_SESSION_REFRESHED)
            .apply();
    }

    public static void enqueueCloudSyncAsync(
            final Context context,
            final String noteId,
            final BroadcastReceiver.PendingResult pendingResult
    ) {
        final Context appContext = context.getApplicationContext();
        markDirtyNote(appContext, noteId);

        SYNC_EXECUTOR.execute(() -> {
            try {
                syncDirtyChecklistsNow(appContext);
            } catch (Exception e) {
                Log.w(TAG, "Background widget cloud sync error", e);
            } finally {
                if (pendingResult != null) {
                    try {
                        pendingResult.finish();
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private static synchronized void markDirtyNote(Context context, String noteId) {
        if (noteId == null || noteId.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> dirty = readDirtyNoteIds(prefs);
        dirty.add(noteId);
        writeDirtyNoteIds(prefs, dirty);
    }

    private static Set<String> readDirtyNoteIds(SharedPreferences prefs) {
        Set<String> result = new LinkedHashSet<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_CLOUD_DIRTY_NOTES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String id = arr.optString(i, "");
                if (!id.isEmpty()) result.add(id);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static void writeDirtyNoteIds(SharedPreferences prefs, Set<String> ids) {
        JSONArray arr = new JSONArray();
        for (String id : ids) {
            arr.put(id);
        }
        prefs.edit().putString(KEY_CLOUD_DIRTY_NOTES, arr.toString()).apply();
    }

    private static synchronized void syncDirtyChecklistsNow(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(TodoWidget.PREFS_NAME, Context.MODE_PRIVATE);
        String supabaseUrl = prefs.getString(KEY_CLOUD_URL, "");
        String anonKey = prefs.getString(KEY_CLOUD_ANON_KEY, "");
        String accessToken = prefs.getString(KEY_CLOUD_ACCESS_TOKEN, "");
        String refreshToken = prefs.getString(KEY_CLOUD_REFRESH_TOKEN, "");
        long expiresAt = prefs.getLong(KEY_CLOUD_EXPIRES_AT, 0L);
        String userId = prefs.getString(KEY_CLOUD_USER_ID, "");
        String e2eeKeyB64 = prefs.getString(KEY_CLOUD_E2EE_KEY, "");

        if (supabaseUrl.isEmpty() || anonKey.isEmpty() || accessToken.isEmpty() || userId.isEmpty()) {
            return;
        }

        Set<String> dirtyIds = readDirtyNoteIds(prefs);
        if (dirtyIds.isEmpty()) {
            return;
        }

        // Proactively refresh Supabase JWT if expired or expiring within 60 seconds
        long nowSec = System.currentTimeMillis() / 1000L;
        if (expiresAt > 0 && nowSec >= (expiresAt - 60L) && !refreshToken.isEmpty()) {
            String refreshedAccess = refreshAuthToken(prefs, supabaseUrl, anonKey, refreshToken);
            if (refreshedAccess != null && !refreshedAccess.isEmpty()) {
                accessToken = refreshedAccess;
                refreshToken = prefs.getString(KEY_CLOUD_REFRESH_TOKEN, refreshToken);
            }
        }

        Set<String> remainingDirty = new LinkedHashSet<>();
        for (String noteId : dirtyIds) {
            try {
                JSONObject noteObj = findChecklistSnapshot(prefs, noteId);
                if (noteObj == null) {
                    continue;
                }

                JSONObject dbPayload = buildEncryptedDbPayload(noteObj, userId, e2eeKeyB64);
                int status = postUpsertNote(supabaseUrl, anonKey, accessToken, dbPayload);

                if ((status == 401 || status == 403) && !refreshToken.isEmpty()) {
                    String refreshedAccess = refreshAuthToken(prefs, supabaseUrl, anonKey, refreshToken);
                    if (refreshedAccess != null && !refreshedAccess.isEmpty()) {
                        accessToken = refreshedAccess;
                        refreshToken = prefs.getString(KEY_CLOUD_REFRESH_TOKEN, refreshToken);
                        status = postUpsertNote(supabaseUrl, anonKey, accessToken, dbPayload);
                    }
                }

                if (status < 200 || status >= 300) {
                    remainingDirty.add(noteId);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed cloud upsert for checklist " + noteId, e);
                remainingDirty.add(noteId);
            }
        }

        writeDirtyNoteIds(prefs, remainingDirty);
    }

    private static JSONObject findChecklistSnapshot(SharedPreferences prefs, String noteId) {
        try {
            JSONArray arr = new JSONArray(prefs.getString(TodoWidget.KEY_ALL_CHECKLISTS_JSON, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (noteId.equals(obj.optString("id", ""))) {
                    return obj;
                }
            }
        } catch (Exception ignored) {}

        String singleId = prefs.getString(TodoWidget.KEY_NOTE_ID, "");
        if (noteId.equals(singleId)) {
            try {
                JSONObject fallback = new JSONObject();
                fallback.put("id", singleId);
                fallback.put("title", prefs.getString(TodoWidget.KEY_NOTE_TITLE, "To-Do List"));
                fallback.put("items", new JSONArray(prefs.getString(TodoWidget.KEY_ITEMS_JSON, "[]")));
                fallback.put("folderId", "checklists");
                fallback.put("isPinned", false);
                fallback.put("createdAt", System.currentTimeMillis());
                fallback.put("updatedAt", System.currentTimeMillis());
                return fallback;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static JSONObject buildEncryptedDbPayload(JSONObject noteObj, String userId, String e2eeKeyB64) throws Exception {
        String id = noteObj.getString("id");
        String title = noteObj.optString("title", "To-Do List");
        if (title.trim().isEmpty()) title = "To-Do List";
        JSONArray items = noteObj.optJSONArray("items");
        if (items == null) items = new JSONArray();

        StringBuilder activeLis = new StringBuilder();
        StringBuilder completedLis = new StringBuilder();
        int doneCount = 0;
        int totalCount = 0;

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String text = item.optString("text", "").trim();
            if (text.isEmpty()) continue;
            boolean checked = item.optBoolean("checked", false);
            totalCount++;

            if (checked) {
                doneCount++;
                completedLis
                    .append("<li data-type=\"taskItem\" data-checked=\"true\"><label><input type=\"checkbox\" checked=\"checked\"><span></span></label><div><p>")
                    .append(escapeHtml(text))
                    .append("</p></div></li>");
            } else {
                activeLis
                    .append("<li data-type=\"taskItem\" data-checked=\"false\"><label><input type=\"checkbox\"><span></span></label><div><p>")
                    .append(escapeHtml(text))
                    .append("</p></div></li>");
            }
        }

        String combinedLis = activeLis.toString() + completedLis.toString();
        if (combinedLis.isEmpty()) {
            combinedLis = "<li data-type=\"taskItem\" data-checked=\"false\"><label><input type=\"checkbox\"><span></span></label><div><p></p></div></li>";
        }

        String html = "<h1>" + escapeHtml(title) + "</h1><ul data-type=\"taskList\">" + combinedLis + "</ul>";
        String plainTextPreview = totalCount > 0 ? (doneCount + "/" + totalCount + " completed") : "0 tasks completed";

        JSONObject bundledContent = new JSONObject();
        bundledContent.put("html", html);
        bundledContent.put("isChecklist", true);

        SecretKeySpec aesKey = null;
        if (e2eeKeyB64 != null && !e2eeKeyB64.isEmpty()) {
            byte[] rawKey = Base64.decode(e2eeKeyB64, Base64.DEFAULT);
            if (rawKey != null && rawKey.length == 32) {
                aesKey = new SecretKeySpec(rawKey, "AES");
            }
        }

        String encTitle = encryptText(title, aesKey);
        String encContent = encryptText(bundledContent.toString(), aesKey);
        String encPreview = encryptText(plainTextPreview, aesKey);

        String folderId = noteObj.optString("folderId", "checklists");
        if (folderId.isEmpty() || "default".equals(folderId)) {
            folderId = "checklists";
        }
        long now = System.currentTimeMillis();
        long createdAt = noteObj.optLong("createdAt", now);
        long updatedAt = noteObj.optLong("updatedAt", now);
        if (updatedAt <= 0) updatedAt = now;
        if (createdAt <= 0) createdAt = updatedAt;

        JSONObject payload = new JSONObject();
        payload.put("id", id);
        payload.put("title", encTitle);
        payload.put("content", encContent);
        payload.put("plain_text_preview", encPreview);
        payload.put("folder_id", folderId);
        payload.put("is_pinned", noteObj.optBoolean("isPinned", false));
        payload.put("is_trash", false);
        payload.put("created_at", createdAt);
        payload.put("updated_at", updatedAt);
        payload.put("user_id", userId);
        return payload;
    }

    private static String encryptText(String plainText, SecretKeySpec key) throws Exception {
        if (key == null || plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        byte[] iv = new byte[12];
        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        String ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP);
        String cipherB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP);
        return ENCRYPTION_PREFIX + ivB64 + ":" + cipherB64;
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private static int postUpsertNote(String supabaseUrl, String anonKey, String accessToken, JSONObject payload) throws Exception {
        String baseUrl = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
        URL url = new URL(baseUrl + "/rest/v1/notes?on_conflict=id");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);
            conn.setRequestProperty("apikey", anonKey);
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal");

            JSONArray bodyArr = new JSONArray();
            bodyArr.put(payload);
            byte[] bodyBytes = bodyArr.toString().getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            return conn.getResponseCode();
        } finally {
            conn.disconnect();
        }
    }

    private static String refreshAuthToken(SharedPreferences prefs, String supabaseUrl, String anonKey, String refreshToken) {
        HttpURLConnection conn = null;
        try {
            String baseUrl = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
            URL url = new URL(baseUrl + "/auth/v1/token?grant_type=refresh_token");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);
            conn.setRequestProperty("apikey", anonKey);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            JSONObject body = new JSONObject();
            body.put("refresh_token", refreshToken);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                String resp = readStream(conn.getInputStream());
                JSONObject json = new JSONObject(resp);
                String newAccess = json.optString("access_token", "");
                String newRefresh = json.optString("refresh_token", refreshToken);
                long expiresIn = json.optLong("expires_in", 3600L);
                long expiresAt = json.optLong("expires_at", (System.currentTimeMillis() / 1000L) + expiresIn);
                if (!newAccess.isEmpty()) {
                    prefs.edit()
                        .putString(KEY_CLOUD_ACCESS_TOKEN, newAccess)
                        .putString(KEY_CLOUD_REFRESH_TOKEN, newRefresh)
                        .putLong(KEY_CLOUD_EXPIRES_AT, expiresAt)
                        .putBoolean(KEY_CLOUD_SESSION_REFRESHED, true)
                        .apply();
                    return newAccess;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed refreshing Supabase token in widget", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
