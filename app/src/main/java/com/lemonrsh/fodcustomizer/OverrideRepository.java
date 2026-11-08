package com.lemonrsh.fodcustomizer;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Private device-protected storage: SystemUI can ask the provider even before user unlock. */
final class OverrideRepository {
    private final Context storage;

    OverrideRepository(Context context) {
        storage = context.createDeviceProtectedStorageContext();
    }

    File fileFor(Slot slot) {
        return new File(new File(storage.getFilesDir(), "overrides"), slot.fileName);
    }

    boolean hasOverride(Slot slot) {
        File file = fileFor(slot);
        return file.isFile() && file.length() > 0;
    }

    String displayName(Slot slot) {
        return preferences().getString("name_" + slot.name(), "Custom file");
    }

    void save(Slot slot, byte[] data, String originalName) throws IOException {
        if (data == null || data.length == 0 || data.length > BoundedInput.MAX_BYTES) {
            throw new IOException("The selected file is empty or too large.");
        }
        File target = fileFor(slot);
        File dir = target.getParentFile();
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
            throw new IOException("Could not create the module's private storage.");
        }
        // Write beside the target and rename only after the entire validated file is synced.
        File temporary = File.createTempFile(slot.fileName + ".", ".tmp", dir);
        try {
            try (FileOutputStream stream = new FileOutputStream(temporary)) {
                stream.write(data);
                stream.flush();
                stream.getFD().sync();
            }
            if (!temporary.renameTo(target)) throw new IOException("Could not save the selected file.");
        } finally {
            if (temporary.exists()) temporary.delete();
        }
        String safeName = originalName == null || originalName.trim().isEmpty() ? "Custom file" : originalName.trim();
        if (safeName.length() > 160) safeName = safeName.substring(0, 160);
        preferences().edit().putString("name_" + slot.name(), safeName).apply();
    }

    void clear(Slot slot) throws IOException {
        File target = fileFor(slot);
        if (target.exists() && !target.delete()) throw new IOException("Could not remove the custom file.");
        preferences().edit().remove("name_" + slot.name()).apply();
    }

    private SharedPreferences preferences() {
        return storage.getSharedPreferences("selected_files", Context.MODE_PRIVATE);
    }
}
