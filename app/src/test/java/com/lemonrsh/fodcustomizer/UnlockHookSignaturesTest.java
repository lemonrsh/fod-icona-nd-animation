package com.lemonrsh.fodcustomizer;

import static org.junit.Assert.*;

import org.junit.Test;

public class UnlockHookSignaturesTest {
    private static final String BIOMETRIC = "android.hardware.biometrics.BiometricSourceType";

    @Test public void selectsOnlySupportedInstanceSourceMethods() {
        assertTrue(UnlockHookSignatures.isSource("showUnlockRipple", false, new String[]{BIOMETRIC}));
        assertTrue(UnlockHookSignatures.isSource("showUnlockRippleInternal", false, new String[]{BIOMETRIC}));
        assertFalse(UnlockHookSignatures.isSource("showUnlockRipple", true, new String[]{BIOMETRIC}));
        assertFalse(UnlockHookSignatures.isSource("showDwellRipple", false, new String[]{BIOMETRIC}));
        assertFalse(UnlockHookSignatures.isSource("showUnlockRipple", false, new String[0]));
        assertFalse(UnlockHookSignatures.isSource("showUnlockRipple", false, new String[]{"java.lang.String"}));
        assertFalse(UnlockHookSignatures.isSource("showUnlockRipple", false, null));
    }

    @Test public void selectsOnlyTheUnlockRippleNotDwellOrGenericRipples() {
        assertTrue(UnlockHookSignatures.isRippleViewMethod("startUnlockedRipple", false,
                new String[]{"java.lang.Runnable"}));
        assertFalse(UnlockHookSignatures.isRippleViewMethod("startDwellRipple", false,
                new String[]{"java.lang.Runnable"}));
        assertFalse(UnlockHookSignatures.isRippleViewMethod("startUnlockedRipple", true,
                new String[]{"java.lang.Runnable"}));
        assertFalse(UnlockHookSignatures.isRippleViewMethod("startUnlockedRipple", false, new String[0]));
        assertTrue(UnlockHookSignatures.isFallbackRipple("showUnlockedRipple", false, new String[0]));
        assertFalse(UnlockHookSignatures.isFallbackRipple("showDwellRipple", false, new String[0]));
        assertFalse(UnlockHookSignatures.isFallbackRipple("showUnlockedRipple", true, new String[0]));
    }
}
