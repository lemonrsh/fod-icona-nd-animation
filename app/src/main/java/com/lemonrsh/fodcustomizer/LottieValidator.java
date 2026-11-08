package com.lemonrsh.fodcustomizer;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Strict JSON and basic Lottie metadata checks, not a renderer or full Lottie validator. */
final class LottieValidator {
    private LottieValidator() {}

    static Info validate(byte[] bytes) throws ValidationException {
        if (bytes == null || bytes.length == 0) throw new ValidationException("The file is empty.");
        if (bytes.length > BoundedInput.MAX_BYTES) throw new ValidationException("File is larger than 4 MiB.");

        String version = null;
        Double fps = null, in = null, out = null, width = null, height = null;
        Integer layers = null;
        Set<String> seen = new HashSet<>();

        try (JsonReader reader = new JsonReader(new InputStreamReader(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw new ValidationException("The file must be a complete Lottie JSON object, starting with {.");
            }
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case "v":
                        requireUnique(seen, name);
                        requireToken(reader, JsonToken.STRING, name);
                        version = reader.nextString();
                        break;
                    case "fr":
                        requireUnique(seen, name);
                        fps = readNumber(reader, name);
                        break;
                    case "ip":
                        requireUnique(seen, name);
                        in = readNumber(reader, name);
                        break;
                    case "op":
                        requireUnique(seen, name);
                        out = readNumber(reader, name);
                        break;
                    case "w":
                        requireUnique(seen, name);
                        width = readNumber(reader, name);
                        break;
                    case "h":
                        requireUnique(seen, name);
                        height = readNumber(reader, name);
                        break;
                    case "layers":
                        requireUnique(seen, name);
                        requireToken(reader, JsonToken.BEGIN_ARRAY, name);
                        reader.beginArray();
                        int count = 0;
                        while (reader.hasNext()) {
                            requireToken(reader, JsonToken.BEGIN_OBJECT, "each layer");
                            reader.skipValue();
                            count++;
                        }
                        reader.endArray();
                        layers = count;
                        break;
                    default:
                        // Consuming the whole value also checks JSON syntax inside nested objects.
                        reader.skipValue();
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new ValidationException("Unexpected text after the JSON object.");
            }
        } catch (ValidationException e) {
            throw e;
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw new ValidationException("Invalid or incomplete UTF-8 JSON. Choose the actual .json file, not a preview.", e);
        }

        if (version == null || version.trim().isEmpty() || fps == null || in == null || out == null
                || width == null || height == null || layers == null) {
            throw new ValidationException("Missing Lottie fields: v, fr, ip, op, w, h and layers are required.");
        }
        if (fps <= 0 || out <= in) {
            throw new ValidationException("Frame rate must be positive and op must be greater than ip.");
        }
        if (!isCanvasSize(width) || !isCanvasSize(height)) {
            throw new ValidationException("Canvas width and height must be whole numbers between 1 and 8192.");
        }
        return new Info(version, fps, in, out, width.intValue(), height.intValue(), layers);
    }

    private static double readNumber(JsonReader reader, String field) throws IOException, ValidationException {
        requireToken(reader, JsonToken.NUMBER, field);
        double value = reader.nextDouble();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new ValidationException("Lottie field " + field + " must be finite.");
        }
        return value;
    }

    private static void requireToken(JsonReader reader, JsonToken token, String field)
            throws IOException, ValidationException {
        if (reader.peek() != token) throw new ValidationException("Unexpected value for " + field + ".");
    }

    private static void requireUnique(Set<String> fields, String field) throws ValidationException {
        if (!fields.add(field)) throw new ValidationException("Duplicate Lottie field: " + field + ".");
    }

    private static boolean isCanvasSize(double value) {
        return value >= 1 && value <= 8192 && value == Math.rint(value);
    }

    static final class Info {
        final String version;
        final double fps, in, out;
        final int width, height, layers;

        Info(String version, double fps, double in, double out, int width, int height, int layers) {
            this.version = version;
            this.fps = fps;
            this.in = in;
            this.out = out;
            this.width = width;
            this.height = height;
            this.layers = layers;
        }

        double durationSeconds() { return (out - in) / fps; }
    }

    static final class ValidationException extends Exception {
        private static final long serialVersionUID = 1L;
        ValidationException(String message) { super(message); }
        ValidationException(String message, Throwable cause) { super(message, cause); }
    }
}
