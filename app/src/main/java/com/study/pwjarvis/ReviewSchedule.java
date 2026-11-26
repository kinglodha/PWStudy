package com.study.pwjarvis;

/** Simple local spaced repetition. Not a prediction of mastery. */
public final class ReviewSchedule {
    private static final long MINUTE = 60000L;
    private static final long DAY = 86400000L;
    public final long due;
    public final int intervalDays;

    private ReviewSchedule(long due, int intervalDays) {
        this.due = due;
        this.intervalDays = intervalDays;
    }

    public static ReviewSchedule next(int previousDays, boolean remembered, long now) {
        if (!remembered) return new ReviewSchedule(now + 10 * MINUTE, 0);
        int days;
        if (previousDays <= 0) {
            days = 1;
        } else if (previousDays == 1) {
            days = 3;
        } else {
            days = (int) Math.min(180L, Math.max(1L, previousDays * 2L));
        }
        return new ReviewSchedule(now + DAY * days, days);
    }
}
