package com.study.pwjarvis;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.widget.Toast;

/**
 * Narrow external entry point for documents opened from Android file managers.
 * The PDF reader remains private; no unrelated app screen is exported.
 */
public final class ExternalPdfActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent incoming = getIntent();
        Uri document = incoming == null ? null : extractPdfUri(incoming);
        if (document == null) {
            Toast.makeText(this, "Choose a PDF from your files", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            Intent open = new Intent(this, PdfActivity.class);
            open.setData(document);
            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // Carry the original grant through to the non-exported PDF reader.
            if ("content".equalsIgnoreCase(document.getScheme())) {
                open.setClipData(ClipData.newUri(getContentResolver(), "PDF", document));
            }
            startActivity(open);
        } catch (RuntimeException error) {
            Toast.makeText(this, "Unable to open this PDF", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private static Uri extractPdfUri(Intent intent) {
        String action = intent.getAction();
        Uri uri;
        if (Intent.ACTION_VIEW.equals(action)) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            Parcelable stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            uri = stream instanceof Uri ? (Uri) stream : null;
        } else {
            return null;
        }
        if (uri == null) return null;
        String scheme = uri.getScheme();
        // Do not allow remote URLs or arbitrary schemes into a local reader.
        if (!"content".equalsIgnoreCase(scheme) && !"file".equalsIgnoreCase(scheme)) {
            return null;
        }
        String type = intent.getType();
        if (type != null && !("application/pdf".equalsIgnoreCase(type)
                || "application/octet-stream".equalsIgnoreCase(type))) {
            return null;
        }
        return uri;
    }
}
