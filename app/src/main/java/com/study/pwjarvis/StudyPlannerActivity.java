package com.study.pwjarvis;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.*;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Personal tasks and revision deadlines, stored entirely on the phone. */
public class StudyPlannerActivity extends StudyToolActivity {
    private String filter = "Upcoming";
    private String subjectFilter = "All subjects";
    private String query = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            filter = state.getString("filter", "Upcoming");
            subjectFilter = state.getString("subject", "All subjects");
            query = state.getString("query", "");
        }
        render();
    }

    private void render() {
        screen("Study planner", "Plan lectures, practice and revision without changing your PDF library.");
        addPair(content, "+ New task", () -> edit(null), "Search / filter", this::filters);
        List<StudyDatabase.Task> tasks;
        try {
            tasks = store.tasks();
        } catch (RuntimeException e) {
            content.addView(text("Unable to open planner data.", 15, false));
            return;
        }
        String today = StudyDates.today();
        int overdue = 0;
        int todayCount = 0;
        int completed = 0;
        for (StudyDatabase.Task task : tasks) {
            if (task.done) completed++;
            else if (task.due.compareTo(today) < 0) overdue++;
            else if (task.due.equals(today)) todayCount++;
        }
        LinearLayout summary = card(content);
        summary.addView(text(todayCount + " due today · " + overdue + " overdue", 18, true));
        summary.addView(text(completed + " completed of " + tasks.size() + " tasks", 13, false));
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(Math.max(1, tasks.size()));
        progress.setProgress(completed);
        summary.addView(progress);
        content.addView(text(filter + " · " + subjectFilter +
                (query.isEmpty() ? "" : " · " + query), 14, true));

        int shown = 0;
        for (StudyDatabase.Task task : tasks) {
            if (!matches(task, today)) continue;
            showTask(task, today);
            shown++;
        }
        if (shown == 0) {
            content.addView(text("No matching tasks. Add a task or change your filters.", 14, false));
        }
    }

    private boolean matches(StudyDatabase.Task task, String today) {
        if (!"All subjects".equals(subjectFilter) && !subjectFilter.equals(task.subject)) return false;
        if (!query.isEmpty()) {
            String haystack = (task.title + " " + task.notes + " " + task.subject).toLowerCase(Locale.ROOT);
            if (!haystack.contains(query.toLowerCase(Locale.ROOT))) return false;
        }
        int date = task.due.compareTo(today);
        switch (filter) {
            case "Today": return !task.done && date == 0;
            case "Overdue": return !task.done && date < 0;
            case "Upcoming": return !task.done && date >= 0;
            case "Completed": return task.done;
            default: return true;
        }
    }

    private void showTask(StudyDatabase.Task task, String today) {
        LinearLayout item = card(content);
        CheckBox done = new CheckBox(this);
        done.setText(task.title);
        done.setTextColor(0xffffffff);
        done.setChecked(task.done);
        item.addView(done);
        done.setOnCheckedChangeListener((v, checked) -> {
            try {
                store.setDone(task.id, checked);
                render();
            } catch (RuntimeException e) {
                toast("Could not update task");
            }
        });
        String when = task.due.compareTo(today) < 0 && !task.done ? "Overdue · " : "";
        item.addView(text(task.subject + " · " + when + task.due, 13, false));
        if (!task.notes.isEmpty()) {
            TextView note = text(task.notes, 13, false);
            note.setMaxLines(5);
            item.addView(note);
        }
        addPair(item, "Edit", () -> edit(task), "Delete", () -> confirm(
                "Delete task?", "Only this planner entry will be removed.", () -> {
                    store.deleteTask(task.id);
                    render();
                }));
    }

    private void edit(StudyDatabase.Task existing) {
        LinearLayout form = dialogBody();
        EditText title = input("Task title", existing == null ? "" : existing.title, true);
        Spinner subject = subjects(existing == null ? "Biology" : existing.subject);
        EditText date = input("YYYY-MM-DD", existing == null ? StudyDates.today() : existing.due, true);
        EditText notes = input("Notes / lecture number / chapters", existing == null ? "" : existing.notes, false);
        form.addView(title);
        form.addView(subject);
        form.addView(date);
        form.addView(button("Pick due date", () -> {
            Calendar initial;
            try {
                initial = StudyDates.calendar(date.getText().toString());
            } catch (Exception e) {
                initial = Calendar.getInstance();
            }
            new DatePickerDialog(this, (view, year, month, day) ->
                    date.setText(String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day)),
                    initial.get(Calendar.YEAR), initial.get(Calendar.MONTH),
                    initial.get(Calendar.DAY_OF_MONTH)).show();
        }));
        addPair(form, "Today", () -> date.setText(StudyDates.today()),
                "Tomorrow", () -> date.setText(StudyDates.plusDays(1)));
        form.addView(notes);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "New study task" : "Edit task")
                .setView(dialogScroll(form))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                store.saveTask(existing == null ? 0 : existing.id,
                        title.getText().toString(), subject.getSelectedItem().toString(),
                        date.getText().toString(), notes.getText().toString());
                dialog.dismiss();
                render();
            } catch (RuntimeException e) {
                toast(e.getMessage() == null ? "Unable to save task" : e.getMessage());
            }
        }));
        dialog.show();
    }

    private void filters() {
        LinearLayout form = dialogBody();
        EditText search = input("Search title or notes", query, true);
        form.addView(search);
        Spinner status = new Spinner(this);
        String[] states = {"All tasks", "Today", "Overdue", "Upcoming", "Completed"};
        status.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, states));
        status.setSelection(Math.max(0, java.util.Arrays.asList(states).indexOf(filter)));
        form.addView(status);
        Spinner subjects = new Spinner(this);
        String[] names = {"All subjects", "Physics", "Chemistry", "Biology", "Other"};
        subjects.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        subjects.setSelection(Math.max(0, java.util.Arrays.asList(names).indexOf(subjectFilter)));
        form.addView(subjects);
        new AlertDialog.Builder(this).setTitle("Planner filters").setView(form)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Reset", (d,w) -> {
                    query = "";
                    filter = "All tasks";
                    subjectFilter = "All subjects";
                    render();
                })
                .setPositiveButton("Apply", (d,w) -> {
                    query = search.getText().toString().trim();
                    filter = status.getSelectedItem().toString();
                    subjectFilter = subjects.getSelectedItem().toString();
                    render();
                }).show();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("filter", filter);
        state.putString("subject", subjectFilter);
        state.putString("query", query);
        super.onSaveInstanceState(state);
    }
}
