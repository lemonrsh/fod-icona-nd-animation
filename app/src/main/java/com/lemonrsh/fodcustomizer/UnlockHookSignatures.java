package com.lemonrsh.fodcustomizer;

/** Exact AOSP method shapes; unknown ROM methods are left alone. */
final class UnlockHookSignatures {
    private static final String SOURCE_TYPE = "android.hardware.biometrics.BiometricSourceType";

    private UnlockHookSignatures() { }

    static boolean isSource(String name, boolean isStatic, String[] parameters) {
        return !isStatic && ("showUnlockRippleInternal".equals(name) || "showUnlockRipple".equals(name))
                && parameters != null && parameters.length == 1 && SOURCE_TYPE.equals(parameters[0]);
    }

    static boolean isRippleViewMethod(String name, boolean isStatic, String[] parameters) {
        return !isStatic && "startUnlockedRipple".equals(name) && parameters != null
                && parameters.length == 1 && "java.lang.Runnable".equals(parameters[0]);
    }

    static boolean isFallbackRipple(String name, boolean isStatic, String[] parameters) {
        return !isStatic && "showUnlockedRipple".equals(name) && parameters != null && parameters.length == 0;
    }
}
