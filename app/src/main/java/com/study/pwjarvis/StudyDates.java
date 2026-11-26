package com.study.pwjarvis;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class StudyDates {
    private StudyDates() {}

    private static SimpleDateFormat iso() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setLenient(false);
        return format;
    }

    public static String validDate(String input) {
        if (input == null || !input.matches("\\d{4}-\\d{2}-\\d{2}"))
            throw new IllegalArgumentException("Use YYYY-MM-DD");
        ParsePosition pos = new ParsePosition(0);
        Date date = iso().parse(input, pos);
        if (date == null || pos.getIndex() != input.length())
            throw new IllegalArgumentException("Invalid calendar date");
        return input;
    }

    public static String today() {
        return iso().format(new Date());
    }

    public static String plusDays(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return iso().format(calendar.getTime());
    }

    public static long startOfDay(int offset) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_YEAR, offset);
        return calendar.getTimeInMillis();
    }

    public static Calendar calendar(String isoDate) {
        Calendar calendar = Calendar.getInstance();
        Date value = iso().parse(validDate(isoDate), new ParsePosition(0));
        if (value != null) calendar.setTime(value);
        return calendar;
    }

    public static String date(long time) {
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(time));
    }

    public static String duration(long seconds) {
        long s = Math.max(0, seconds);
        return s >= 3600 ? (s / 3600) + "h " + ((s / 60) % 60) + "m" :
                (s / 60) + "m " + (s % 60) + "s";
    }
}
