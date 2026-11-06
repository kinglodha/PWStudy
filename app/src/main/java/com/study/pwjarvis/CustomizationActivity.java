package com.study.pwjarvis;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class CustomizationActivity extends Activity {
    private static final int PICK_BACKGROUND = 7101;
    private LinearLayout root;
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(18),dp(20),dp(18)); root.setBackgroundColor(Color.rgb(5,7,12));
        TextView title=new TextView(this); title.setText("PWSTUDY CUSTOMIZATION");
        title.setTextColor(Color.WHITE); title.setTextSize(21); title.setTypeface(null,Typeface.BOLD);
        title.setGravity(Gravity.CENTER); root.addView(title,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView note=new TextView(this); note.setText("AMOLED dark base remains enabled. Choose text and accent colours, or set a background image.");
        note.setTextColor(Color.LTGRAY); note.setTextSize(13); note.setPadding(0,dp(8),0,dp(18)); root.addView(note);
        addSection("TEXT COLOUR");
        addPalette(new String[]{"White","Soft grey","Mint","Cyan","Amber"},new int[]{Color.WHITE,0xFFCFD4DC,0xFF80CBC4,0xFF80DEEA,0xFFFFD180},true);
        addSection("ACCENT / THEME COLOUR");
        addPalette(new String[]{"Blue","Purple","Green","Orange","Pink"},new int[]{0xFF2370D2,0xFF8E5BCE,0xFF269B70,0xFFDA8732,0xFFCA4F91},false);
        addSection("BACKGROUND IMAGE");
        addButton("Choose image from device",v->{
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT); pick.addCategory(Intent.CATEGORY_OPENABLE); pick.setType("image/*");
            pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(pick,PICK_BACKGROUND);
        });
        addButton("Remove background image",v->{AppAppearance.clearBackground(this);Toast.makeText(this,"Background reset to AMOLED black",Toast.LENGTH_SHORT).show();});
        addButton("Reset all customization",v->{
            getSharedPreferences("pwstudy_appearance",0).edit().clear().apply();
            Toast.makeText(this,"Customization reset",Toast.LENGTH_SHORT).show();
        });
        addButton("Done",v->finish());
        setContentView(root);
    }
    private void addSection(String text){TextView t=new TextView(this);t.setText(text);t.setTextColor(0xFF80B8FF);t.setTextSize(13);t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(18),0,dp(7));root.addView(t);}
    private void addPalette(String[] names,int[] colors,boolean text){
        for(int i=0;i<names.length;i++){final int color=colors[i];final String name=names[i];addButton(name,v->{if(text)AppAppearance.setTextColor(this,color);else AppAppearance.setAccentColor(this,color);Toast.makeText(this,name+" applied when screens reopen",Toast.LENGTH_SHORT).show();});}
    }
    private void addButton(String label,android.view.View.OnClickListener listener){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(AppAppearance.accentColor(this)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.bottomMargin=dp(7);root.addView(b,p);b.setOnClickListener(listener);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_BACKGROUND&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            try{
                int flags=data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri,flags);
                AppAppearance.setBackgroundUri(this,uri.toString());
                Toast.makeText(this,"Background image saved",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,"Image permission could not be saved: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }
    }
}
