package com.lemonrsh.fodcustomizer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class BoundedInput {
    static final int MAX_BYTES = 4 * 1024 * 1024;

    private BoundedInput() {}

    /** Reads at most maxBytes; the caller owns and closes the input stream. */
    static byte[] readFully(InputStream input, int maxBytes) throws IOException {
        if (input == null) throw new IOException("Unable to open the selected file.");
        if (maxBytes < 0) throw new IllegalArgumentException("negative byte limit");
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        while (true) {
            int n = input.read(buffer);
            if (n == -1) break;
            // Some providers return zero temporarily. Read one byte to make progress.
            if (n == 0) {
                int value = input.read();
                if (value == -1) break;
                if (total == maxBytes) throw new IOException("File is larger than 4 MiB.");
                out.write(value);
                total++;
            } else {
                if (n > maxBytes - total) throw new IOException("File is larger than 4 MiB.");
                out.write(buffer, 0, n);
                total += n;
            }
        }
        return out.toByteArray();
    }
}
