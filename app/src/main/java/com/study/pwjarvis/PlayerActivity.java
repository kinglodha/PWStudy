package com.study.pwjarvis;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.File;

public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView playerView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        // Open videos in landscape immersive fullscreen.
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        enterImmersiveFullscreen();

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        setContentView(playerView, new ViewGroup.LayoutParams(-1, -1));

        String path = getIntent().getStringExtra("file_path");
        if (path == null) {
            Toast.makeText(this, "Video path missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        File file = new File(path);
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)));
        player.prepare();
        player.play();
    }

    private void enterImmersiveFullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveFullscreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveFullscreen();
    }

    @Override protected void onStop() {
        super.onStop();
        releasePlayer();
    }

    @Override protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    private void releasePlayer() {
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }
}
