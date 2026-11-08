package com.lemonrsh.fodcustomizer;

import static org.junit.Assert.*;
import org.junit.Test;

public class CallerPolicyTest {
    @Test public void onlyTheModuleOrSystemUiUidMayRead() {
        assertTrue(CallerPolicy.mayRead(10123, 10123, null));
        assertTrue(CallerPolicy.mayRead(10124, 10123, new String[]{"com.android.systemui"}));
        assertTrue(CallerPolicy.mayRead(10124, 10123, new String[]{"shared.uid", "com.android.systemui"}));
        assertFalse(CallerPolicy.mayRead(10124, 10123, null));
        assertFalse(CallerPolicy.mayRead(10124, 10123, new String[0]));
        assertFalse(CallerPolicy.mayRead(10124, 10123, new String[]{"com.android.systemui.fake"}));
        assertFalse(CallerPolicy.mayRead(10124, 10123, new String[]{"other", null}));
    }
}
