package pers.XiaoShadiao.skydiao.utils.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PickaxeAbilityCountdown {

    private static final Pattern CLOCK = Pattern.compile("(?<![\\d:])(?:(\\d{1,3}):)?(\\d{1,3}):([0-5]\\d)$");
    private static final Pattern UNITS = Pattern.compile(
            "(?i)(?<![\\w:])(?:(\\d{1,3})h\\s*)?(?:(\\d{1,3})m\\s*)?(\\d{1,3})s$");

    private List<String> tabLines = List.of();
    private int timerLine = -1;
    private String timerPrefix = "";
    private long deadlineMillis;

    public void sync(List<String> lines, long nowMillis) {
        // Re-reading the same Tab entry must not restart the countdown.
        if (lines.equals(tabLines)) return;
        boolean wasCounting = timerLine != -1;
        tabLines = lines;
        timerLine = -1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher timer = CLOCK.matcher(line);
            if (!timer.find()) {
                timer = UNITS.matcher(line);
                if (!timer.find()) continue;
            }

            long seconds = number(timer.group(1)) * 3600 + number(timer.group(2)) * 60 + number(timer.group(3));
            long correctedDeadline = nowMillis + seconds * 1000;
            String prefix = line.substring(0, timer.start());
            // Ignore small differences caused by Tab rounding and arrival time.
            if (!wasCounting || !prefix.equals(timerPrefix) || Math.abs(correctedDeadline - deadlineMillis) >= 1500) {
                deadlineMillis = correctedDeadline;
            }
            timerPrefix = prefix;
            timerLine = i;
            return;
        }

        timerPrefix = "";
        deadlineMillis = 0;
    }

    public List<String> lines(long nowMillis) {
        if (timerLine == -1) return tabLines;

        long seconds = Math.max(0, (deadlineMillis - nowMillis + 999) / 1000);
        String time = seconds >= 60
                ? String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
                : seconds + "s";
        List<String> display = new ArrayList<>(tabLines);
        display.set(timerLine, timerPrefix + time);
        return display;
    }

    private static int number(String digits) {
        return digits == null ? 0 : Integer.parseInt(digits);
    }
}
