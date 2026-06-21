package nl.local.energyoptimizer;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class PhaseLogger {
    private static final int MAX_LINES = 200;
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public synchronized void add(String message) {
        lines.addFirst(timeFormat.format(new Date()) + "  " + message);
        while (lines.size() > MAX_LINES) {
            lines.removeLast();
        }
    }

    public synchronized String render() {
        List<String> snapshot = new ArrayList<>(lines);
        StringBuilder builder = new StringBuilder();
        for (String line : snapshot) {
            builder.append(line).append('\n');
        }
        return builder.toString();
    }
}
