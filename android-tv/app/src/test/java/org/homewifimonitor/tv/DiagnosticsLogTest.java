package org.homewifimonitor.tv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DiagnosticsLogTest {
    @Test
    public void keepsOnlyRecentEntries() {
        DiagnosticsLog log = new DiagnosticsLog(3);

        log.add("one");
        log.add("two");
        log.add("three");
        log.add("four");

        String text = log.snapshotText();
        assertFalse(text.contains("one"));
        assertTrue(text.contains("two"));
        assertTrue(text.contains("three"));
        assertTrue(text.contains("four"));
    }

    @Test
    public void formatsEmptyLogForTvDisplay() {
        DiagnosticsLog log = new DiagnosticsLog(3);

        assertEquals("暂无诊断日志", log.snapshotText());
    }
}
