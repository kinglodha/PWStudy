package com.study.pwjarvis;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Offline study data. No account or network access is necessary. */
public final class StudyDatabase extends SQLiteOpenHelper {
    private static final String NAME = "tweak_study.db";
    private static final int VERSION = 1;
    public static final int NEW = 0, LEARNING = 1, REVIEW = 2;
    public static final String[] SUBJECTS = {"Physics", "Chemistry", "Biology", "Other"};

    public StudyDatabase(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL, subject TEXT NOT NULL, due TEXT NOT NULL," +
                "notes TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0," +
                "created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX task_due ON tasks(done,due)");
        db.execSQL("CREATE TABLE decks (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL, subject TEXT NOT NULL, created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE cards (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "deck_id INTEGER NOT NULL REFERENCES decks(_id) ON DELETE CASCADE," +
                "front TEXT NOT NULL, back TEXT NOT NULL, stage INTEGER NOT NULL DEFAULT 0," +
                "due INTEGER NOT NULL DEFAULT 0, interval_days INTEGER NOT NULL DEFAULT 0," +
                "reviews INTEGER NOT NULL DEFAULT 0, lapses INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX card_due ON cards(deck_id,due)");
        db.execSQL("CREATE TABLE focus_sessions (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "token TEXT UNIQUE NOT NULL, subject TEXT NOT NULL, started INTEGER NOT NULL," +
                "ended INTEGER NOT NULL, seconds INTEGER NOT NULL, completed INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX focus_ended ON focus_sessions(ended)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the initial schema. Refuse unknown migrations rather than dropping study data.
        throw new IllegalStateException("Study data migration required: " + oldVersion + " to " + newVersion);
    }

    public static final class Task {
        public final long id;
        public final String title, subject, due, notes;
        public final boolean done;

        Task(Cursor c) {
            id = c.getLong(0);
            title = c.getString(1);
            subject = c.getString(2);
            due = c.getString(3);
            notes = c.getString(4);
            done = c.getInt(5) != 0;
        }
    }

    public static final class Deck {
        public final long id;
        public final String name, subject;
        public final int count, due;

        Deck(Cursor c) {
            id = c.getLong(0);
            name = c.getString(1);
            subject = c.getString(2);
            count = c.getInt(3);
            due = c.getInt(4);
        }
    }

    public static final class Card {
        public final long id, deckId, due;
        public final String front, back;
        public final int stage, interval, reviews, lapses;

        Card(Cursor c) {
            id = c.getLong(0);
            deckId = c.getLong(1);
            front = c.getString(2);
            back = c.getString(3);
            stage = c.getInt(4);
            due = c.getLong(5);
            interval = c.getInt(6);
            reviews = c.getInt(7);
            lapses = c.getInt(8);
        }
    }

    public static final class Session {
        public final long id, started, ended;
        public final String subject;
        public final int seconds;
        public final boolean completed;

        Session(Cursor c) {
            id = c.getLong(0);
            subject = c.getString(1);
            started = c.getLong(2);
            ended = c.getLong(3);
            seconds = c.getInt(4);
            completed = c.getInt(5) != 0;
        }
    }

    private static String required(String input, int max, String label) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Enter " + label);
        if (value.length() > max) throw new IllegalArgumentException(label + " is too long");
        return value;
    }

    private static String subject(String value) {
        for (String item : SUBJECTS) {
            if (item.equals(value)) return item;
        }
        return "Other";
    }

    public long saveTask(long id, String title, String subject, String due, String notes) {
        ContentValues values = new ContentValues();
        values.put("title", required(title, 200, "task title"));
        values.put("subject", subject(subject));
        values.put("due", StudyDates.validDate(due));
        if (notes != null && notes.length() > 4000) {
            throw new IllegalArgumentException("Notes are too long");
        }
        values.put("notes", notes == null ? "" : notes);
        SQLiteDatabase db = getWritableDatabase();
        if (id > 0) {
            if (db.update("tasks", values, "_id=?", new String[]{Long.toString(id)}) != 1)
                throw new IllegalStateException("Task no longer exists");
            return id;
        }
        values.put("created", System.currentTimeMillis());
        return db.insertOrThrow("tasks", null, values);
    }

    public List<Task> tasks() {
        List<Task> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("tasks",
                new String[]{"_id","title","subject","due","notes","done"},
                null, null, null, null, "done ASC,due ASC,_id DESC")) {
            while (c.moveToNext()) result.add(new Task(c));
        }
        return result;
    }

    public void setDone(long id, boolean done) {
        ContentValues values = new ContentValues();
        values.put("done", done ? 1 : 0);
        getWritableDatabase().update("tasks", values, "_id=?", new String[]{Long.toString(id)});
    }

    public void deleteTask(long id) {
        getWritableDatabase().delete("tasks", "_id=?", new String[]{Long.toString(id)});
    }

    public long saveDeck(long id, String name, String subject) {
        ContentValues values = new ContentValues();
        values.put("name", required(name, 100, "deck name"));
        values.put("subject", subject(subject));
        SQLiteDatabase db = getWritableDatabase();
        if (id > 0) {
            if (db.update("decks", values, "_id=?", new String[]{Long.toString(id)}) != 1)
                throw new IllegalStateException("Deck no longer exists");
            return id;
        }
        values.put("created", System.currentTimeMillis());
        return db.insertOrThrow("decks", null, values);
    }

    public List<Deck> decks(long now) {
        List<Deck> result = new ArrayList<>();
        String sql = "SELECT d._id,d.name,d.subject,COUNT(c._id)," +
                "SUM(CASE WHEN c._id IS NOT NULL AND c.due<=? THEN 1 ELSE 0 END)" +
                " FROM decks d LEFT JOIN cards c ON d._id=c.deck_id" +
                " GROUP BY d._id,d.name,d.subject ORDER BY d.name COLLATE NOCASE";
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{Long.toString(now)})) {
            while (c.moveToNext()) result.add(new Deck(c));
        }
        return result;
    }

    public void deleteDeck(long id) {
        getWritableDatabase().delete("decks", "_id=?", new String[]{Long.toString(id)});
    }

    public long saveCard(long id, long deck, String front, String back) {
        ContentValues values = new ContentValues();
        values.put("deck_id", deck);
        values.put("front", required(front, 4000, "question"));
        values.put("back", required(back, 8000, "answer"));
        SQLiteDatabase db = getWritableDatabase();
        if (id > 0) {
            if (db.update("cards", values, "_id=? AND deck_id=?",
                    new String[]{Long.toString(id),Long.toString(deck)}) != 1)
                throw new IllegalStateException("Card no longer exists");
            return id;
        }
        return db.insertOrThrow("cards", null, values);
    }

    private static final String[] CARD_COLUMNS = {
            "_id","deck_id","front","back","stage","due","interval_days","reviews","lapses"
    };

    public List<Card> cards(long deck, boolean dueOnly, long now) {
        List<Card> result = new ArrayList<>();
        String where = dueOnly ? "deck_id=? AND due<=?" : "deck_id=?";
        String[] args = dueOnly ? new String[]{Long.toString(deck),Long.toString(now)} :
                new String[]{Long.toString(deck)};
        try (Cursor c = getReadableDatabase().query("cards", CARD_COLUMNS, where, args,
                null, null, dueOnly ? "due ASC,_id ASC" : "_id DESC")) {
            while (c.moveToNext()) result.add(new Card(c));
        }
        return result;
    }

    public void deleteCard(long id) {
        getWritableDatabase().delete("cards", "_id=?", new String[]{Long.toString(id)});
    }

    public void resetCard(long id) {
        ContentValues values = new ContentValues();
        values.put("stage", NEW);
        values.put("due", 0);
        values.put("interval_days", 0);
        values.put("reviews", 0);
        values.put("lapses", 0);
        getWritableDatabase().update("cards", values, "_id=?", new String[]{Long.toString(id)});
    }

    /** Each rating writes exactly one review and a future due time. */
    public void rate(Card card, boolean remembered, long now) {
        ReviewSchedule next = ReviewSchedule.next(card.interval, remembered, now);
        ContentValues values = new ContentValues();
        values.put("stage", remembered ? REVIEW : LEARNING);
        values.put("due", next.due);
        values.put("interval_days", next.intervalDays);
        values.put("reviews", card.reviews + 1);
        values.put("lapses", card.lapses + (remembered ? 0 : 1));
        int changed = getWritableDatabase().update("cards", values,
                "_id=? AND reviews=?", new String[]{Long.toString(card.id),Integer.toString(card.reviews)});
        if (changed != 1) throw new IllegalStateException("Card changed; restart review");
    }

    /** Unique token prevents duplicate history after a restart or repeated finish. */
    public void recordFocus(String token, String subject, long started, long ended,
                            int seconds, boolean complete) {
        if (seconds <= 0 || ended < started) return;
        ContentValues values = new ContentValues();
        values.put("token", required(token, 100, "session token"));
        values.put("subject", subject(subject));
        values.put("started", started);
        values.put("ended", ended);
        values.put("seconds", seconds);
        values.put("completed", complete ? 1 : 0);
        getWritableDatabase().insertWithOnConflict("focus_sessions", null, values,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<Session> sessions(long since) {
        List<Session> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("focus_sessions",
                new String[]{"_id","subject","started","ended","seconds","completed"},
                "ended>=?", new String[]{Long.toString(since)}, null, null, "ended DESC")) {
            while (c.moveToNext()) result.add(new Session(c));
        }
        return result;
    }

    public long focusSeconds(long since) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(seconds),0) FROM focus_sessions WHERE ended>=?",
                new String[]{Long.toString(since)})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    public void deleteSession(long id) {
        getWritableDatabase().delete("focus_sessions", "_id=?", new String[]{Long.toString(id)});
    }
}
