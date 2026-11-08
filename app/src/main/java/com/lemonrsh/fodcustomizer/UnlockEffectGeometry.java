package com.lemonrsh.fodcustomizer;

/** Pure bounds calculations for a short decorative effect in an existing SystemUI view overlay. */
final class UnlockEffectGeometry {
    private UnlockEffectGeometry() { }

    static Box place(float screenX, float screenY, float sensorRadius,
                     int rootLeft, int rootTop, int rootWidth, int rootHeight) {
        if (!finite(screenX) || !finite(screenY) || !finite(sensorRadius)
                || rootWidth <= 0 || rootHeight <= 0 || sensorRadius <= 0) return null;
        int shorterSide = Math.min(rootWidth, rootHeight);
        if (sensorRadius > shorterSide / 2f) return null;
        float localX = screenX - rootLeft;
        float localY = screenY - rootTop;
        if (localX < 0 || localX >= rootWidth || localY < 0 || localY >= rootHeight) return null;
        int size = Math.round(Math.min(sensorRadius * 4f, shorterSide * 0.8f));
        if (size < 2) return null;
        return new Box(Math.round(localX - size / 2f), Math.round(localY - size / 2f), size);
    }

    static long durationMillis(double duration) {
        if (Double.isNaN(duration) || Double.isInfinite(duration) || duration <= 0) return 1500;
        return (long) Math.max(250, Math.min(2500, duration));
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    static final class Box {
        final int left;
        final int top;
        final int size;

        Box(int left, int top, int size) {
            this.left = left;
            this.top = top;
            this.size = size;
        }
    }
}
