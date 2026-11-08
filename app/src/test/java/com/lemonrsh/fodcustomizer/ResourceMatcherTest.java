package com.lemonrsh.fodcustomizer;

import static org.junit.Assert.*;
import org.junit.Test;

public class ResourceMatcherTest {
    @Test public void matchesTheTwoExactDeviceNamesSeparately() {
        assertEquals(Slot.AUTH, ResourceMatcher.match("com.android.systemui", "raw",
                "biometricprompt_sfps_fingerprint_authenticating"));
        assertEquals(Slot.REAR_AUTH, ResourceMatcher.match("com.android.systemui", "raw",
                "biometricprompt_sfps_rear_display_fingerprint_authenticating"));
        assertNotEquals(Slot.AUTH, ResourceMatcher.match("com.android.systemui", "raw",
                "biometricprompt_sfps_rear_display_fingerprint_authenticating"));
    }

    @Test public void matchesAospUdfpsLockScreenAndAodSeparately() {
        assertEquals(Slot.UDFPS_LOCKSCREEN, ResourceMatcher.match("com.android.systemui", "raw", "udfps_lockscreen_fp"));
        assertEquals(Slot.UDFPS_AOD, ResourceMatcher.match("com.android.systemui", "raw", "udfps_aod_fp"));
        assertNotEquals(Slot.UDFPS_AOD, ResourceMatcher.match("com.android.systemui", "raw", "udfps_lockscreen_fp"));
        assertNotEquals(Slot.AUTH, ResourceMatcher.match("com.android.systemui", "raw", "udfps_lockscreen_fp"));
        assertNull(ResourceMatcher.match("com.android.systemui", "drawable", "udfps_lockscreen_fp"));
        assertNull(ResourceMatcher.match("com.android.systemui", "raw", "udfps_aod_fp_vendor"));
        assertNull(ResourceMatcher.match("com.vendor.systemui", "raw", "udfps_lockscreen_fp"));
    }

    @Test public void unlockEffectIsASeparateOptInSlotNotARawResourceOverride() {
        assertEquals(Slot.Kind.LOTTIE, Slot.UNLOCK_EFFECT.kind);
        assertEquals(Slot.UNLOCK_EFFECT, Slot.fromFileName("unlock_effect.json"));
        assertNotEquals(Slot.UNLOCK_EFFECT, Slot.UDFPS_AOD);
        assertNull(ResourceMatcher.match("com.android.systemui", "raw", "unlock_effect"));
    }

    @Test public void matchesOnlyKnownResourceTypesAndPackage() {
        assertEquals(Slot.ICON, ResourceMatcher.match("com.android.systemui", "drawable", "ic_fingerprint"));
        assertEquals(Slot.BACKGROUND, ResourceMatcher.match("com.android.systemui", "drawable", "fingerprint_bg"));
        assertEquals(Slot.AUTH, ResourceMatcher.match("com.android.systemui", "raw", "fingerprint_authenticating"));
        assertEquals(Slot.SUCCESS, ResourceMatcher.match("com.android.systemui", "raw", "fingerprint_to_success"));
        assertEquals(Slot.ERROR, ResourceMatcher.match("com.android.systemui", "raw", "fingerprint_to_error"));
        assertNull(ResourceMatcher.match("android", "drawable", "ic_fingerprint"));
        assertNull(ResourceMatcher.match("com.example.other", "raw", "fingerprint_to_success"));
        assertNull(ResourceMatcher.match("com.android.systemui", "drawable", "fingerprint_authenticating"));
        assertNull(ResourceMatcher.match("com.android.systemui", "raw", "ic_fingerprint"));
        assertNull(ResourceMatcher.match("com.android.systemui", "raw", "fingerprint_to_success_extra"));
        assertNull(ResourceMatcher.match("com.android.systemui", "raw", "my_fingerprint_authenticating"));
        assertNull(ResourceMatcher.match("com.android.systemui", "raw", null));
        assertNull(ResourceMatcher.match(null, "raw", "fingerprint_to_success"));
    }

    @Test public void onlyRunsInSystemUiProcesses() {
        assertTrue(ResourceMatcher.isSystemUiProcess("com.android.systemui", "com.android.systemui"));
        assertTrue(ResourceMatcher.isSystemUiProcess("com.android.systemui", "com.android.systemui:secondary"));
        assertFalse(ResourceMatcher.isSystemUiProcess("com.android.systemui", "com.android.systemui.other"));
        assertFalse(ResourceMatcher.isSystemUiProcess("com.android.systemui", "android"));
        assertFalse(ResourceMatcher.isSystemUiProcess("com.example.other", "com.android.systemui"));
        assertFalse(ResourceMatcher.isSystemUiProcess("com.android.systemui", null));
    }

    @Test public void filesAreStrictlyAllowlisted() {
        for (Slot slot : Slot.values()) assertEquals(slot, Slot.fromFileName(slot.fileName));
        assertNull(Slot.fromFileName("../auth.json"));
        assertNull(Slot.fromFileName("auth.json/../fingerprint_icon.png"));
        assertNull(Slot.fromFileName("unknown.json"));
        assertNull(Slot.fromFileName(null));
    }
}
