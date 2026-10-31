package com.study.pwjarvis;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(22), dp(24), dp(22), dp(24));
        root.setBackgroundColor(Color.rgb(12,16,25));

        TextView title = new TextView(this);
        title.setText("PW JARVIS STUDY");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1,-2);
        tp.bottomMargin=dp(10);
        root.addView(title,tp);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your study space");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2);
        sp.bottomMargin=dp(32);
        root.addView(subtitle,sp);

        Button website=makeButton("1   PW WEBSITE","Open batches, classes and study content",true);
        root.addView(website,buttonParams());
        website.setOnClickListener(v->startActivity(new Intent(this,WebActivity.class)));

        Button library=makeButton("2   OFFLINE LIBRARY","Files downloaded and saved inside this app",false);
        LinearLayout.LayoutParams lp2=buttonParams(); lp2.topMargin=dp(16);
        root.addView(library,lp2);
        library.setOnClickListener(v->startActivity(new Intent(this,LibraryActivity.class)));

        Button device=makeButton("3   DEVICE DOCUMENTS","Browse documents and folders on this phone",false);
        LinearLayout.LayoutParams lp3=buttonParams(); lp3.topMargin=dp(16);
        root.addView(device,lp3);
        device.setOnClickListener(v->startActivity(new Intent(this,DeviceDocumentsActivity.class)));
        setContentView(root);
    }

    private LinearLayout.LayoutParams buttonParams(){ return new LinearLayout.LayoutParams(-1,dp(82)); }

    private Button makeButton(String title,String detail,boolean primary){
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(title+"\\n"+detail);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            primary?new int[]{Color.rgb(28,93,170),Color.rgb(22,58,112)}:
                    new int[]{Color.rgb(29,39,55),Color.rgb(20,28,42)});
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1),Color.rgb(75,115,160));
        b.setBackground(bg);
        b.setPadding(dp(10),dp(6),dp(10),dp(6));
        return b;
    }
}