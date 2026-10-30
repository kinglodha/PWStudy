package com.study.pwjarvis;

import android.app.*;
import android.os.*;
import android.net.Uri;
import android.widget.*;
import android.view.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

public class PdfActivity extends Activity {
    @Override public void onCreate(Bundle b){ super.onCreate(b);
        TextView t=new TextView(this); t.setText("PDF viewer foundation. For local PDFs, choose a PDF using the Android file picker from the Offline Library. Full page rendering and file saving are in the next build step."); t.setPadding(24,24,24,24); setContentView(t);
    }
}
