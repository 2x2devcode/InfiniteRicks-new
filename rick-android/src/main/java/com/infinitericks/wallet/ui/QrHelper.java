package com.infinitericks.wallet.ui;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public final class QrHelper {
    private QrHelper() {
    }

    public static Bitmap createQr(String content, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (Exception e) {
            throw new IllegalStateException("failed to create QR", e);
        }
    }

    public static String paymentUri(String address) {
        return "infinitericks:" + address;
    }

    public static String parsePayment(String scanned) {
        if (scanned == null) {
            return "";
        }
        String value = scanned.trim();
        if (value.startsWith("infinitericks:")) {
            return value.substring("infinitericks:".length()).split("\\?")[0];
        }
        if (value.startsWith("bitcoin:")) {
            return value.substring("bitcoin:".length()).split("\\?")[0];
        }
        return value;
    }
}
