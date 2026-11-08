package com.lemonrsh.fodcustomizer;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class LottieValidatorTest {
    // Synthetic fixture for validation tests, not the user's animation.
    private static final String VALID = "{\"v\":\"5.10.2\",\"fr\":60,\"ip\":0,\"op\":301,"
            + "\"w\":150,\"h\":150,\"nm\":\"test\",\"assets\":[],\"layers\":["
            + "{\"ty\":3,\"nm\":\"Null\"},{\"ty\":4,\"shapes\":[{\"ty\":\"fl\",\"c\":{\"k\":[0.4,0.6,1,1]}}]}],"
            + "\"markers\":[]}";

    @Test public void readsRealJsonAndDerivesItsMetadata() throws Exception {
        LottieValidator.Info info = LottieValidator.validate(bytes(VALID));
        assertEquals("5.10.2", info.version);
        assertEquals(60.0, info.fps, 0.001);
        assertEquals(301.0 / 60.0, info.durationSeconds(), 0.00001);
        assertEquals(150, info.width);
        assertEquals(150, info.height);
        assertEquals(2, info.layers);
    }

    @Test public void emptyLayersAreAllowedButReportedAsBlank() throws Exception {
        String empty = "{\"v\":\"5.10\",\"fr\":30,\"ip\":0,\"op\":60,\"w\":50,\"h\":50,\"layers\":[]}";
        assertEquals(0, LottieValidator.validate(bytes(empty)).layers);
    }

    @Test public void rejectsInspectorSnippetTruncatedOrMalformedJson() {
        assertInvalid("v\"5.10.2\"fr60ip0op301w150h150layers0");
        assertInvalid(VALID.substring(0, VALID.length() - 1));
        assertInvalid(VALID.replace("[0.4,0.6,1,1]", "[0.4, …]"));
        assertInvalid(VALID + " trailing");
        assertInvalid(VALID + " {}");
        assertInvalid(VALID.replace("\"markers\":[]", "\"markers\":[],"));
        assertInvalid("[" + VALID + "]");
        assertInvalid(VALID.replace("\"fr\":60", "\"fr\":NaN"));
        assertInvalid(VALID.replace("\"fr\":60", "\"fr\":60, // not JSON\n"));
    }

    @Test public void rejectsIncompleteOrInvalidLottieMetadata() {
        assertInvalid(VALID.replace("\"fr\":60,", ""));
        assertInvalid(VALID.replace("\"fr\":60", "\"fr\":\"60\""));
        assertInvalid(VALID.replace("\"fr\":60", "\"fr\":0"));
        assertInvalid(VALID.replace("\"op\":301", "\"op\":0"));
        assertInvalid(VALID.replace("\"w\":150", "\"w\":0"));
        assertInvalid(VALID.replace("\"h\":150", "\"h\":1.5"));
        assertInvalid(VALID.replace("\"w\":150", "\"w\":8193"));
        assertInvalid(VALID.replace("\"layers\":[", "\"layers\":[1,"));
        assertInvalid(VALID.replace("\"fr\":60", "\"fr\":60,\"fr\":30"));
    }

    @Test public void rejectsInvalidUtf8AndOversizedInput() {
        try {
            LottieValidator.validate(new byte[]{'{', '"', 'v', '"', ':', '"', (byte) 0xc3, 0x28, '"', '}'});
            fail("Malformed UTF-8 must be rejected");
        } catch (LottieValidator.ValidationException expected) { }
        try {
            LottieValidator.validate(new byte[BoundedInput.MAX_BYTES + 1]);
            fail("Oversized files must be rejected");
        } catch (LottieValidator.ValidationException expected) { }
    }

    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static void assertInvalid(String text) {
        try {
            LottieValidator.validate(bytes(text));
            fail("Expected invalid input to be rejected: " + text);
        } catch (LottieValidator.ValidationException expected) { }
    }
}
