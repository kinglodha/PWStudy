package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
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
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
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
    private LinearLayout controls;
    private final Handler controlHandler = new Handler(Looper.getMainLooper());
    private boolean controlsVisible = true;
    private final Runnable hideControlsTask = () -> setControlsVisible(false);
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private final Matrix imageMatrix = new Matrix();
    private float baseScale = 1f;
    private float imageScale = 1f;
    private float offsetX = 0f;
    private float offsetY = 0f;
    private boolean matrixReady = false;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private float lastTouchX;
    private float lastTouchY;
    private float lastScaleFocusX;
    private float lastScaleFocusY;
    private boolean hadMultiplePointers;
    private boolean gesturePannedX;
    private boolean gesturePannedY;
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(12, 16, 25));
        image = new ImageView(this);
        image.setBackgroundColor(Color.WHITE);
        image.setScaleType(ImageView.ScaleType.MATRIX);
        image.setAdjustViewBounds(false);
        image.setHapticFeedbackEnabled(false);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        image.addOnLayoutChangeListener((v, left, top, right, bottom,
                                         oldLeft, oldTop, oldRight, oldBottom) -> {
            int oldWidth = oldRight - oldLeft;
            int oldHeight = oldBottom - oldTop;
            if (right - left != oldWidth || bottom - top != oldHeight) {
                updateImageMatrix(oldWidth, oldHeight);
            }
        });
        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(5), dp(5), dp(5), dp(5));
        controls.setBackgroundColor(Color.argb(225, 10, 13, 20));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(controls, cp);
        LinearLayout tools = new LinearLayout(this); tools.setGravity(Gravity.CENTER);
        addTool(tools, "−", v -> setZoom(zoom / 1.25f), 0);
        addTool(tools, "100%", v -> setZoom(1f), 0);
        addTool(tools, "+", v -> setZoom(zoom * 1.25f), 0);
        addTool(tools, "Fit Width", v -> setFitWidth(), 1);
        addTool(tools, "Fit Page", v -> setFitPage(), 1);
        controls.addView(tools, new LinearLayout.LayoutParams(-1, dp(44)));
        LinearLayout row2 = new LinearLayout(this); row2.setGravity(Gravity.CENTER);
        addTool(row2, "Rotate", v -> { rotation = (rotation + 90) % 360; renderPage(); }, 1);
        addTool(row2, "Go to", v -> showJumpDialog(), 1);
        addTool(row2, "Full", v -> toggleFullscreen(), 1);
        addTool(row2, "Theme", v -> cycleTheme(), 1);
        controls.addView(row2, new LinearLayout.LayoutParams(-1, dp(44)));
        LinearLayout navigation = new LinearLayout(this); navigation.setGravity(Gravity.CENTER);
        addTool(navigation, "Previous", v -> showPage(pageIndex - 1), 1);
        pageLabel = new TextView(this); pageLabel.setTextColor(Color.WHITE);
        pageLabel.setGravity(Gravity.CENTER); pageLabel.setTextSize(14);
        navigation.addView(pageLabel, new LinearLayout.LayoutParams(0, dp(42), 1));
        addTool(navigation, "Next", v -> showPage(pageIndex + 1), 1);
        controls.addView(navigation, new LinearLayout.LayoutParams(-1, dp(44)));
        setContentView(root); scheduleControlsHide();
        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                hadMultiplePointers = true;
                lastScaleFocusX = detector.getFocusX();
                lastScaleFocusY = detector.getFocusY();
                return true;
            }
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float focusX = detector.getFocusX();
                float focusY = detector.getFocusY();
                offsetX += focusX - lastScaleFocusX;
                offsetY += focusY - lastScaleFocusY;
                setZoomAt(zoom * detector.getScaleFactor(), focusX, focusY);
                lastScaleFocusX = focusX;
                lastScaleFocusY = focusY;
                return true;
            }
        });
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) { setControlsVisible(true); scheduleControlsHide(); return true; }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null || hadMultiplePointers) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > dp(55) && Math.abs(dx) > Math.abs(dy)
                        && !gesturePannedX && !canPanX()) {
                    if (dx < 0) showPage(pageIndex + 1); else showPage(pageIndex - 1);
                    return true;
                }
                if (Math.abs(dy) > dp(70) && Math.abs(dy) > Math.abs(dx)
                        && !gesturePannedY && !canPanY()) {
                    if (dy < 0) showPage(pageIndex + 1); else showPage(pageIndex - 1);
                    return true;
                }
                return false;
            }
        });
        image.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                hadMultiplePointers = false;
                gesturePannedX = false;
                gesturePannedY = false;
                activePointerId = event.getPointerId(0);
                lastTouchX = event.getX(0);
                lastTouchY = event.getY(0);
            } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
                hadMultiplePointers = true;
            }
            scaleDetector.onTouchEvent(event);
            if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() == 1
                    && !scaleDetector.isInProgress()) {
                int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex >= 0) {
                    float x = event.getX(pointerIndex);
                    float y = event.getY(pointerIndex);
                    boolean panX = canPanX();
                    boolean panY = canPanY();
                    if (panX || panY) {
                        if (panX) {
                            if (x != lastTouchX) gesturePannedX = true;
                            offsetX += x - lastTouchX;
                        }
                        if (panY) {
                            if (y != lastTouchY) gesturePannedY = true;
                            offsetY += y - lastTouchY;
                        }
                        applyImageMatrix();
                    }
                    lastTouchX = x;
                    lastTouchY = y;
                }
            } else if (action == MotionEvent.ACTION_POINTER_UP) {
                int liftedIndex = event.getActionIndex();
                int remainingIndex = liftedIndex == 0 ? 1 : 0;
                if (remainingIndex < event.getPointerCount()) {
                    activePointerId = event.getPointerId(remainingIndex);
                    lastTouchX = event.getX(remainingIndex);
                    lastTouchY = event.getY(remainingIndex);
                } else {
                    activePointerId = MotionEvent.INVALID_POINTER_ID;
                }
            }
            gestureDetector.onTouchEvent(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                activePointerId = MotionEvent.INVALID_POINTER_ID;
            }
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
        button.setHapticFeedbackEnabled(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = weight == 1
                ? new LinearLayout.LayoutParams(0, dp(42), 1)
                : new LinearLayout.LayoutParams(-2, dp(42));
        params.setMargins(dp(2), 0, dp(2), 0);
        parent.addView(button, params);
    }

    private void scheduleControlsHide() { controlHandler.removeCallbacks(hideControlsTask); controlHandler.postDelayed(hideControlsTask, 4000); }
    private void setControlsVisible(boolean visible) { controlsVisible = visible; if (controls != null) controls.setVisibility(visible ? View.VISIBLE : View.GONE); }

    private void setZoom(float value) {
        setZoomAt(value, image.getWidth() / 2f, image.getHeight() / 2f);
    }

    private void setZoomAt(float value, float focusX, float focusY) {
        fitWidth = false;
        if (!matrixReady) {
            zoom = Math.max(0.5f, Math.min(4f, value));
            return;
        }
        Drawable drawable = image.getDrawable();
        float widthZoom = image.getWidth() / (float) drawable.getIntrinsicWidth() / baseScale;
        float nextZoom = Math.max(0.5f, Math.min(Math.max(4f, widthZoom), value));
        float factor = nextZoom / zoom;
        offsetX = focusX - (focusX - offsetX) * factor;
        offsetY = focusY - (focusY - offsetY) * factor;
        zoom = nextZoom;
        imageScale = baseScale * zoom;
        applyImageMatrix();
    }

    private void setFitWidth() {
        fitWidth = true;
        zoom = 1f;
        matrixReady = false;
        updateImageMatrix(0, 0);
    }

    private void setFitPage() {
        fitWidth = false;
        zoom = 1f;
        matrixReady = false;
        updateImageMatrix(0, 0);
    }

    // Scale is relative to Fit Page. Keeping the same document point at the
    // viewport center avoids jumps when fullscreen changes the available size.
    private void updateImageMatrix(int oldWidth, int oldHeight) {
        if (image == null || image.getDrawable() == null) return;
        Drawable drawable = image.getDrawable();
        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();
        int viewWidth = image.getWidth();
        int viewHeight = image.getHeight();
        if (drawableWidth <= 0 || drawableHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return;

        boolean preserveCenter = matrixReady && oldWidth > 0 && oldHeight > 0;
        float centerX = preserveCenter ? (oldWidth / 2f - offsetX) / imageScale : drawableWidth / 2f;
        float centerY = preserveCenter ? (oldHeight / 2f - offsetY) / imageScale : drawableHeight / 2f;
        baseScale = Math.min(viewWidth / (float) drawableWidth, viewHeight / (float) drawableHeight);
        if (fitWidth) zoom = viewWidth / (float) drawableWidth / baseScale;
        imageScale = baseScale * zoom;
        if (preserveCenter) {
            offsetX = viewWidth / 2f - centerX * imageScale;
            offsetY = viewHeight / 2f - centerY * imageScale;
        } else {
            offsetX = (viewWidth - drawableWidth * imageScale) / 2f;
            offsetY = fitWidth && drawableHeight * imageScale > viewHeight
                    ? 0f : (viewHeight - drawableHeight * imageScale) / 2f;
        }
        matrixReady = true;
        applyImageMatrix();
    }

    private boolean canPanX() {
        if (!matrixReady || image.getDrawable() == null) return false;
        return image.getDrawable().getIntrinsicWidth() * imageScale > image.getWidth() + 0.5f;
    }

    private boolean canPanY() {
        if (!matrixReady || image.getDrawable() == null) return false;
        return image.getDrawable().getIntrinsicHeight() * imageScale > image.getHeight() + 0.5f;
    }

    private void applyImageMatrix() {
        if (!matrixReady || image.getDrawable() == null) return;
        Drawable drawable = image.getDrawable();
        float contentWidth = drawable.getIntrinsicWidth() * imageScale;
        float contentHeight = drawable.getIntrinsicHeight() * imageScale;
        int viewWidth = image.getWidth();
        int viewHeight = image.getHeight();
        offsetX = contentWidth <= viewWidth ? (viewWidth - contentWidth) / 2f
                : Math.max(viewWidth - contentWidth, Math.min(0f, offsetX));
        offsetY = contentHeight <= viewHeight ? (viewHeight - contentHeight) / 2f
                : Math.max(viewHeight - contentHeight, Math.min(0f, offsetY));
        imageMatrix.setValues(new float[]{imageScale, 0f, offsetX, 0f, imageScale, offsetY, 0f, 0f, 1f});
        image.setImageMatrix(imageMatrix);
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
            zoom = 1f;
            matrixReady = false;
            updateImageMatrix(0, 0);
            pageLabel.setText((pageIndex + 1) + " / " + renderer.getPageCount());
        } catch (Exception e) {
            Toast.makeText(this, "Page render failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onDestroy() {
        controlHandler.removeCallbacks(hideControlsTask);
        try { if (page != null) page.close(); } catch (Exception ignored) {}
        try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
        try { if (descriptor != null) descriptor.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}

