package com.lemonrsh.fodcustomizer;

import static org.junit.Assert.*;
import org.junit.Test;

public class ResourceDiagnosticsTest {
    @Test public void reportsUnmappedUnderDisplayCandidates() {
        assertTrue(ResourceDiagnostics.shouldLog("com.android.systemui", "drawable", "udfps_icon_vendor"));
        assertTrue(ResourceDiagnostics.shouldLog("com.android.systemui", "raw", "custom_fod_animation"));
        assertTrue(ResourceDiagnostics.shouldLog("com.android.systemui", "mipmap", "under_display_sensor"));
        assertTrue(ResourceDiagnostics.shouldLog("com.android.systemui", "animator", "underdisplay_reveal"));
        assertNull(ResourceMatcher.match("com.android.systemui", "drawable", "udfps_icon_vendor"));
    }

    @Test public void reportsTheSharedSfpsNamesWithoutConflatingThemWithUdfps() {
        assertTrue(ResourceDiagnostics.shouldLog("com.android.systemui", "raw",
                "biometricprompt_sfps_fingerprint_authenticating"));
        assertTrue(ResourceDiagnostics.shouldLog("com.android.systemui", "raw",
                "biometricprompt_sfps_rear_display_fingerprint_authenticating"));
        assertTrue(ResourceDiagnostics.shouldLog("com.android.systemui", "drawable", "IC_FINGERPRINT"));
    }

    @Test public void ignoresOtherAppsAndUnrelatedResourceTypes() {
        assertFalse(ResourceDiagnostics.shouldLog("android", "drawable", "ic_fingerprint"));
        assertFalse(ResourceDiagnostics.shouldLog("com.example.app", "raw", "udfps_animation"));
        assertFalse(ResourceDiagnostics.shouldLog("com.android.systemui", "string", "fingerprint_message"));
        assertFalse(ResourceDiagnostics.shouldLog("com.android.systemui", "drawable", "ic_wifi"));
        assertFalse(ResourceDiagnostics.shouldLog(null, "raw", "udfps"));
        assertFalse(ResourceDiagnostics.shouldLog("com.android.systemui", null, "udfps"));
        assertFalse(ResourceDiagnostics.shouldLog("com.android.systemui", "raw", null));
    }
}
