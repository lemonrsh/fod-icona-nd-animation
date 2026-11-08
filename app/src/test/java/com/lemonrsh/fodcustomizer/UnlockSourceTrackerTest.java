package com.lemonrsh.fodcustomizer;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class UnlockSourceTrackerTest {
    @Test public void recognizesOnlyTheSystemBiometricFingerprintSource() {
        assertTrue(UnlockSourceTracker.isFingerprint(
                "android.hardware.biometrics.BiometricSourceType", "FINGERPRINT"));
        assertFalse(UnlockSourceTracker.isFingerprint(
                "android.hardware.biometrics.BiometricSourceType", "FACE"));
        assertFalse(UnlockSourceTracker.isFingerprint(
                "android.hardware.biometrics.BiometricSourceType", "IRIS"));
        assertFalse(UnlockSourceTracker.isFingerprint("other.BiometricSourceType", "FINGERPRINT"));
        assertFalse(UnlockSourceTracker.isFingerprint(null, "FINGERPRINT"));
        assertFalse(UnlockSourceTracker.isFingerprint(
                "android.hardware.biometrics.BiometricSourceType", null));
    }

    @Test public void onlyReturnsAControllerInsideAFingerprintCall() {
        UnlockSourceTracker tracker = new UnlockSourceTracker();
        Object controller = new Object();
        assertNull(tracker.fingerprintController());
        tracker.enter(false, controller);
        assertNull(tracker.fingerprintController());
        tracker.exit();
        tracker.enter(true, controller);
        assertSame(controller, tracker.fingerprintController());
        tracker.exit();
        assertNull(tracker.fingerprintController());
        tracker.exit(); // Extra cleanup must be harmless.
        assertNull(tracker.fingerprintController());
    }

    @Test public void nestedCallsRestoreThePreviousSourceAndController() {
        UnlockSourceTracker tracker = new UnlockSourceTracker();
        Object outer = new Object();
        Object nested = new Object();
        tracker.enter(true, outer);
        tracker.enter(false, nested);
        assertNull(tracker.fingerprintController());
        tracker.exit();
        assertSame(outer, tracker.fingerprintController());
        tracker.enter(true, nested);
        assertSame(nested, tracker.fingerprintController());
        tracker.exit();
        assertSame(outer, tracker.fingerprintController());
        tracker.exit();
        assertNull(tracker.fingerprintController());
    }

    @Test public void sourceContextDoesNotLeakAcrossThreads() throws InterruptedException {
        UnlockSourceTracker tracker = new UnlockSourceTracker();
        Object controller = new Object();
        tracker.enter(true, controller);
        AtomicReference<Object> otherThread = new AtomicReference<>(controller);
        Thread thread = new Thread(() -> otherThread.set(tracker.fingerprintController()));
        thread.start();
        thread.join();
        assertNull(otherThread.get());
        assertSame(controller, tracker.fingerprintController());
        tracker.exit();
    }
}
