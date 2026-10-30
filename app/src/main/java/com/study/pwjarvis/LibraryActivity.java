package com.study.pwjarvis;

import android.app.*;
import android.os.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.io.*;

public class LibraryActivity extends Activity {
    LinearLayout list;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView sc=new ScrollView(this); list=new LinearLayout(this); list.setPadding(24,24,24,24); list.setOrientation(1); sc.addView(list);
        TextView title=new TextView(this); title.setText("OFFLINE STUDY LIBRARY"); title.setTextSize(22); list.addView(title);
        addSection("PHYSICS"); addSection("CHEMISTRY"); addSection("BIOLOGY");
        TextView note=new TextView(this); note.setText("\nDownloaded files will appear here. Organize them by Subject → Chapter → Lecture/PDF."); list.addView(note);
        Button browse=new Button(this); browse.setText("Open phone Downloads"); list.addView(browse);
        browse.setOnClickListener(v->{ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,2);});
        setContentView(sc);
    }
    void addSection(String name){ TextView t=new TextView(this); t.setText("\n"+name+"  ›"); t.setTextSize(19); t.setPadding(0,24,0,8); list.addView(t); }
}
