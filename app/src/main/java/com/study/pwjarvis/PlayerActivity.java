package com.study.pwjarvis;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
