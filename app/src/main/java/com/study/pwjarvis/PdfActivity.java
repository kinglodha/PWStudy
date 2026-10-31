package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private int rotation = 0;
    private int themeMode = 0;
    private float zoom = 1f;
    private boolean fullscreen = false;
    private boolean fitWidth = false;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(6), dp(6), dp(6), dp(6));
        root.setBackgroundColor(Color.rgb(12, 16, 25));

        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER);
        addTool(tools, "−", v -> setZoom(zoom / 1.25f), 0);
        addTool(tools, "100%", v -> setZoom(1f), 0);
        addTool(tools, "+", v -> setZoom(zoom * 1.25f), 0);
        addTool(tools, "Fit Width", v -> setFitWidth(), 1);
        addTool(tools, "Fit Page", v -> setFitPage(), 1);
        addTool(tools, "Rotate", v -> { rotation = (rotation + 90) % 360; renderPage(); }, 1);
        addTool(tools, "Go to", v -> showJumpDialog(), 1);
        addTool(tools, "Full", v -> toggleFullscreen(), 0);
        root.addView(tools, new LinearLayout.LayoutParams(-1, dp(48)));

        image = new ImageView(this);
        image.setBackgroundColor(Color.WHITE);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(false);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, 0, 1);
        imageParams.topMargin = dp(5);
        imageParams.bottomMargin = dp(5);
        root.addView(image, imageParams);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        addTool(navigation, "Previous", v -> showPage(pageIndex - 1), 1);
        pageLabel = new TextView(this);
        pageLabel.setTextColor(Color.WHITE);
        pageLabel.setGravity(Gravity.CENTER);
        pageLabel.setTextSize(14);
        navigation.addView(pageLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
        addTool(navigation, "Next", v -> showPage(pageIndex + 1), 1);
        root.addView(navigation, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        addTool(bottom, "Theme", v -> cycleTheme(), 0);
        TextView swipeHint = new TextView(this);
        swipeHint.setText("Swipe left/right to change page");
        swipeHint.setTextColor(Color.LTGRAY);
        swipeHint.setTextSize(11);
        swipeHint.setGravity(Gravity.CENTER);
        bottom.addView(swipeHint, new LinearLayout.LayoutParams(0, dp(34), 1));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(34)));
        setContentView(root);

        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                setZoom(zoom * detector.getScaleFactor());
                return true;
            }
        });
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > dp(55) && Math.abs(dx) > Math.abs(dy)) {
                    if (dx < 0) showPage(pageIndex + 1); else showPage(pageIndex - 1);
                    return true;
                }
                if (Math.abs(dy) > dp(70) && Math.abs(dy) > Math.abs(dx) && zoom <= 1.05f) {
                    if (dy < 0) showPage(pageIndex + 1); else showPage(pageIndex - 1);
                    return true;
                }
                return false;
            }
        });
        image.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            return true;
        });

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

    private void addTool(LinearLayout parent, String label, View.OnClickListener listener, int weight) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.TRANSPARENT);
        outline.setCornerRadius(dp(10));
        outline.setStroke(dp(1), Color.rgb(85, 130, 180));
        button.setBackground(outline);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = weight == 1
                ? new LinearLayout.LayoutParams(0, dp(42), 1)
                : new LinearLayout.LayoutParams(-2, dp(42));
        params.setMargins(dp(2), 0, dp(2), 0);
        parent.addView(button, params);
    }

    private void setZoom(float value) {
        zoom = Math.max(0.5f, Math.min(4f, value));
        fitWidth = false;
        if (image != null) {
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setScaleX(zoom);
            image.setScaleY(zoom);
        }
    }

    private void setFitWidth() {
        fitWidth = true;
        zoom = 1f;
        if (image != null) {
            image.setScaleType(ImageView.ScaleType.FIT_XY);
            image.setScaleX(1f);
            image.setScaleY(1f);
        }
    }

    private void setFitPage() {
        fitWidth = false;
        zoom = 1f;
        if (image != null) {
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setScaleX(1f);
            image.setScaleY(1f);
        }
    }

    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        if (fullscreen) getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void showJumpDialog() {
        if (renderer == null) return;
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Page number (1-" + renderer.getPageCount() + ")");
        new AlertDialog.Builder(this).setTitle("Go to page").setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Go", (d, w) -> {
                    try {
                        int target = Integer.parseInt(input.getText().toString().trim()) - 1;
                        if (target < 0 || target >= renderer.getPageCount()) {
                            Toast.makeText(this, "Page number out of range", Toast.LENGTH_SHORT).show();
                        } else showPage(target);
                    } catch (Exception e) {
                        Toast.makeText(this, "Enter a valid page number", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void cycleTheme() {
        themeMode = (themeMode + 1) % 3;
        if (themeMode == 0) {
            image.setColorFilter(null);
            image.setBackgroundColor(Color.WHITE);
            Toast.makeText(this, "Normal theme", Toast.LENGTH_SHORT).show();
        } else if (themeMode == 1) {
            ColorMatrix sepia = new ColorMatrix(new float[]{
                    .393f,.769f,.189f,0,0,
                    .349f,.686f,.168f,0,0,
                    .272f,.534f,.131f,0,0,
                    0,0,0,1,0});
            image.setColorFilter(new ColorMatrixColorFilter(sepia));
            image.setBackgroundColor(Color.rgb(244, 230, 200));
            Toast.makeText(this, "Sepia theme", Toast.LENGTH_SHORT).show();
        } else {
            ColorMatrix dark = new ColorMatrix(new float[]{
                    -1,0,0,0,255,
                    0,-1,0,0,255,
                    0,0,-1,0,255,
                    0,0,0,1,0});
            image.setColorFilter(new ColorMatrixColorFilter(dark));
            image.setBackgroundColor(Color.DKGRAY);
            Toast.makeText(this, "Dark / inverted theme", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPage(int index) {
        if (renderer == null || index < 0 || index >= renderer.getPageCount()) return;
        pageIndex = index;
        renderPage();
    }

    private void renderPage() {
        if (renderer == null) return;
        try {
            if (page != null) page.close();
            page = renderer.openPage(pageIndex);
            int width = Math.max(1, getResources().getDisplayMetrics().widthPixels - dp(20));
            int height = Math.max(1, (int)(width * (page.getHeight() / (float) page.getWidth())));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) bitmap.recycle();
                bitmap = rotated;
            }
            image.setImageBitmap(bitmap);
            if (fitWidth) setFitWidth(); else setFitPage();
            pageLabel.setText((pageIndex + 1) + " / " + renderer.getPageCount());
        } catch (Exception e) {
            Toast.makeText(this, "Page render failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onDestroy() {
        try { if (page != null) page.close(); } catch (Exception ignored) {}
        try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
        try { if (descriptor != null) descriptor.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
