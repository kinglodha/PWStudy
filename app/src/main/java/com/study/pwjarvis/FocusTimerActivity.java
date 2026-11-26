package com.study.pwjarvis;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** A local focus/break timer. Elapsed time survives rotation and backgrounding. */
public class FocusTimerActivity extends StudyToolActivity {
    private static final String PREFS = "tweak_focus_timer";
    private static final int IDLE = 0, RUNNING = 1, PAUSED = 2;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::updateClock;
    private int focusMinutes = 25;
    private int breakMinutes = 5;
    private int state = IDLE;
    private boolean isBreak;
    private long duration;
    private long remaining;
    private long deadline;
    private long startedWall;
    private String token = "";
    private String subject = "Physics";
    private TextView clock;
    private TextView modeLabel;
    private ProgressBar progress;
    private boolean resumed;
    private boolean keepAwake;

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        restore();
        render();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void restore() {
        SharedPreferences p = prefs();
        focusMinutes = Math.max(1, Math.min(180, p.getInt("focus_minutes", 25)));
        breakMinutes = Math.max(1, Math.min(60, p.getInt("break_minutes", 5)));
        subject = p.getString("subject", "Physics");
        keepAwake = p.getBoolean("awake", false);
        state = p.getInt("state", IDLE);
        isBreak = p.getBoolean("break", false);
        duration = p.getLong("duration", focusMinutes * 60000L);
        remaining = p.getLong("remaining", duration);
        deadline = p.getLong("deadline", 0);
        startedWall = p.getLong("started_wall", 0);
        token = p.getString("token", "");
        if (state != IDLE && state != PAUSED && state != RUNNING) state = IDLE;
        if (duration <= 0 || duration > 180 * 60000L || remaining < 0 || remaining > duration) {
            state = IDLE;
            duration = focusMinutes * 60000L;
            remaining = duration;
        }
        if (state != IDLE && token.isEmpty()) state = IDLE;
        if (state == RUNNING && SystemClock.elapsedRealtime() < p.getLong("saved_elapsed", 0)) {
            // A reboot invalidates monotonic deadlines; do not record fictitious study time.
            state = PAUSED;
            deadline = 0;
        }
        if (state == IDLE) {
            duration = (isBreak ? breakMinutes : focusMinutes) * 60000L;
            remaining = duration;
        }
    }

    private void save() {
        prefs().edit()
                .putInt("focus_minutes", focusMinutes)
                .putInt("break_minutes", breakMinutes)
                .putString("subject", subject)
                .putBoolean("awake", keepAwake)
                .putInt("state", state)
                .putBoolean("break", isBreak)
                .putLong("duration", duration)
                .putLong("remaining", currentRemaining())
                .putLong("deadline", deadline)
                .putLong("started_wall", startedWall)
                .putLong("saved_elapsed", SystemClock.elapsedRealtime())
                .putString("token", token).apply();
    }

    private long currentRemaining() {
        if (state == RUNNING) {
            return Math.max(0, Math.min(duration, deadline - SystemClock.elapsedRealtime()));
        }
        return Math.max(0, remaining);
    }

    private void render() {
        screen("Focus timer", "Pomodoro-style focus sessions and breaks. Your history stays on this device.");
        LinearLayout timer = card(content);
        modeLabel = text((isBreak ? "Break" : subject + " focus") +
                (state == PAUSED ? " · Paused" : ""), 18, true);
        modeLabel.setGravity(Gravity.CENTER);
        timer.addView(modeLabel);
        clock = text(format(currentRemaining()), 52, true);
        clock.setGravity(Gravity.CENTER);
        timer.addView(clock);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        timer.addView(progress);
        if (state == RUNNING) {
            addPair(timer, "Pause", this::pause, "Finish now", this::askFinish);
        } else if (state == PAUSED) {
            addPair(timer, "Resume", this::resumeTimer, "Finish now", this::askFinish);
        } else {
            addPair(timer, isBreak ? "Start break" : "Start focus", this::startTimer,
                    isBreak ? "Switch to focus" : "Switch to break", this::switchMode);
        }
        if (state != IDLE) {
            timer.addView(button("Reset without saving", () -> confirm(
                    "Discard this session?", "Elapsed time in this session will not be recorded.",
                    this::resetTimer)));
        }
        Button settings = button("Timer settings", this::settings);
        settings.setEnabled(state == IDLE);
        content.addView(settings);
        content.addView(text("The timer keeps its deadline while you use other apps. " +
                "This version does not promise a background alarm or notification.", 12, false));
        showSummary();
        updateAwake();
        if (resumed) updateClock();
    }

    private String format(long millis) {
        long seconds = (millis + 999) / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void updateClock() {
        handler.removeCallbacks(tick);
        if (!resumed || clock == null) return;
        long left = currentRemaining();
        if (state == RUNNING && left == 0) {
            finishTimer(true);
            return;
        }
        clock.setText(format(left));
        progress.setProgress((int) (1000.0 * (duration - left) / Math.max(1, duration)));
        if (state == RUNNING) handler.postDelayed(tick, 250);
    }

    private void startTimer() {
        if (state != IDLE) return;
        duration = (isBreak ? breakMinutes : focusMinutes) * 60000L;
        remaining = duration;
        startedWall = System.currentTimeMillis();
        token = UUID.randomUUID().toString();
        resumeTimer();
    }

    private void resumeTimer() {
        if (state == RUNNING || remaining <= 0) return;
        deadline = SystemClock.elapsedRealtime() + remaining;
        state = RUNNING;
        save();
        render();
    }

    private void pause() {
        if (state != RUNNING) return;
        remaining = currentRemaining();
        state = PAUSED;
        deadline = 0;
        save();
        render();
    }

    private void askFinish() {
        if (state == IDLE) return;
        confirm("Finish session?", isBreak ? "End this break early?" :
                "Save the elapsed focus time as an incomplete session?", () -> finishTimer(false));
    }

    private void finishTimer(boolean completed) {
        if (state == IDLE) return;
        long left = currentRemaining();
        int seconds = (int) Math.min(Integer.MAX_VALUE, (duration - left) / 1000);
        if (!isBreak && seconds > 0) {
            try {
                long end = System.currentTimeMillis();
                long start = Math.min(startedWall, end);
                store.recordFocus(token, subject, start, end, seconds, completed || left == 0);
            } catch (RuntimeException e) {
                remaining = left;
                state = PAUSED;
                save();
                toast("Could not save focus history. Try Finish again.");
                render();
                return;
            }
        }
        boolean nextBreak = !isBreak;
        state = IDLE;
        isBreak = nextBreak;
        duration = (isBreak ? breakMinutes : focusMinutes) * 60000L;
        remaining = duration;
        token = "";
        deadline = 0;
        save();
        toast(completed ? (isBreak ? "Focus complete. Take a break." : "Break complete.") : "Session ended.");
        render();
    }

    private void resetTimer() {
        state = IDLE;
        token = "";
        deadline = 0;
        duration = (isBreak ? breakMinutes : focusMinutes) * 60000L;
        remaining = duration;
        save();
        render();
    }

    private void switchMode() {
        if (state != IDLE) return;
        isBreak = !isBreak;
        resetTimer();
    }

    private void settings() {
        if (state != IDLE) return;
        LinearLayout form = dialogBody();
        EditText focus = input("Focus minutes (1–180)", Integer.toString(focusMinutes), true);
        focus.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText rest = input("Break minutes (1–60)", Integer.toString(breakMinutes), true);
        rest.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        Spinner selected = subjects(subject);
        CheckBox awake = new CheckBox(this);
        awake.setText("Keep screen awake while timing");
        awake.setChecked(keepAwake);
        form.addView(focus);
        form.addView(rest);
        form.addView(selected);
        form.addView(awake);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Focus settings")
                .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int f = Integer.parseInt(focus.getText().toString());
                int b = Integer.parseInt(rest.getText().toString());
                if (f < 1 || f > 180 || b < 1 || b > 60) throw new IllegalArgumentException();
                focusMinutes = f;
                breakMinutes = b;
                subject = selected.getSelectedItem().toString();
                keepAwake = awake.isChecked();
                dialog.dismiss();
                resetTimer();
            } catch (RuntimeException e) {
                toast("Enter focus 1–180 and break 1–60 minutes");
            }
        }));
        dialog.show();
    }

    private void showSummary() {
        long today = StudyDates.startOfDay(0);
        long week = StudyDates.startOfDay(-6);
        LinearLayout stats = card(content);
        stats.addView(text("Focus history", 19, true));
        try {
            stats.addView(text("Today: " + StudyDates.duration(store.focusSeconds(today)), 16, false));
            stats.addView(text("Last 7 days: " + StudyDates.duration(store.focusSeconds(week)), 16, false));
            List<StudyDatabase.Session> sessions = store.sessions(week);
            if (sessions.isEmpty()) {
                stats.addView(text("No sessions recorded this week.", 13, false));
            }
            int shown = 0;
            for (StudyDatabase.Session session : sessions) {
                if (shown++ >= 20) break;
                LinearLayout item = card(content);
                item.addView(text(session.subject + " · " + StudyDates.duration(session.seconds), 16, true));
                item.addView(text(StudyDates.date(session.ended) +
                        (session.completed ? " · Completed" : " · Ended early"), 12, false));
                item.addView(button("Remove history entry", () -> confirm(
                        "Remove session?", "This removes only the selected timer history entry.", () -> {
                            store.deleteSession(session.id);
                            render();
                        })));
            }
        } catch (RuntimeException e) {
            stats.addView(text("Focus history could not be loaded.", 13, false));
        }
    }

    private void updateAwake() {
        if (resumed && keepAwake && state == RUNNING) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        updateAwake();
        updateClock();
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(tick);
        save();
        updateAwake();
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }
}
