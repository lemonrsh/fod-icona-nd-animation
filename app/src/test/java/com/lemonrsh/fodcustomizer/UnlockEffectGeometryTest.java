package com.lemonrsh.fodcustomizer;

import static org.junit.Assert.*;

import org.junit.Test;

public class UnlockEffectGeometryTest {
    @Test public void effectIsCenteredOnSensorInRootCoordinates() {
        UnlockEffectGeometry.Box box = UnlockEffectGeometry.place(540, 2100, 75,
                10, 24, 1080, 2400);
        assertNotNull(box);
        assertEquals(300, box.size);
        assertEquals(530, box.left + box.size / 2);
        assertEquals(2076, box.top + box.size / 2);
    }

    @Test public void effectSizeIsBoundedByDisplay() {
        UnlockEffectGeometry.Box box = UnlockEffectGeometry.place(200, 600, 120,
                0, 0, 400, 800);
        assertNotNull(box);
        assertEquals(320, box.size);
    }

    @Test public void unknownInvalidAndOffscreenSensorsAreRejected() {
        assertNull(UnlockEffectGeometry.place(100, 100, 0, 0, 0, 400, 800));
        assertNull(UnlockEffectGeometry.place(100, 100, -1, 0, 0, 400, 800));
        assertNull(UnlockEffectGeometry.place(Float.NaN, 100, 50, 0, 0, 400, 800));
        assertNull(UnlockEffectGeometry.place(100, Float.POSITIVE_INFINITY, 50, 0, 0, 400, 800));
        assertNull(UnlockEffectGeometry.place(100, 100, Float.NaN, 0, 0, 400, 800));
        assertNull(UnlockEffectGeometry.place(500, 100, 50, 0, 0, 400, 800));
        assertNull(UnlockEffectGeometry.place(100, 100, 50, 0, 0, 0, 800));
        assertNull(UnlockEffectGeometry.place(100, 100, 201, 0, 0, 400, 800));
    }

    @Test public void displayedDurationIsBounded() {
        assertEquals(250, UnlockEffectGeometry.durationMillis(5));
        assertEquals(2000, UnlockEffectGeometry.durationMillis(2000));
        assertEquals(2500, UnlockEffectGeometry.durationMillis(30_000));
        assertEquals(1500, UnlockEffectGeometry.durationMillis(Double.NaN));
        assertEquals(1500, UnlockEffectGeometry.durationMillis(-1));
    }
}
