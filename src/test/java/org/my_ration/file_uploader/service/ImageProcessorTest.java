package org.my_ration.file_uploader.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    // Боевые настройки из .env.
    private static final int TARGET = 1024;
    private static final long MAX_BYTES = 524288;
    private static final long MAX_PIXELS = 100_000_000;

    private final ImageProcessor processor = new ImageProcessor(TARGET, TARGET, MAX_BYTES, MAX_PIXELS);

    @Test
    void transparentPngOverLimitIsFlattenedOntoWhite() throws IOException {
        byte[] png = encode(circleOnTransparent(1500), "png");

        ProcessedImage result = processor.process(png);

        assertThat(result.extension()).isEqualTo("jpg");
        BufferedImage out = decode(result.data());
        int corner = out.getRGB(2, 2);
        assertThat(red(corner)).isGreaterThan(240);
        assertThat(green(corner)).isGreaterThan(240);
        assertThat(blue(corner)).isGreaterThan(240);
    }

    @Test
    void smallPngIsKeptAsIsWithPngExtension() throws IOException {
        byte[] png = encode(circleOnTransparent(600), "png");

        ProcessedImage result = processor.process(png);

        assertThat(result.extension()).isEqualTo("png");
        assertThat(result.data()).isEqualTo(png);
    }

    @Test
    void smallJpegIsKeptAsIsWithJpgExtension() throws IOException {
        byte[] jpg = encode(photo(800, 600), "jpg");

        ProcessedImage result = processor.process(jpg);

        assertThat(result.extension()).isEqualTo("jpg");
        assertThat(result.data()).isEqualTo(jpg);
    }

    @Test
    void bigPhotoIsDownscaledIntoLimits() throws IOException {
        byte[] jpg = encode(photo(6000, 4500), "jpg");

        ProcessedImage result = processor.process(jpg);

        BufferedImage out = decode(result.data());
        assertThat(result.extension()).isEqualTo("jpg");
        assertThat(out.getWidth()).isEqualTo(1024);
        assertThat(out.getHeight()).isEqualTo(768);
        assertThat((long) result.data().length).isLessThanOrEqualTo(MAX_BYTES);
    }

    @Test
    void exifOrientationIsApplied() throws IOException {
        // Камера пишет кадр «лёжа» и ставит Orientation=6: показывать повёрнутым на 90°.
        byte[] jpg = withExifOrientation(encode(photo(2000, 1000), "jpg"), 6);

        ProcessedImage result = processor.process(jpg);

        BufferedImage out = decode(result.data());
        assertThat(out.getHeight()).isGreaterThan(out.getWidth());
    }

    @Test
    void hugePixelCountIsRejectedBeforeDecoding() throws IOException {
        // Однотонный PNG: на диске десятки КБ, в памяти — 1,6 ГБ.
        byte[] bomb = encode(new BufferedImage(20000, 20000, BufferedImage.TYPE_BYTE_BINARY), "png");

        assertThatThrownBy(() -> processor.process(bomb))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("image_too_large");
    }

    @Test
    void notAnImageIsRejected() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();

        assertThatThrownBy(() -> processor.process(html))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid_image");
    }

    // --- помощники ---

    private static BufferedImage circleOnTransparent(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(220, 120, 40));
        g.fillOval(size / 5, size / 5, size * 3 / 5, size * 3 / 5);
        g.dispose();
        return img;
    }

    /** Градиент с шумом, чтобы JPEG не сжимался до нуля. */
    private static BufferedImage photo(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(1);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int n = r.nextInt(12);
                int rr = (x * 255 / w + n) & 0xFF;
                int gg = (y * 255 / h + n) & 0xFF;
                int bb = ((x + y) * 128 / (w + h) + n) & 0xFF;
                img.setRGB(x, y, rr << 16 | gg << 8 | bb);
            }
        }
        return img;
    }

    private static byte[] encode(BufferedImage img, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(data));
    }

    /** Вставляет за JFIF APP0 сегмент APP1 с единственным тегом Orientation. */
    private static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        int app0End = 4 + ((jpeg[4] & 0xFF) << 8 | (jpeg[5] & 0xFF));
        byte[] tiff = {
                'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00,       // little-endian, IFD0 по смещению 8
                0x01, 0x00,                                         // одна запись
                0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00,     // 0x0112 Orientation, SHORT, 1 шт.
                (byte) orientation, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00                              // следующего IFD нет
        };
        byte[] header = {'E', 'x', 'i', 'f', 0, 0};
        int length = 2 + header.length + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, app0End);
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.write(header, 0, header.length);
        out.write(tiff, 0, tiff.length);
        out.write(jpeg, app0End, jpeg.length - app0End);
        return out.toByteArray();
    }

    private static int red(int rgb) { return (rgb >> 16) & 0xFF; }
    private static int green(int rgb) { return (rgb >> 8) & 0xFF; }
    private static int blue(int rgb) { return rgb & 0xFF; }
}
