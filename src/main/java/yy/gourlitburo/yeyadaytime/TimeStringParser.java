package yy.gourlitburo.yeyadaytime;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeStringParser {
    private static final Pattern patTimeNamed = Pattern.compile("day|night|dawn");
    private static final Pattern patTimeFull = Pattern.compile("(\\d{2}):(\\d{2})");
    private static final Pattern patTimeShort = Pattern.compile("(\\d+)(am|AM|pm|PM)");
    private static final Pattern patTimeTicks = Pattern.compile("(\\d+)ticks");

    public static long parse(String time) {
        Matcher m;

        m = patTimeNamed.matcher(time);
        if (m.matches()) {
            String name = m.group(0);
            if (name.equals("day")) return 1000;
            if (name.equals("night")) return 13000;
            if (name.equals("dawn")) return 23000; // BE's /time set sunrise
        }

        m = patTimeFull.matcher(time);
        if (m.matches()) {
            int hr = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));

            long ticks = (hr - 6) * 1000;
            ticks += min * (16 + 2 / 3); // 16.666...
            return ticks;
        }

        m = patTimeShort.matcher(time);
        if (m.matches()) {
            int hr = Integer.parseInt(m.group(1));
            int period = m.group(2).equalsIgnoreCase("am") ? 0 : 1;

            long ticks = period == 1 ? 6000 : 0;
            ticks += (hr - 6) * 1000;
            return ticks;
        }

        m = patTimeTicks.matcher(time);
        if (m.matches()) {
            long ticks = Integer.parseInt(m.group(1));
            return ticks;
        }

        throw new IllegalArgumentException("Invalid time string.");
    }
}
