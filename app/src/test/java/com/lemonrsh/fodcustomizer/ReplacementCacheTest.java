package com.lemonrsh.fodcustomizer;

import static org.junit.Assert.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class ReplacementCacheTest {
    @Test public void cachesPresentFileAndReloadsAfterTtl() {
        final long[] now = {0};
        AtomicInteger reads = new AtomicInteger();
        ReplacementCache cache = new ReplacementCache(slot -> new byte[]{(byte) reads.incrementAndGet()},
                () -> now[0], 60, 5);
        assertArrayEquals(new byte[]{1}, cache.get(Slot.AUTH));
        now[0] = 59;
        assertArrayEquals(new byte[]{1}, cache.get(Slot.AUTH));
        assertEquals(1, reads.get());
        now[0] = 60;
        assertArrayEquals(new byte[]{2}, cache.get(Slot.AUTH));
        assertEquals(2, reads.get());
        cache.clear();
        assertArrayEquals(new byte[]{3}, cache.get(Slot.AUTH));
    }

    @Test public void missingAndUnreadableFilesFallBackAndAreRetried() {
        final long[] now = {0};
        AtomicInteger reads = new AtomicInteger();
        ReplacementCache cache = new ReplacementCache(slot -> {
            int count = reads.incrementAndGet();
            if (count == 1) throw new IOException("not ready");
            if (count == 2) throw new SecurityException("not ready");
            return new byte[]{42};
        }, () -> now[0], 60, 5);
        assertNull(cache.get(Slot.ICON));
        now[0] = 4;
        assertNull(cache.get(Slot.ICON));
        assertEquals(1, reads.get());
        now[0] = 5;
        assertNull(cache.get(Slot.ICON));
        now[0] = 10;
        assertArrayEquals(new byte[]{42}, cache.get(Slot.ICON));
        assertEquals(3, reads.get());
    }

    @Test public void slotsAreIndependentAndEmptyFilesDoNotOverride() {
        final long[] now = {100};
        AtomicInteger reads = new AtomicInteger();
        ReplacementCache cache = new ReplacementCache(slot -> {
            reads.incrementAndGet();
            return slot == Slot.AUTH ? new byte[]{1} : new byte[0];
        }, () -> now[0], 60, 5);
        assertArrayEquals(new byte[]{1}, cache.get(Slot.AUTH));
        assertNull(cache.get(Slot.REAR_AUTH));
        assertNull(cache.get(Slot.REAR_AUTH));
        assertEquals(2, reads.get());
        now[0] = 90; // A non-monotonic test clock should force a fresh read.
        assertArrayEquals(new byte[]{1}, cache.get(Slot.AUTH));
        assertEquals(3, reads.get());
    }
}
