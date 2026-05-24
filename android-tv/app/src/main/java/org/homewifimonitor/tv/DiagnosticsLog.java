package org.homewifimonitor.tv;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

final class DiagnosticsLog {
    private final int maxEntries;
    private final Deque<String> entries = new ArrayDeque<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);

    DiagnosticsLog(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
    }

    synchronized void add(String message) {
        String safeMessage = message == null ? "" : message.trim();
        if (safeMessage.isEmpty()) return;
        entries.addLast(timeFormat.format(new Date()) + " " + safeMessage);
        while (entries.size() > maxEntries) {
            entries.removeFirst();
        }
    }

    synchronized String latestLine() {
        String latest = entries.peekLast();
        return latest == null ? "" : latest;
    }

    synchronized String snapshotText() {
        if (entries.isEmpty()) return "暂无诊断日志";
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String entry : entries) {
            if (!first) builder.append('\n');
            first = false;
            builder.append(entry);
        }
        return builder.toString();
    }
}
