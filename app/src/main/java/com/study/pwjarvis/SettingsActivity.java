package com.study.pwjarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import android.text.Editable;
import android.text.TextWatcher;

public class SettingsActivity extends Activity {
    static final String PREFS = "tweak_player_settings";
    static final String GESTURES = "gestures", SAVE = "save_progress",
            RESUME = "resume_progress", AWAKE = "keep_awake", ROTATE = "orientation",
            SPEED = "speed", FIT = "fit", SEEK = "seek_seconds",
            CAPTURE = "allow_capture", ACCENT = "accent";
    private SharedPreferences prefs;
    private LinearLayout rows;
    private String page = "Settings";
    private int accent;
    private final int bg = 0xff000000, card = 0xff191b20, sub = 0xffaab0ba;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        page = state != null ? state.getString("page", "Settings")
                : getIntent().getStringExtra("page");
        if (page == null) page = "Settings";
        render();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("page", page);
        super.onSaveInstanceState(state);
    }
    int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    TextView text(String title, int size, int color) {
        TextView t = new TextView(this); t.setText(title);
        t.setTextSize(size); t.setTextColor(color); return t;
    }
    void render() {
        accent = prefs.getInt(ACCENT, 0xff59bac8);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1);
        root.setBackgroundColor(bg);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10),dp(8),dp(12),dp(8));
        Button back = new Button(this); back.setText("‹"); back.setTextColor(Color.WHITE);
        back.setContentDescription("Back"); back.setBackgroundColor(Color.TRANSPARENT);
        header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        back.setOnClickListener(v -> onBackPressed());
        TextView title = text(page,24,Color.WHITE);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(header);
        EditText search = new EditText(this); search.setSingleLine(true);
        search.setHint("Search settings"); search.setTextColor(Color.WHITE);
        search.setHintTextColor(sub); search.setPadding(dp(16),dp(10),dp(16),dp(10));
        search.setBackground(shape(card,14));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1,-2);
        searchParams.setMargins(dp(16),0,dp(16),dp(12)); root.addView(search,searchParams);
        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this); rows.setOrientation(1);
        rows.setPadding(dp(12),0,dp(12),dp(24)); scroll.addView(rows);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        fill();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                String q=s.toString().toLowerCase(java.util.Locale.ROOT);
                for(int i=0;i<rows.getChildCount();i++) {
                    android.view.View child=rows.getChildAt(i);
                    Object tag=child.getTag();
                    child.setVisibility(tag==null || tag.toString().toLowerCase(java.util.Locale.ROOT).contains(q)
                            ? android.view.View.VISIBLE : android.view.View.GONE);
                }
            }
            public void afterTextChanged(Editable e) {}
        });
    }
    void fill() {
        if ("Player".equals(page)) {
            section("Playback");
            toggle("Gesture controls","Swipe left side for brightness, right side for volume; horizontal to seek",GESTURES,true);
            toggle("Keep screen on","Prevent screen timeout during video playback",AWAKE,true);
            choice("Orientation",ROTATE,new String[]{"Follow device","Landscape","Portrait"},0);
            choice("Playback speed",SPEED,new String[]{"0.5×","0.75×","1×","1.25×","1.5×","1.75×","2×"},2);
            choice("Video scaling",FIT,new String[]{"Fit","Fill","Zoom"},0);
            choice("Skip interval",SEEK,new String[]{"5 seconds","10 seconds","15 seconds","30 seconds"},1);
            section("Downloaded videos");
            toggle("Save progress","Store playback position on this device",SAVE,true);
            toggle("Resume saved progress","Continue from the last saved position",RESUME,true);
            link("Clear saved video progress","Removes playback positions only",()->new AlertDialog.Builder(this)
                    .setTitle("Clear saved progress?").setNegativeButton("Cancel",null)
                    .setPositiveButton("Clear",(d,w)->getSharedPreferences("tweak_video_positions",MODE_PRIVATE).edit().clear().apply()).show());
        } else if ("Appearance".equals(page)) {
            section("AMOLED black");
            TextView note=text("Black background · Accent applies to Settings and player controls",14,sub);
            note.setPadding(dp(16),dp(10),dp(16),dp(12)); rows.addView(note);
            int[] colors={0xff59bac8,0xff2196f3,0xffb78be5,0xff63c998,0xffffa75b};
            String[] names={"Aqua","Sky","Twilight","Mint","Amber"};
            for(int i=0;i<colors.length;i++){
                final int color=colors[i];
                link(names[i], color==accent ? "Selected" : "Choose accent",()->{prefs.edit().putInt(ACCENT,color).apply(); render();});
            }
            section("App icon");
            link("App icon","Choose your Android launcher icon",()->open("App icon"));
        } else if ("App icon".equals(page)) {
            section("Choose your icon");
            int current=LauncherIcons.current(this);
            for(int i=0;i<LauncherIcons.NAMES.length;i++){
                final int selected=i;
                LinearLayout item=row(LauncherIcons.NAMES[i],i==current?"Selected":"Tap to apply");
                ImageView image=new ImageView(this);
                image.setImageDrawable(LauncherIcons.preview(this,i));
                image.setContentDescription(LauncherIcons.NAMES[i]+" icon");
                LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(56),dp(56));
                iconParams.setMarginEnd(dp(14));
                item.addView(image,0,iconParams);
                if(i==current)item.addView(text("✓",24,accent));
                item.setOnClickListener(v->{
                    if(selected==LauncherIcons.current(this))return;
                    new AlertDialog.Builder(this).setTitle("Change app icon?")
                        .setMessage("Your launcher may refresh or require you to re-pin a shortcut. Your study files will not change.")
                        .setNegativeButton("Cancel",null)
                        .setPositiveButton("Apply",(d,w)->{
                            try{
                                LauncherIcons.apply(this,selected);
                                Toast.makeText(this,"Icon changed; launcher may take a moment to refresh",Toast.LENGTH_LONG).show();
                                render();
                            }catch(RuntimeException e){
                                Toast.makeText(this,"Unable to change icon",Toast.LENGTH_LONG).show();
                                render();
                            }
                        }).show();
                });
            }
        } else if ("Privacy".equals(page)) {
            section("Video privacy");
            toggle("Allow screenshots","When off, the video player requests secure window protection",CAPTURE,true);
        } else {
            link("Player","Gesture controls, orientation, speed and saved progress",()->open("Player"));
            link("Appearance","AMOLED black, accent colors and app icon",()->open("Appearance"));
            link("Privacy","Video screenshot protection",()->open("Privacy"));
        }
    }
    void open(String name){page=name;render();}
    void section(String name){
        TextView t=text(name,14,accent); t.setPadding(dp(8),dp(16),0,dp(8)); rows.addView(t);
    }
    LinearLayout row(String title,String detail) {
        LinearLayout item=new LinearLayout(this); item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(16),dp(13),dp(12),dp(13)); item.setBackground(shape(card,12));
        item.setTag(title+" "+detail);
        LinearLayout labels=new LinearLayout(this); labels.setOrientation(1);
        labels.addView(text(title,17,Color.WHITE));
        TextView d=text(detail,13,sub); d.setPadding(0,dp(4),0,0); labels.addView(d);
        item.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,0,0,dp(5)); rows.addView(item,p); return item;
    }
    void link(String title,String detail,Runnable action){
        LinearLayout r=row(title,detail); r.addView(text("›",26,accent)); r.setOnClickListener(v->action.run());
    }
    void toggle(String title,String detail,String key,boolean fallback){
        LinearLayout r=row(title,detail); Switch s=new Switch(this);
        s.setContentDescription(title); s.setChecked(prefs.getBoolean(key,fallback));
        s.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
                new int[]{accent,0xff8b9099}));
        s.setOnCheckedChangeListener((button,on)->prefs.edit().putBoolean(key,on).apply());
        r.addView(s); r.setOnClickListener(v->s.setChecked(!s.isChecked()));
    }
    void choice(String title,String key,String[] choices,int fallback){
        int selected=Math.max(0,Math.min(choices.length-1,prefs.getInt(key,fallback)));
        link(title,choices[selected],()->new AlertDialog.Builder(this).setTitle(title)
            .setSingleChoiceItems(choices,selected,(d,n)->{prefs.edit().putInt(key,n).apply();d.dismiss();render();})
            .setNegativeButton("Cancel",null).show());
    }
    @Override public void onBackPressed(){if("App icon".equals(page)){page="Appearance";render();}else if(!"Settings".equals(page)){page="Settings";render();}else super.onBackPressed();}
}
