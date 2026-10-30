package com.study.pwjarvis;

import android.app.*;
import android.os.*;
import android.net.Uri;
import android.widget.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {
    ExoPlayer player;
    @Override public void onCreate(Bundle b){ super.onCreate(b);
        String source=getIntent().getStringExtra("video_uri");
        if(source==null){ TextView t=new TextView(this); t.setText("Choose a downloaded video from Offline Library."); setContentView(t); return; }
        PlayerView view=new PlayerView(this); setContentView(view);
        player=new ExoPlayer.Builder(this).build(); view.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(source))); player.prepare(); player.play();
    }
    @Override protected void onDestroy(){ if(player!=null) player.release(); super.onDestroy(); }
}
