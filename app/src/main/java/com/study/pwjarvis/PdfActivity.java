package com.study.pwjarvis;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.ScaleGestureDetector;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class PdfActivity extends Activity {
    private ParcelFileDescriptor descriptor;
    private PdfRenderer renderer;
    private PdfRenderer.Page page;
    private ImageView image;
    private TextView pageLabel;
    private int pageIndex = 0;
    private float zoom = 1f;
    private ScaleGestureDetector scaleDetector;

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 20, 26));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        Button previous = new Button(this); previous.setText("Previous");
        Button next = new Button(this); next.setText("Next");
        pageLabel = new TextView(this); pageLabel.setTextColor(Color.WHITE); pageLabel.setGravity(Gravity.CENTER);
        controls.addView(previous, new LinearLayout.LayoutParams(0, dp(48), 1));
        controls.addView(pageLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
        controls.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(controls);

        ScrollView scroll = new ScrollView(this);
        image = new ImageView(this);
        image.setBackgroundColor(Color.WHITE);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        scroll.addView(image);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        previous.setOnClickListener(v -> showPage(pageIndex - 1));
        next.setOnClickListener(v -> showPage(pageIndex + 1));
        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoom = Math.max(1f, Math.min(4f, zoom * detector.getScaleFactor()));
                image.setScaleX(zoom);
                image.setScaleY(zoom);
                return true;
            }
        });
        image.setOnTouchListener((v, event) -> { scaleDetector.onTouchEvent(event); return true; });

        try {
            String path = getIntent().getStringExtra("file_path");
            if (path == null) throw new Exception("PDF file path missing");
            descriptor = ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            showPage(0);
        } catch (Exception e) {
            Toast.makeText(this, "PDF नहीं खुल पाया: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void showPage(int index) {
        if (renderer == null || index < 0 || index >= renderer.getPageCount()) return;
        try {
            if (page != null) page.close();
            pageIndex = index;
            page = renderer.openPage(pageIndex);
            int width = Math.max(1, getResources().getDisplayMetrics().widthPixels - dp(24));
            int height = Math.max(1, (int)(width * (page.getHeight() / (float) page.getWidth())));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            image.setImageBitmap(bitmap);
            image.setScaleX(1f); image.setScaleY(1f); zoom = 1f;
            pageLabel.setText((pageIndex + 1) + " / " + renderer.getPageCount());
        } catch (Exception e) {
            Toast.makeText(this, "Page render failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onDestroy() {
        try { if (page != null) page.close(); } catch (Exception ignored) {}
        try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
        try { if (descriptor != null) descriptor.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
