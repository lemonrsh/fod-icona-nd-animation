package com.lemonrsh.fodcustomizer;

import java.io.IOException;
import java.util.EnumMap;

/** Avoids a provider IPC on every SystemUI resource lookup. Missing files are retried. */
final class ReplacementCache {
    interface Source { byte[] read(Slot slot) throws IOException; }
    interface Clock { long nowMillis(); }

    private final Source source;
    private final Clock clock;
    private final long presentTtlMs;
    private final long missingTtlMs;
    private final EnumMap<Slot, Entry> entries = new EnumMap<>(Slot.class);

    ReplacementCache(Source source, Clock clock, long presentTtlMs, long missingTtlMs) {
        if (presentTtlMs < 0 || missingTtlMs < 0) throw new IllegalArgumentException("negative TTL");
        this.source = source;
        this.clock = clock;
        this.presentTtlMs = presentTtlMs;
        this.missingTtlMs = missingTtlMs;
    }

    synchronized byte[] get(Slot slot) {
        long now = clock.nowMillis();
        Entry entry = entries.get(slot);
        if (entry != null && now >= entry.loadedAt) {
            long ttl = entry.data == null ? missingTtlMs : presentTtlMs;
            if (now - entry.loadedAt < ttl) return entry.data;
        }

        byte[] data;
        try {
            data = source.read(slot);
            if (data != null && data.length == 0) data = null;
        } catch (IOException | SecurityException ignored) {
            // A missing/locked provider must never replace a working stock resource.
            data = null;
        }
        entries.put(slot, new Entry(data, now));
        return data;
    }

    synchronized void clear() {
        entries.clear();
    }

    private static final class Entry {
        final byte[] data;
        final long loadedAt;
        Entry(byte[] data, long loadedAt) {
            this.data = data;
            this.loadedAt = loadedAt;
        }
    }
}
