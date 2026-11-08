package com.lemonrsh.fodcustomizer;

import java.util.ArrayDeque;

/** Tracks only the source of a synchronous SystemUI unlock-ripple call, per thread. */
final class UnlockSourceTracker {
    private static final String SOURCE_TYPE = "android.hardware.biometrics.BiometricSourceType";
    private final ThreadLocal<ArrayDeque<Source>> sources = new ThreadLocal<>();

    static boolean isFingerprint(String className, String enumName) {
        return SOURCE_TYPE.equals(className) && "FINGERPRINT".equals(enumName);
    }

    void enter(boolean fingerprint, Object controller) {
        ArrayDeque<Source> stack = sources.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            sources.set(stack);
        }
        stack.push(new Source(fingerprint, controller));
    }

    Object fingerprintController() {
        ArrayDeque<Source> stack = sources.get();
        if (stack == null || stack.isEmpty() || !stack.peek().fingerprint) return null;
        return stack.peek().controller;
    }

    void exit() {
        ArrayDeque<Source> stack = sources.get();
        if (stack == null || stack.isEmpty()) return;
        stack.pop();
        if (stack.isEmpty()) sources.remove();
    }

    private static final class Source {
        final boolean fingerprint;
        final Object controller;

        Source(boolean fingerprint, Object controller) {
            this.fingerprint = fingerprint;
            this.controller = controller;
        }
    }
}
