package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.text.TextUtils;
import android.graphics.Bitmap;
import android.net.Uri;
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
import android.widget.HorizontalScrollView;
import java.util.List;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class PdfActivity extends Activity {
    private ParcelFileDescriptor descriptor;
    private PdfRenderer renderer;
    private PdfRenderer.Page page;
    private Bitmap renderedBitmap;
    private ImageView image;
    private TextView pageLabel;
    private TextView documentTitle;
    private SeekBar pageSeek;
    private LinearLayout topBar;
    private HorizontalScrollView topScroll;
    private String documentId;
    private boolean savedFitSetting;
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
        savedFitSetting = PdfReaderSettings.fitWidth(this);
        fitWidth = savedFitSetting;
        themeMode = PdfReaderSettings.theme(this);
        updateScreenAwake();
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
        topScroll = new HorizontalScrollView(this);
        topScroll.setHorizontalScrollBarEnabled(false);
        topScroll.setFillViewport(true);
        topScroll.setBackgroundColor(Color.argb(240, 9, 15, 27));
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(7), dp(3), dp(7), dp(3));
        topScroll.addView(topBar, new FrameLayout.LayoutParams(-2, dp(54)));
        root.addView(topScroll, new FrameLayout.LayoutParams(-1, dp(54), Gravity.TOP));

        documentTitle = new TextView(this);
        documentTitle.setText("PDF Reader");
        documentTitle.setSingleLine(true);
        documentTitle.setEllipsize(TextUtils.TruncateAt.END);
        documentTitle.setGravity(Gravity.CENTER_VERTICAL);
        documentTitle.setTextColor(Color.WHITE);
        documentTitle.setTextSize(15);
        documentTitle.setMinWidth(dp(115));
        documentTitle.setPadding(dp(8), 0, dp(6), 0);
        documentTitle.setOnLongClickListener(v -> {
            startActivity(new Intent(this, PdfToolbarEditorActivity.class));
            return true;
        });

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(5), dp(5), dp(5), dp(5));
        controls.setBackgroundColor(Color.argb(225, 10, 13, 20));
        root.addView(controls, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        pageLabel = new TextView(this);
        pageLabel.setTextColor(Color.WHITE);
        pageLabel.setGravity(Gravity.CENTER);
        pageLabel.setTextSize(14);
        pageSeek = new SeekBar(this);
        pageSeek.setContentDescription("Jump to page");
        pageSeek.setMax(0);
        pageSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean fromUser) {
                if (fromUser && renderer != null)
                    pageLabel.setText("Page " + (value + 1) + " / " + renderer.getPageCount());
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {
                controlHandler.removeCallbacks(hideControlsTask);
            }
            @Override public void onStopTrackingTouch(SeekBar seek) {
                if (renderer != null) showPage(seek.getProgress());
                scheduleControlsHide();
            }
        });
        rebuildToolbars();
        setContentView(root);
        applyTheme(false);
        scheduleControlsHide();
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
            @Override public boolean onSingleTapUp(MotionEvent e) {
                setControlsVisible(!controlsVisible);
                scheduleControlsHide();
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                setControlsVisible(true);
                setZoomAt(zoom > 1.6f ? 1f : 2.5f, e.getX(), e.getY());
                scheduleControlsHide();
                return true;
            }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (!PdfReaderSettings.swipeNavigation(PdfActivity.this) || e1 == null
                        || e2 == null || hadMultiplePointers) return false;
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
            Uri uri = getIntent().getData();
            if (uri != null && "content".equalsIgnoreCase(uri.getScheme())) {
                documentId = uri.toString();
                String name = uri.getLastPathSegment();
                documentTitle.setText(name == null ? "PDF Document" : name);
                // SAF and Android Downloads files must be opened using their
                // content URI, not by constructing an inaccessible disk path.
                descriptor = getContentResolver().openFileDescriptor(uri, "r");
            } else {
                String path = getIntent().getStringExtra("file_path");
                if (path == null && uri != null && "file".equalsIgnoreCase(uri.getScheme()))
                    path = uri.getPath();
                if (path == null) throw new Exception("PDF path or URI missing");
                File file = new File(path);
                if (!file.isFile()) throw new Exception("PDF file not found");
                documentId = file.getAbsolutePath();
                documentTitle.setText(file.getName());
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            }
            if (descriptor == null) throw new Exception("Cannot access selected PDF");
            renderer = new PdfRenderer(descriptor);
            if (renderer.getPageCount() == 0) throw new Exception("PDF contains no pages");
            pageSeek.setMax(renderer.getPageCount() - 1);
            int initialPage = state == null
                    ? (PdfReaderSettings.rememberPage(this)
                        ? PdfReaderSettings.lastPage(this, documentId) : 0)
                    : state.getInt("page", 0);
            if(state==null&&getIntent().hasExtra("initial_page"))initialPage=getIntent().getIntExtra("initial_page",initialPage);
            showPage(Math.max(0, Math.min(renderer.getPageCount() - 1, initialPage)));
        } catch (Exception e) {
            Toast.makeText(this, "PDF नहीं खुल पाया: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /** Rebuild the visible toolbar using saved slot, order, size, color and label. */
    private void rebuildToolbars() {
        if (topBar == null || controls == null || pageSeek == null) return;
        topBar.removeAllViews();
        controls.removeAllViews();
        List<String> topItems = PdfToolbarLayout.items(this, 0, false);
        boolean addedTitle = false;
        for (String id : topItems) {
            if (!addedTitle && "settings".equals(id)) {
                addDocumentTitle();
                addedTitle = true;
            }
            addConfiguredItem(topBar, id, true, topItems.size());
            if (!addedTitle && "back".equals(id)) {
                addDocumentTitle();
                addedTitle = true;
            }
        }
        if (!addedTitle) addDocumentTitle();

        for (int slot = 1; slot <= 4; slot++) {
            List<String> items = PdfToolbarLayout.items(this, slot, false);
            if (items.isEmpty()) continue;
            HorizontalScrollView scroller = new HorizontalScrollView(this);
            scroller.setFillViewport(true);
            scroller.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumWidth(getResources().getDisplayMetrics().widthPixels - dp(10));
            for (String id : items) addConfiguredItem(row, id, false, items.size());
            scroller.addView(row, new FrameLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
            outer.bottomMargin = dp(2);
            controls.addView(scroller, outer);
        }
        updatePageLabel();
    }

    private void addDocumentTitle() {
        if (documentTitle.getParent() instanceof ViewGroup)
            ((ViewGroup) documentTitle.getParent()).removeView(documentTitle);
        topBar.addView(documentTitle, new LinearLayout.LayoutParams(0, dp(46), 1f));
    }

    private void addConfiguredItem(LinearLayout row, String id, boolean top, int count) {
        if ("page_indicator".equals(id)) {
            if (pageLabel.getParent() instanceof ViewGroup)
                ((ViewGroup) pageLabel.getParent()).removeView(pageLabel);
            int height = itemHeight(id);
            pageLabel.setTextSize(PdfToolbarLayout.size(this, id) == 2 ? 16 : 14);
            LinearLayout.LayoutParams params = top
                    ? new LinearLayout.LayoutParams(dp(145), height)
                    : new LinearLayout.LayoutParams(0, height, 1f);
            row.addView(pageLabel, params);
            return;
        }
        if ("page_slider".equals(id)) {
            if (pageSeek.getParent() instanceof ViewGroup)
                ((ViewGroup) pageSeek.getParent()).removeView(pageSeek);
            LinearLayout.LayoutParams params = top
                    ? new LinearLayout.LayoutParams(dp(150), itemHeight(id))
                    : new LinearLayout.LayoutParams(0, itemHeight(id), 1f);
            row.addView(pageSeek, params);
            return;
        }

        Button button = new Button(this);
        button.setText(PdfToolbarLayout.label(this, id));
        button.setContentDescription(PdfToolbarLayout.name(id));
        button.setTextSize(PdfToolbarLayout.size(this, id) == 0 ? 11
                : PdfToolbarLayout.size(this, id) == 2 ? 15 : 12);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinWidth(dp(46));
        button.setPadding(dp(5), 0, dp(5), 0);
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.TRANSPARENT);
        outline.setCornerRadius(dp(PdfToolbarLayout.size(this, id) == 2 ? 14 : 10));
        outline.setStroke(dp(1), PdfToolbarLayout.accent(this, id));
        button.setBackground(outline);
        button.setOnClickListener(clickAction(id));
        button.setHapticFeedbackEnabled(false);
        // Every control can jump directly to its editor by long-pressing it.
        button.setOnLongClickListener(v -> {
            startActivity(new Intent(this, PdfToolbarEditorActivity.class));
            return true;
        });
        LinearLayout.LayoutParams params = top
                ? new LinearLayout.LayoutParams(-2, itemHeight(id))
                : new LinearLayout.LayoutParams(0, itemHeight(id), 1f);
        params.setMargins(dp(2), dp(1), dp(2), dp(1));
        row.addView(button, params);
    }

    private int itemHeight(String id) {
        int size = PdfToolbarLayout.size(this, id);
        return dp(size == 0 ? 36 : size == 2 ? 50 : 42);
    }

    private View.OnClickListener clickAction(String id) {
        switch (id) {
            case "back": return v -> finish();
            case "settings": return v -> {
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.putExtra("section", "pdf");
                startActivity(intent);
            };
            case "zoom_out": return v -> setZoom(zoom / 1.25f);
            case "zoom_reset": return v -> setZoom(1f);
            case "zoom_in": return v -> setZoom(zoom * 1.25f);
            case "fit_width": return v -> setFitWidth();
            case "fit_page": return v -> setFitPage();
            case "rotate": return v -> { rotation = (rotation + 90) % 360; renderPage(); };
            case "go_to": return v -> showJumpDialog();
            case "full": return v -> toggleFullscreen();
            case "theme": return v -> cycleTheme();
            case "previous": return v -> showPage(pageIndex - 1);
            case "next": return v -> showPage(pageIndex + 1);
            case "study_tools": return v -> openStudyTools();
            default: return v -> {};
        }
    }

    /** Open offline document tools without changing the existing PDF reader state. */
    private void openStudyTools() {
        if (documentId == null || renderer == null) return;
        Intent tools = new Intent(this, PdfStudyToolsActivity.class);
        tools.putExtra("document_id", documentId);
        tools.putExtra("page_index", pageIndex);
        tools.putExtra("page_count", renderer.getPageCount());
        startActivityForResult(tools, 710);
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 710 && resultCode == RESULT_OK && data != null && renderer != null) {
            int target = data.getIntExtra("go_to_page", -1);
            if (target >= 0 && target < renderer.getPageCount()) showPage(target);
        }
    }

    private void scheduleControlsHide() {
        controlHandler.removeCallbacks(hideControlsTask);
        int seconds = PdfReaderSettings.controlsDelay(this);
        if (controlsVisible && seconds > 0)
            controlHandler.postDelayed(hideControlsTask, seconds * 1000L);
    }
    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        if (controls != null) controls.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (topScroll != null) topScroll.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) controlHandler.removeCallbacks(hideControlsTask);
    }

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
        updatePageLabel();
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
        updatePageLabel();
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
        PdfReaderSettings.setTheme(this, themeMode);
        applyTheme(true);
    }

    private void applyTheme(boolean announce) {
        if (image == null) return;
        if (themeMode == 0) {
            image.setColorFilter(null);
            image.setBackgroundColor(Color.WHITE);
        } else if (themeMode == 1) {
            ColorMatrix sepia = new ColorMatrix(new float[]{
                    .393f,.769f,.189f,0,0,
                    .349f,.686f,.168f,0,0,
                    .272f,.534f,.131f,0,0,
                    0,0,0,1,0});
            image.setColorFilter(new ColorMatrixColorFilter(sepia));
            image.setBackgroundColor(Color.rgb(244, 230, 200));
        } else {
            ColorMatrix dark = new ColorMatrix(new float[]{
                    -1,0,0,0,255,
                    0,-1,0,0,255,
                    0,0,-1,0,255,
                    0,0,0,1,0});
            image.setColorFilter(new ColorMatrixColorFilter(dark));
            image.setBackgroundColor(Color.DKGRAY);
        }
        if (announce) {
            Toast.makeText(this,
                    themeMode == 0 ? "Normal theme" :
                    themeMode == 1 ? "Sepia theme" : "Night / inverted theme",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePageLabel() {
        if (renderer == null || pageLabel == null) return;
        pageLabel.setText("Page " + (pageIndex + 1) + " / " + renderer.getPageCount()
                + "  •  " + Math.round(zoom * 100f) + "%");
    }

    private void updateScreenAwake() {
        if (PdfReaderSettings.keepAwake(this))
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override protected void onResume() {
        super.onResume();
        int preferredTheme = PdfReaderSettings.theme(this);
        if (themeMode != preferredTheme) {
            themeMode = preferredTheme;
            applyTheme(false);
        }
        boolean preferredFit = PdfReaderSettings.fitWidth(this);
        if (savedFitSetting != preferredFit) {
            savedFitSetting = preferredFit;
            if (renderer != null) {
                if (preferredFit) setFitWidth();
                else setFitPage();
            } else fitWidth = preferredFit;
        }
        updateScreenAwake();
        rebuildToolbars();
        if (controlsVisible) {
            setControlsVisible(true);
            scheduleControlsHide();
        }
    }

    @Override protected void onStop() {
        if (renderer != null) PdfReaderSettings.savePage(this, documentId, pageIndex);
        super.onStop();
    }

    private void showPage(int index) {
        if (renderer == null || index < 0 || index >= renderer.getPageCount()) return;
        pageIndex = index;
        renderPage();
        PdfReaderSettings.savePage(this, documentId, pageIndex);
        PdfReadingProgress.record(this, documentId, pageIndex, renderer.getPageCount());
    }

    private void renderPage() {
        if (renderer == null) return;
        try {
            if (page != null) page.close();
            page = renderer.openPage(pageIndex);
            // Bound bitmap allocation for oversized scans and unusually long
            // pages. The previous unbounded render could crash on large PDFs.
            float aspect = page.getHeight() / (float) Math.max(1, page.getWidth());
            int desiredWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels * 2);
            int width = Math.max(1, Math.min(3072, Math.min(desiredWidth,
                    (int) Math.sqrt(6000000f / Math.max(.01f, aspect)))));
            int height = Math.max(1, Math.round(width * aspect));
            if (height > 4096) {
                width = Math.max(1, Math.round(width * (4096f / height)));
                height = 4096;
            }
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
            Bitmap previous = renderedBitmap;
            renderedBitmap = bitmap;
            if (previous != null && previous != bitmap && !previous.isRecycled())
                previous.recycle();
            zoom = 1f;
            matrixReady = false;
            updateImageMatrix(0, 0);
            if (pageSeek != null) pageSeek.setProgress(pageIndex);
            updatePageLabel();
        } catch (Exception e) {
            Toast.makeText(this, "Page render failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("page", pageIndex);
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        controlHandler.removeCallbacks(hideControlsTask);
        try { if (page != null) page.close(); } catch (Exception ignored) {}
        try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
        try { if (descriptor != null) descriptor.close(); } catch (Exception ignored) {}
        image.setImageDrawable(null);
        if (renderedBitmap != null && !renderedBitmap.isRecycled()) renderedBitmap.recycle();
        renderedBitmap = null;
        super.onDestroy();
    }
}

