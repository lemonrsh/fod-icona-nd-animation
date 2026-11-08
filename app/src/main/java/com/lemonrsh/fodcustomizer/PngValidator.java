package com.lemonrsh.fodcustomizer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

final class PngValidator {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    private static final int MAX_SIDE = 2048;

    private PngValidator() {}

    static void validate(byte[] data) throws LottieValidator.ValidationException {
        if (data == null || data.length < SIGNATURE.length || data.length > BoundedInput.MAX_BYTES) {
            throw new LottieValidator.ValidationException("Choose a PNG file no larger than 4 MiB.");
        }
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (data[i] != SIGNATURE[i]) throw new LottieValidator.ValidationException("The selected file is not a PNG.");
        }
        BitmapFactory.Options info = new BitmapFactory.Options();
        info.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, info);
        if (info.outWidth < 1 || info.outHeight < 1 || info.outWidth > MAX_SIDE || info.outHeight > MAX_SIDE) {
            throw new LottieValidator.ValidationException("PNG dimensions must be between 1 and 2048 pixels per side.");
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bitmap == null) throw new LottieValidator.ValidationException("The PNG could not be decoded.");
        bitmap.recycle();
    }
}
