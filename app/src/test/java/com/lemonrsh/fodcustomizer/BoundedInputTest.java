package com.lemonrsh.fodcustomizer;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;

public class BoundedInputTest {
    @Test public void acceptsExactlyTheLimit() throws Exception {
        assertArrayEquals(new byte[]{1, 2, 3},
                BoundedInput.readFully(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3));
        assertArrayEquals(new byte[0], BoundedInput.readFully(new ByteArrayInputStream(new byte[0]), 0));
    }

    @Test public void rejectsFilesOverTheLimit() throws Exception {
        try {
            BoundedInput.readFully(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}), 3);
            fail("expected an IOException");
        } catch (IOException expected) { }
    }

    @Test public void handlesStreamsThatTemporarilyReturnZero() throws Exception {
        InputStream zeroFirst = new InputStream() {
            int position;
            boolean gaveZero;
            @Override public int read(byte[] bytes, int off, int len) {
                if (!gaveZero) { gaveZero = true; return 0; }
                if (position >= 2) return -1;
                bytes[off] = (byte) (++position);
                return 1;
            }
            @Override public int read() { return position < 2 ? ++position : -1; }
        };
        assertArrayEquals(new byte[]{1, 2}, BoundedInput.readFully(zeroFirst, 2));
    }
}
