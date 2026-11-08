package com.lemonrsh.fodcustomizer;

/** Exact resource matching prevents unrelated drawables and other apps being replaced. */
final class ResourceMatcher {
    static final String SYSTEM_UI = "com.android.systemui";

    private ResourceMatcher() {}

    static boolean isSystemUiProcess(String packageName, String processName) {
        return SYSTEM_UI.equals(packageName)
                && processName != null
                && (SYSTEM_UI.equals(processName) || processName.startsWith(SYSTEM_UI + ":"));
    }

    static Slot match(String packageName, String type, String entryName) {
        if (!SYSTEM_UI.equals(packageName) || entryName == null) return null;
        if ("drawable".equals(type)) {
            switch (entryName) {
                case "ic_fingerprint": return Slot.ICON;
                case "fingerprint_bg": return Slot.BACKGROUND;
                default: return null;
            }
        }
        if ("raw".equals(type)) {
            switch (entryName) {
                case "udfps_lockscreen_fp": return Slot.UDFPS_LOCKSCREEN;
                case "udfps_aod_fp": return Slot.UDFPS_AOD;
                case "biometricprompt_sfps_fingerprint_authenticating":
                case "fingerprint_authenticating":
                    return Slot.AUTH;
                case "biometricprompt_sfps_rear_display_fingerprint_authenticating":
                    return Slot.REAR_AUTH;
                case "fingerprint_to_success": return Slot.SUCCESS;
                case "fingerprint_to_error": return Slot.ERROR;
                default: return null;
            }
        }
        return null;
    }
}
