package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local video playback; the Activity owns the player, never a WebView. */
public class PlayerActivity extends Activity {
    private static final String KEY_POSITION = "position";
    private static final String KEY_PLAY = "play";
    private static final String KEY_SPEED = "speed";
    private static final String KEY_MUTED = "muted";
    private static final String KEY_REPEAT = "repeat";
    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout actionRow;
    private View nightShade;
    private Button lockButton;
    private boolean locked;
    private boolean night;
    private boolean muted;
    private boolean resumePlay = true;
    private long resumePosition;
    private float speed = 1f;
    private int repeatMode = Player.REPEAT_MODE_OFF;
    private String videoPath;
    private Uri videoUri;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final Runnable sleepAction = () -> {
        if (player != null) player.pause();
        toast("Sleep timer finished; playback paused");
    };

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + .5f);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Do not force an orientation at startup: users can rotate without a
        // forced activity restart or losing ExoPlayer state.
        videoPath = getIntent().getStringExtra("file_path");
        videoUri = getIntent().getData();
        if (videoUri == null && (videoPath == null || !new File(videoPath).isFile())) {
            toast("Video file not found. Open it from your PWStudy library.");
            finish();
            return;
        }
        if (state != null) {
            resumePosition = state.getLong(KEY_POSITION, 0L);
            resumePlay = state.getBoolean(KEY_PLAY, true);
            speed = state.getFloat(KEY_SPEED, 1f);
            muted = state.getBoolean(KEY_MUTED, false);
            repeatMode = state.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setKeepScreenOn(true);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        nightShade = new View(this);
        nightShade.setBackgroundColor(Color.BLACK);
        nightShade.setAlpha(0f);
        nightShade.setClickable(false);
        root.addView(nightShade, new FrameLayout.LayoutParams(-1, -1));

        HorizontalScrollView actionScroll = new HorizontalScrollView(this);
        actionScroll.setHorizontalScrollBarEnabled(false);
        actionScroll.setFillViewport(false);
        actionScroll.setBackgroundColor(Color.argb(205, 12, 16, 24));
        actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(dp(6), dp(5), dp(6), dp(5));
        actionScroll.addView(actionRow, new HorizontalScrollView.LayoutParams(-2, -2));
        addAction("← Back", v -> finish());
        addAction("Speed", v -> selectSpeed());
        addAction("Loop", v -> toggleLoop());
        addAction("Mute", v -> toggleMute());
        addAction("Rotate", v -> rotate());
        addAction("Night", v -> toggleNight());
        addAction("Sleep", v -> selectSleepTimer());
        addAction("Screenshot", v -> saveScreenshot());
        addAction("Equalizer", v -> openEqualizer());
        addAction("Lock", v -> setLocked(true));
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                -1, -2, Gravity.TOP);
        root.addView(actionScroll, barParams);

        lockButton = new Button(this);
        lockButton.setText("Unlock");
        lockButton.setAllCaps(false);
        lockButton.setVisibility(View.GONE);
        lockButton.setOnClickListener(v -> setLocked(false));
        FrameLayout.LayoutParams lockParams = new FrameLayout.LayoutParams(
                dp(110), dp(55), Gravity.CENTER);
        root.addView(lockButton, lockParams);
        actionRow.setTag(actionScroll);
        setContentView(root);
        enterFullscreen();
    }

    private void addAction(String name, View.OnClickListener click) {
        Button button = new Button(this);
        button.setText(name);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(30, 42, 61));
        bg.setCornerRadius(dp(16));
        button.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(46));
        lp.setMargins(dp(3), 0, dp(3), 0);
        actionRow.addView(button, lp);
        button.setOnClickListener(click);
    }

    @Override protected void onStart() {
        super.onStart();
        if ((videoPath == null && videoUri == null) || player != null) return;
        try {
            player = new ExoPlayer.Builder(this).build();
            player.addListener(new Player.Listener() {
                @Override public void onPlayerError(PlaybackException error) {
                    toast("Cannot play this video: " + error.getErrorCodeName());
                }
            });
            playerView.setPlayer(player);
            player.setMediaItem(MediaItem.fromUri(videoUri != null ? videoUri
                    : Uri.fromFile(new File(videoPath))));
            player.setRepeatMode(repeatMode);
            player.setPlaybackSpeed(speed);
            player.setVolume(muted ? 0f : 1f);
            player.prepare();
            if (resumePosition > 0) player.seekTo(resumePosition);
            player.setPlayWhenReady(resumePlay);
        } catch (RuntimeException error) {
            toast("Player could not start: " + error.getMessage());
            releasePlayer();
        }
    }

    private void selectSpeed() {
        final float[] values = {.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        String[] labels = {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×"};
        new AlertDialog.Builder(this).setTitle("Playback speed")
                .setSingleChoiceItems(labels, -1, (dialog, which) -> {
                    speed = values[which];
                    if (player != null) player.setPlaybackSpeed(speed);
                    dialog.dismiss();
                }).setNegativeButton("Cancel", null).show();
    }

    private void toggleLoop() {
        repeatMode = repeatMode == Player.REPEAT_MODE_ONE
                ? Player.REPEAT_MODE_OFF : Player.REPEAT_MODE_ONE;
        if (player != null) player.setRepeatMode(repeatMode);
        toast(repeatMode == Player.REPEAT_MODE_ONE ? "Loop enabled" : "Loop disabled");
    }

    private void toggleMute() {
        muted = !muted;
        if (player != null) player.setVolume(muted ? 0f : 1f);
        toast(muted ? "Muted" : "Sound on");
    }

    private void rotate() {
        int orientation = getResources().getConfiguration().orientation;
        try {
            setRequestedOrientation(orientation == Configuration.ORIENTATION_LANDSCAPE
                    ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } catch (RuntimeException error) {
            toast("Screen rotation is not available on this device");
        }
    }

    private void toggleNight() {
        night = !night;
        nightShade.setAlpha(night ? .33f : 0f);
        toast(night ? "Night mode on" : "Night mode off");
    }

    private void selectSleepTimer() {
        String[] choices = {"Off", "5 minutes", "15 minutes", "30 minutes", "60 minutes"};
        int[] minutes = {0, 5, 15, 30, 60};
        new AlertDialog.Builder(this).setTitle("Sleep timer")
                .setItems(choices, (dialog, which) -> {
                    handler.removeCallbacks(sleepAction);
                    int delay = minutes[which];
                    if (delay > 0) handler.postDelayed(sleepAction, delay * 60L * 1000L);
                    toast(delay == 0 ? "Sleep timer off" : "Sleep timer: " + delay + " min");
                }).show();
    }

    private void openEqualizer() {
        if (player == null) return;
        Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.getAudioSessionId());
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
        if (intent.resolveActivity(getPackageManager()) != null) {
            try { startActivity(intent); } catch (RuntimeException error) {
                toast("Equalizer unavailable");
            }
        } else toast("No system equalizer is installed");
    }

    private void saveScreenshot() {
        if (player == null || (videoPath == null && videoUri == null)) return;
        final long positionUs = player.getCurrentPosition() * 1000L;
        toast("Saving video frame…");
        work.execute(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            Bitmap bitmap = null;
            try {
                if (videoUri != null) retriever.setDataSource(this, videoUri);
                else retriever.setDataSource(videoPath);
                bitmap = retriever.getFrameAtTime(positionUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (bitmap == null) throw new Exception("Frame unavailable for this file");
                File directory = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                        "PWStudy");
                if (!directory.isDirectory() && !directory.mkdirs())
                    throw new Exception("Cannot create screenshot folder");
                File destination = new File(directory,
                        String.format(Locale.US, "frame_%d.png", System.currentTimeMillis()));
                try (FileOutputStream out = new FileOutputStream(destination)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                        throw new Exception("Could not encode screenshot");
                }
                runOnUiThread(() -> toast("Saved in PWStudy app pictures"));
            } catch (Exception error) {
                runOnUiThread(() -> toast("Screenshot failed: " + error.getMessage()));
            } finally {
                if (bitmap != null) bitmap.recycle();
                try { retriever.release(); } catch (Exception ignored) {}
            }
        });
    }

    private void setLocked(boolean value) {
        locked = value;
        View scroll = (View) actionRow.getTag();
        scroll.setVisibility(locked ? View.GONE : View.VISIBLE);
        lockButton.setVisibility(locked ? View.VISIBLE : View.GONE);
        playerView.setUseController(!locked);
    }

    private void enterFullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        enterFullscreen();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterFullscreen();
    }

    @Override protected void onResume() {
        super.onResume();
        enterFullscreen();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        savePlaybackPosition();
        out.putLong(KEY_POSITION, resumePosition);
        out.putBoolean(KEY_PLAY, resumePlay);
        out.putFloat(KEY_SPEED, speed);
        out.putBoolean(KEY_MUTED, muted);
        out.putInt(KEY_REPEAT, repeatMode);
        super.onSaveInstanceState(out);
    }

    @Override protected void onStop() {
        savePlaybackPosition();
        releasePlayer();
        super.onStop();
    }

    private void savePlaybackPosition() {
        if (player != null) {
            resumePosition = player.getCurrentPosition();
            resumePlay = player.getPlayWhenReady();
        }
    }

    private void releasePlayer() {
        if (player == null) return;
        if (playerView != null) playerView.setPlayer(null);
        player.release();
        player = null;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        work.shutdown();
        super.onDestroy();
    }
}
