package com.study.pwjarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/** Ordered storage shortcuts. Existing single-folder access is migrated without copying files. */
public final class PdfFolderShortcuts {
    private final SharedPreferences prefs;
    public PdfFolderShortcuts(Context c) {
        prefs = c.getSharedPreferences("pdf_manager", Context.MODE_PRIVATE);
    }
    public List<String> all() {
        List<String> values = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("saved_roots", "[]"));
            for (int i = 0; i < array.length(); i++) {
                String uri = array.optString(i, "");
                if (!uri.isEmpty() && !values.contains(uri)) values.add(uri);
            }
        } catch (Exception ignored) {}
        String legacy = prefs.getString("tree", null);
        if (legacy != null && !values.contains(legacy)) values.add(legacy);
        return values;
    }
    public String active() { return prefs.getString("tree", null); }
    public void activate(String uri) {
        List<String> values = all();
        if (!values.contains(uri)) values.add(uri);
        persist(values, uri);
    }
    public void remove(String uri) {
        List<String> values = all();
        values.remove(uri);
        String active = active();
        if (uri.equals(active)) active = values.isEmpty() ? null : values.get(0);
        persist(values, active);
        // Do not revoke access used by the organizer or delete any original documents.
    }
    private void persist(List<String> values, String active) {
        SharedPreferences.Editor edit = prefs.edit().putString("saved_roots", new JSONArray(values).toString());
        if (active == null) edit.remove("tree"); else edit.putString("tree", active);
        edit.apply();
    }
    public String label(String value) {
        try {
            String id = DocumentsContract.getTreeDocumentId(Uri.parse(value));
            int slash = id.lastIndexOf('/');
            int colon = id.lastIndexOf(':');
            String name = id.substring(Math.max(slash, colon) + 1);
            return name.isEmpty() ? "Storage" : name;
        } catch (Exception e) {
            return "Storage folder";
        }
    }
}
