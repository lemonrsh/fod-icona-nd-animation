package com.lemonrsh.fodcustomizer;

import android.content.ContentResolver;
import android.net.Uri;

final class OverridesContract {
    static final String AUTHORITY = "com.lemonrsh.fodcustomizer.overrides";

    private OverridesContract() {}

    static Uri uriFor(Slot slot) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY).appendPath(slot.fileName).build();
    }

    static Slot slotFor(Uri uri) {
        if (uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
                || !AUTHORITY.equals(uri.getAuthority()) || uri.getPathSegments().size() != 1) {
            return null;
        }
        return Slot.fromFileName(uri.getPathSegments().get(0));
    }
}
