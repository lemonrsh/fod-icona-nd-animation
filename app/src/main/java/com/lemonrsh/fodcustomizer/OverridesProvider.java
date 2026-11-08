package com.lemonrsh.fodcustomizer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.Process;

import java.io.File;
import java.io.FileNotFoundException;

/** Read-only IPC bridge. Files stay private; callers are checked by their actual Binder UID. */
public final class OverridesProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        enforceSystemUiOrSelf();
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only provider");
        Slot slot = OverridesContract.slotFor(uri);
        if (slot == null) throw new FileNotFoundException("Unknown override");
        File file = new OverrideRepository(providerContext()).fileFor(slot);
        if (!file.isFile()) throw new FileNotFoundException("No override selected");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        enforceSystemUiOrSelf();
        Slot slot = OverridesContract.slotFor(uri);
        if (slot == null) return null;
        return slot.kind == Slot.Kind.PNG ? "image/png" : "application/json";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        enforceSystemUiOrSelf();
        return null;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read-only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read-only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read-only"); }

    private android.content.Context providerContext() {
        android.content.Context context = getContext();
        if (context == null) throw new IllegalStateException("Provider is not attached");
        return context;
    }

    private void enforceSystemUiOrSelf() {
        int caller = Binder.getCallingUid();
        int ownUid = Process.myUid();
        PackageManager pm = providerContext().getPackageManager();
        String[] packages = caller == ownUid ? null : pm.getPackagesForUid(caller);
        if (!CallerPolicy.mayRead(caller, ownUid, packages)) {
            throw new SecurityException("Only SystemUI may read fingerprint overrides");
        }
    }
}
