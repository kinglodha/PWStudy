package com.study.pwjarvis;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.AspectRatioFrameLayout;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class PlayerActivity extends Activity {
    private static final int PICK_VIDEO=401;
    private ExoPlayer player;
    private PlayerView view;
    private LinearLayout root, toolbar;
    private TextView feedback;
    private SharedPreferences prefs, positions;
    private String source;
    private long pendingPosition=-1;
    private boolean resumePlaying=true, dragging;
    private float downX,downY, initialBrightness;
    private int initialVolume, gestureMode;
    private long initialPosition;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable checkpoint=new Runnable(){
        public void run(){save();if(player!=null)handler.postDelayed(this,5000);}
    };
    private final Runnable hideFeedback=()->{if(feedback!=null)feedback.setVisibility(View.GONE);};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences(SettingsActivity.PREFS,MODE_PRIVATE);
        positions=getSharedPreferences("tweak_video_positions",MODE_PRIVATE);
        source=state==null?getIntent().getStringExtra("video_uri"):state.getString("source");
        if(source==null&&getIntent().getData()!=null)source=getIntent().getData().toString();
        if(state!=null){pendingPosition=state.getLong("position",-1);resumePlaying=state.getBoolean("playing",true);}
        getWindow().setStatusBarColor(0xff000000);getWindow().setNavigationBarColor(0xff000000);
        layout();
    }
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    void layout(){
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xff000000);
        toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        addButton("‹ Back",()->finish());
        addButton("Open",this::pick);
        addButton("↻ Rotate",this::rotate);
        addButton("⚙ Settings",()->{
            Intent i=new Intent(this,SettingsActivity.class);i.putExtra("page","Player");startActivity(i);
        });
        root.addView(toolbar);
        FrameLayout frame=new FrameLayout(this);
        view=new PlayerView(this); view.setKeepScreenOn(false);
        frame.addView(view,new FrameLayout.LayoutParams(-1,-1));
        feedback=new TextView(this);feedback.setTextColor(0xffffffff);feedback.setTextSize(18);
        feedback.setPadding(dp(16),dp(10),dp(16),dp(10));feedback.setBackgroundColor(0xbb181b20);
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);
        frame.addView(feedback,fp);feedback.setVisibility(View.GONE);
        root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        if(source==null){view.setVisibility(View.GONE);TextView hint=new TextView(this);
            hint.setText("Open a video from device storage or the Offline Library.");hint.setTextColor(0xffffffff);
            hint.setGravity(Gravity.CENTER);frame.addView(hint,new FrameLayout.LayoutParams(-1,-1));}
        setContentView(root);
    }
    void addButton(String title,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextSize(12);
        b.setTextColor(prefs.getInt(SettingsActivity.ACCENT,0xff59bac8));
        b.setOnClickListener(v->action.run());toolbar.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));
    }
    void pick(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("video/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(i,PICK_VIDEO);}catch(Exception e){message("Video picker unavailable");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_VIDEO||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
        release();source=uri.toString();pendingPosition=-1;resumePlaying=true;layout();
    }
    @Override protected void onStart(){super.onStart();applySettings();startPlayer();}
    @Override protected void onResume(){super.onResume();applySettings();if(player==null)startPlayer();}
    @Override protected void onStop(){release();super.onStop();}
    @Override protected void onDestroy(){release();handler.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle b){
        super.onSaveInstanceState(b);b.putString("source",source);
        b.putLong("position",player==null?pendingPosition:player.getCurrentPosition());
        b.putBoolean("playing",player==null?resumePlaying:player.getPlayWhenReady());
    }
    void applySettings(){
        if(prefs.getBoolean(SettingsActivity.CAPTURE,true))getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        int o=prefs.getInt(SettingsActivity.ROTATE,0);
        int requested=o==1?ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:o==2?ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        if(getRequestedOrientation()!=requested)setRequestedOrientation(requested);
        if(view!=null){int fit=prefs.getInt(SettingsActivity.FIT,0);
            view.setResizeMode(fit==1?AspectRatioFrameLayout.RESIZE_MODE_FILL:fit==2?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:AspectRatioFrameLayout.RESIZE_MODE_FIT);}
        if(player!=null)player.setPlaybackSpeed(speed());
    }
    float speed(){float[] speeds={.5f,.75f,1f,1.25f,1.5f,1.75f,2f};return speeds[Math.max(0,Math.min(6,prefs.getInt(SettingsActivity.SPEED,2)))];}
    long interval(){int[] seconds={5,10,15,30};return 1000L*seconds[Math.max(0,Math.min(3,prefs.getInt(SettingsActivity.SEEK,1)))];}
    void rotate(){
        int next=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?2:1;
        prefs.edit().putInt(SettingsActivity.ROTATE,next).apply();applySettings();
    }
    void startPlayer(){
        if(player!=null||source==null||view==null)return;
        try{
            player=new ExoPlayer.Builder(this).setSeekBackIncrementMs(interval()).setSeekForwardIncrementMs(interval()).build();
            view.setPlayer(player);view.setVisibility(View.VISIBLE);
            player.addListener(new Player.Listener(){
                @Override public void onIsPlayingChanged(boolean playing){
                    if(playing&&prefs.getBoolean(SettingsActivity.AWAKE,true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
                @Override public void onPlayerError(PlaybackException error){message("Cannot play this video: "+error.getErrorCodeName());}
                @Override public void onPlaybackStateChanged(int state){
                    if(state==Player.STATE_ENDED){positions.edit().remove(key()).apply();}
                }
            });
            player.setMediaItem(MediaItem.fromUri(Uri.parse(source)));
            long pos=pendingPosition>=0?pendingPosition:prefs.getBoolean(SettingsActivity.RESUME,true)?positions.getLong(key(),0):0;
            if(pos>0)player.seekTo(pos);
            player.setPlaybackSpeed(speed());player.prepare();player.setPlayWhenReady(resumePlaying);
            handler.removeCallbacks(checkpoint);handler.postDelayed(checkpoint,5000);
        }catch(Exception e){release();message("Unable to open video");}
    }
    String key(){
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder b=new StringBuilder();for(byte x:digest)b.append(String.format(java.util.Locale.ROOT,"%02x",x&255));return b.toString();}
        catch(Exception e){return Integer.toHexString(source.hashCode());}
    }
    void save(){
        if(player==null||source==null||!prefs.getBoolean(SettingsActivity.SAVE,true))return;
        if(player.getPlaybackState()==Player.STATE_ENDED){positions.edit().remove(key()).apply();return;}
        if(player.getPlaybackState()==Player.STATE_READY||player.getCurrentPosition()>0)
            positions.edit().putLong(key(),Math.max(0,player.getCurrentPosition())).apply();
    }
    void release(){
        handler.removeCallbacks(checkpoint);
        if(player==null)return;
        save();pendingPosition=player.getCurrentPosition();resumePlaying=player.getPlayWhenReady();
        if(view!=null)view.setPlayer(null);player.release();player=null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    void message(String s){
        if(feedback==null)return;feedback.setText(s);feedback.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideFeedback);handler.postDelayed(hideFeedback,1600);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(player==null||view==null||!prefs.getBoolean(SettingsActivity.GESTURES,true))return super.dispatchTouchEvent(e);
        int[] xy=new int[2];view.getLocationOnScreen(xy);
        float x=e.getRawX()-xy[0],y=e.getRawY()-xy[1];
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            dragging=false;gestureMode=0;downX=x;downY=y;
            if(y>dp(35)&&y<view.getHeight()-dp(90)&&x>=0&&x<=view.getWidth()&&!view.isControllerFullyVisible()){
                gestureMode=-1;initialPosition=player.getCurrentPosition();
                initialBrightness=getWindow().getAttributes().screenBrightness;
                if(initialBrightness<0)initialBrightness=.5f;
                AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE);initialVolume=a.getStreamVolume(AudioManager.STREAM_MUSIC);
            }
        }else if(action==MotionEvent.ACTION_MOVE&&gestureMode!=0&&e.getPointerCount()==1){
            float dx=x-downX,dy=y-downY;
            if(!dragging&&Math.max(Math.abs(dx),Math.abs(dy))>dp(22)){
                dragging=true;gestureMode=Math.abs(dx)>Math.abs(dy)?1:downX<view.getWidth()/2f?2:3;
                MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();
            }
            if(dragging){
                if(gestureMode==1&&player.isCurrentMediaItemSeekable()){
                    long duration=player.getDuration();long target=Math.max(0,initialPosition+(long)(dx/Math.max(1,view.getWidth())*120000));
                    if(duration!=C.TIME_UNSET)target=Math.min(duration,target);
                    player.seekTo(target);message(String.format(java.util.Locale.ROOT,"%d:%02d",target/60000,target/1000%60));
                }else if(gestureMode==2){
                    float brightness=Math.max(.03f,Math.min(1f,initialBrightness-dy/Math.max(1,view.getHeight())));
                    WindowManager.LayoutParams a=getWindow().getAttributes();a.screenBrightness=brightness;getWindow().setAttributes(a);
                    message("Brightness "+Math.round(brightness*100)+"%");
                }else if(gestureMode==3){
                    AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE);int max=a.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int vol=Math.max(0,Math.min(max,initialVolume-Math.round(dy/Math.max(1,view.getHeight())*max)));
                    try{a.setStreamVolume(AudioManager.STREAM_MUSIC,vol,0);message("Volume "+vol+"/"+max);}catch(SecurityException ignored){}
                }
                return true;
            }
        }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            boolean consumed=dragging;dragging=false;gestureMode=0;if(consumed)return true;
        }
        return super.dispatchTouchEvent(e);
    }
}
