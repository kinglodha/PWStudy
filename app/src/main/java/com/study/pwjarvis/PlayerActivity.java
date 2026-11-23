package com.study.pwjarvis;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/**
 * Local and authorized video playback. The player belongs to the Activity and
 * is released on stop so rotation never leaves a stale surface/player behind.
 */
public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView playerView;
    private TextView errorText;
    private FrameLayout root;
    private LinearLayout controls;
    private boolean fullscreen;
    private boolean userPaused;
    private long resumePosition;
    private String source;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        source = getIntent().getStringExtra("video_uri");
        if (state != null) {
            resumePosition = state.getLong("position", 0L);
            userPaused = state.getBoolean("paused", false);
            fullscreen = state.getBoolean("fullscreen", false);
        }
        buildInterface();
        if (source == null || source.trim().isEmpty()) {
            showError("Choose a downloaded video from Offline Library.");
        }
    }

    private void buildInterface() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(8, 8, 8, 8);
        controls.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams bar = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(controls, bar);

        Button back = new Button(this);
        back.setText("Back");
        back.setOnClickListener(v -> finish());
        controls.addView(back, new LinearLayout.LayoutParams(0, -2, 1));

        Button rotate = new Button(this);
        rotate.setText("Rotate");
        rotate.setOnClickListener(v -> {
            int orientation = getResources().getConfiguration().orientation;
            setRequestedOrientation(orientation == Configuration.ORIENTATION_LANDSCAPE
                    ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        });
        controls.addView(rotate, new LinearLayout.LayoutParams(0, -2, 1));

        Button full = new Button(this);
        full.setText("Fullscreen");
        full.setOnClickListener(v -> {
            fullscreen = !fullscreen;
            applyFullscreen();
        });
        controls.addView(full, new LinearLayout.LayoutParams(0, -2, 1));

        errorText = new TextView(this);
        errorText.setTextColor(0xFFFFFFFF);
        errorText.setBackgroundColor(0xDD222222);
        errorText.setGravity(Gravity.CENTER);
        errorText.setPadding(24, 24, 24, 24);
        errorText.setVisibility(View.GONE);
        root.addView(errorText, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        setContentView(root);
        applyFullscreen();
    }

    private void applyFullscreen() {
        if (fullscreen) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            controls.setVisibility(View.GONE);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(0);
            controls.setVisibility(View.VISIBLE);
        }
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override protected void onStart() {
        super.onStart();
        if (source == null || source.trim().isEmpty() || player != null) return;
        try {
            ExoPlayer created = new ExoPlayer.Builder(this).build();
            player = created;
            playerView.setPlayer(created);
            created.addListener(new Player.Listener() {
                @Override public void onPlayerError(PlaybackException error) {
                    showError("Video playback failed: " + error.getErrorCodeName());
                }
                @Override public void onIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying) errorText.setVisibility(View.GONE);
                    updateKeepScreenOn(isPlaying);
                }
            });
            created.setMediaItem(MediaItem.fromUri(Uri.parse(source)));
            created.seekTo(Math.max(0L, resumePosition));
            created.prepare();
            created.setPlayWhenReady(!userPaused);
        } catch (RuntimeException exception) {
            showError("Unable to start video player");
            releasePlayer();
        }
    }

    private void updateKeepScreenOn(boolean playing) {
        if (playing) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void releasePlayer() {
        if (player == null) return;
        resumePosition = Math.max(0L, player.getCurrentPosition());
        userPaused = !player.getPlayWhenReady();
        playerView.setPlayer(null);
        player.release();
        player = null;
        updateKeepScreenOn(false);
    }

    @Override protected void onStop() {
        releasePlayer();
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        if (player != null) {
            resumePosition = Math.max(0L, player.getCurrentPosition());
            userPaused = !player.getPlayWhenReady();
        }
        out.putLong("position", resumePosition);
        out.putBoolean("paused", userPaused);
        out.putBoolean("fullscreen", fullscreen);
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
