package com.lemonrsh.fodcustomizer;

/** Security decision for the exported, read-only bridge to SystemUI. */
final class CallerPolicy {
    private CallerPolicy() {}

    static boolean mayRead(int callingUid, int moduleUid, String[] packagesForUid) {
        if (callingUid == moduleUid) return true;
        if (packagesForUid == null) return false;
        for (String name : packagesForUid) {
            if (ResourceMatcher.SYSTEM_UI.equals(name)) return true;
        }
        return false;
    }
}
