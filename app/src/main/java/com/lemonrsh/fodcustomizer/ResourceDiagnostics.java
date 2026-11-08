package com.lemonrsh.fodcustomizer;

import java.util.Locale;

/** Selects harmless resource names for the bounded SystemUI diagnostic log. */
final class ResourceDiagnostics {
    private ResourceDiagnostics() {}

    static boolean shouldLog(String packageName, String type, String entryName) {
        if (!ResourceMatcher.SYSTEM_UI.equals(packageName) || type == null || entryName == null) {
            return false;
        }
        if (!"raw".equals(type) && !"drawable".equals(type) && !"mipmap".equals(type)
                && !"anim".equals(type) && !"animator".equals(type)) {
            return false;
        }
        String name = entryName.toLowerCase(Locale.ROOT);
        return name.contains("fingerprint") || name.contains("udfps") || name.contains("sfps")
                || name.contains("fod") || name.contains("under_display")
                || name.contains("underdisplay");
    }
}
