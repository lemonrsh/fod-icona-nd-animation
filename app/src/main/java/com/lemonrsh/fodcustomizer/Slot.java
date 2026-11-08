package com.lemonrsh.fodcustomizer;

/** The only files that may be stored and served by the module. */
enum Slot {
    ICON("fingerprint_icon.png", "Fingerprint icon", "ic_fingerprint · PNG", Kind.PNG),
    BACKGROUND("fingerprint_background.png", "Fingerprint background", "fingerprint_bg · PNG", Kind.PNG),
    UDFPS_LOCKSCREEN("udfps_lockscreen.json", "Lock-screen FOD animation", "udfps_lockscreen_fp · Lottie JSON", Kind.LOTTIE),
    UDFPS_AOD("udfps_aod.json", "Always-on-display FOD animation", "udfps_aod_fp · Lottie JSON", Kind.LOTTIE),
    AUTH("auth.json", "Fingerprint prompt / SFPS animation", "biometricprompt_sfps_fingerprint_authenticating · Lottie JSON", Kind.LOTTIE),
    REAR_AUTH("rear_auth.json", "Rear-display side-sensor prompt", "biometricprompt_sfps_rear_display_fingerprint_authenticating · Lottie JSON", Kind.LOTTIE),
    SUCCESS("success.json", "Success animation", "fingerprint_to_success · Lottie JSON", Kind.LOTTIE),
    ERROR("error.json", "Error animation", "fingerprint_to_error · Lottie JSON", Kind.LOTTIE);

    enum Kind { PNG, LOTTIE }

    final String fileName;
    final String label;
    final String description;
    final Kind kind;

    Slot(String fileName, String label, String description, Kind kind) {
        this.fileName = fileName;
        this.label = label;
        this.description = description;
        this.kind = kind;
    }

    static Slot fromFileName(String fileName) {
        if (fileName == null) return null;
        for (Slot slot : values()) {
            if (slot.fileName.equals(fileName)) return slot;
        }
        return null;
    }
}
