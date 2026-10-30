package com.study.pwjarvis;

import android.app.*;
import android.os.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class LibraryActivity extends Activity {

    LinearLayout list;
    String selectedSubject = "Physics";

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        showSubjects();
    }

    File getFolder(String subject) {
        File root = new File(getExternalFilesDir(null), "PWStudy");
        File folder = new File(root, subject);

        if (!folder.exists()) {
            folder.mkdirs();
        }

        return folder;
    }

    void setup(String title) {
        ScrollView scroll = new ScrollView(this);

        list = new LinearLayout(this);
        list.setOrientation(1);
        list.setPadding(20, 20, 20, 20);
        list.setBackgroundColor(0xFF000000);

        scroll.addView(list);
        setContentView(scroll);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(25);
        heading.setTextColor(0xFFFFFFFF);
        heading.setPadding(5, 20, 5, 25);

        list.addView(heading);
    }

    void showSubjects() {
        setup("PWStudy • Offline Library");

        list.addView(label("Your downloaded study materials"));

        addSubject("Physics");
        addSubject("Chemistry");
        addSubject("Biology");
    }

    void addSubject(String subject) {
        Button button = new Button(this);
        button.setText(subject + " →");
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(0xFF181818)
        );

        list.addView(button);

        button.setOnClickListener(v -> {
            selectedSubject = subject;
            showFiles();
        });
    }

    TextView label(String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(16);
        t.setTextColor(0xFFBBBBBB);
        t.setPadding(8, 15, 8, 15);
        return t;
    }

    void showFiles() {
        setup(selectedSubject);

        Button back = new Button(this);
        back.setText("← Back");
        list.addView(back);

        back