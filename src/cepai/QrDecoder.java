package cepai;

import java.io.IOException;
import java.util.Hashtable;
import javax.microedition.lcdui.Image;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

public final class QrDecoder {
    public static String decode(Image image) throws IOException {
        int w = image.getWidth(), h = image.getHeight();
        if (w <= 0 || h <= 0 || w > 2048 || h > 2048 || w * h > 2000000)
            throw new IOException("QR image is too large. Use a smaller PNG/JPG.");
        byte[] luminance = new byte[w * h]; int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            image.getRGB(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                int rgb = row[x];
                luminance[y * w + x] = (byte) ((((rgb >> 16) & 255)
                    + 2 * ((rgb >> 8) & 255) + (rgb & 255)) / 4);
            }
        }
        Pixels pixels = new Pixels(w, h, luminance);
        Hashtable hints = new Hashtable(); hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        QRCodeReader reader = new QRCodeReader();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return reader.decode(new BinaryBitmap(attempt == 1
                    ? new GlobalHistogramBinarizer(pixels) : new HybridBinarizer(pixels)), hints).getText();
            } catch (ReaderException noCode) {
                if (attempt == 1) pixels = pixels.rotated();
            }
        }
        throw new IOException("QR not recognized. Enlarge the code, reduce glare and scan again.");
    }
    private static final class Pixels extends LuminanceSource {
        private final byte[] pixels;
        Pixels(int w, int h, byte[] data) { super(w, h); pixels = data; }
        public byte[] getRow(int y, byte[] row) {
            int w = getWidth(); if (row == null || row.length < w) row = new byte[w];
            System.arraycopy(pixels, y * w, row, 0, w); return row;
        }
        public byte[] getMatrix() { return pixels; }
        Pixels rotated() {
            int w = getWidth(), h = getHeight(); byte[] data = new byte[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) data[(w - x - 1) * h + y] = pixels[y * w + x];
            return new Pixels(h, w, data);
        }
    }
}
