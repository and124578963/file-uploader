package org.my_ration.file_uploader.service;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.filters.ImageFilter;
import net.coobird.thumbnailator.util.exif.ExifFilterUtils;
import net.coobird.thumbnailator.util.exif.ExifUtils;
import net.coobird.thumbnailator.util.exif.Orientation;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Приводит загруженную картинку к лимитам хранилища.
 *
 * Формат определяется по содержимому, а не по имени файла: расширение из имени
 * решает, с каким Content-Type nginx отдаст файл, и доверять его клиенту нельзя.
 *
 * Размеры читаются из заголовка до декодирования. Память декодера зависит от
 * числа пикселей, а не от веса файла: фото на 48 МП весом 5 МБ занимает в куче
 * ~150 МБ, а однотонный PNG 20000×20000 — 1,6 ГБ при весе в десятки КБ.
 * Поэтому слишком большие отклоняются сразу, а остальные декодируются
 * с прореживанием — сразу примерно в целевой размер.
 */
public class ImageProcessor {

    private static final Map<String, String> EXTENSION_BY_FORMAT = Map.of(
            "jpeg", "jpg",
            "png", "png",
            "gif", "gif",
            "webp", "webp");

    private final int targetWidth;
    private final int targetHeight;
    private final long maxFileSizeBytes;
    private final long maxPixels;

    public ImageProcessor(int targetWidth, int targetHeight, long maxFileSizeBytes, long maxPixels) {
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxPixels = maxPixels;
    }

    public ProcessedImage process(byte[] data) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            ImageReader reader = readerFor(input);
            try {
                reader.setInput(input, true, false);
                String extension = EXTENSION_BY_FORMAT.get(reader.getFormatName().toLowerCase(Locale.ROOT));
                if (extension == null) {
                    throw new IllegalArgumentException("invalid_image");
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > maxPixels) {
                    throw new IllegalArgumentException("image_too_large");
                }

                if (width <= targetWidth && height <= targetHeight && data.length <= maxFileSizeBytes) {
                    return new ProcessedImage(data, extension);
                }

                Orientation orientation = "jpg".equals(extension) ? exifOrientation(reader) : null;
                BufferedImage decoded = reader.read(0, subsampling(reader, width, height));
                return new ProcessedImage(compressToJpeg(onWhite(decoded), orientation), "jpg");
            } finally {
                reader.dispose();
            }
        }
    }

    private static ImageReader readerFor(ImageInputStream input) {
        if (input == null) {
            throw new IllegalArgumentException("invalid_image");
        }
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            throw new IllegalArgumentException("invalid_image");
        }
        return readers.next();
    }

    /** Битые или нестандартные метаданные — не повод отказывать: картинка просто останется без поворота. */
    private static Orientation exifOrientation(ImageReader reader) {
        try {
            return ExifUtils.getExifOrientation(reader, 0);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Прореживание, при котором картинка ещё не меньше целевого размера: дальше её уменьшит Thumbnailator. */
    private ImageReadParam subsampling(ImageReader reader, int width, int height) {
        ImageReadParam param = reader.getDefaultReadParam();
        int step = Math.max(1, Math.min(width / targetWidth, height / targetHeight));
        if (step > 1) {
            param.setSourceSubsampling(step, step, 0, 0);
        }
        return param;
    }

    /** У JPEG нет прозрачности: без подложки прозрачный фон станет чёрным. */
    private static BufferedImage onWhite(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private byte[] compressToJpeg(BufferedImage image, Orientation orientation) throws IOException {
        // Поворот по EXIF делаем до подбора размера: у повёрнутого кадра меняются стороны.
        BufferedImage oriented = image;
        ImageFilter rotation = orientation == null ? null : ExifFilterUtils.getFilterForOrientation(orientation);
        if (rotation != null) {
            oriented = rotation.apply(image);
        }

        int currentWidth = Math.min(oriented.getWidth(), targetWidth);
        int currentHeight = Math.min(oriented.getHeight(), targetHeight);
        BufferedImage resized = Thumbnails.of(oriented).size(currentWidth, currentHeight).asBufferedImage();

        double quality = 1.0;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (true) {
            output.reset();
            Thumbnails.of(resized)
                    .size(currentWidth, currentHeight)
                    .outputFormat("jpg")
                    .outputQuality(quality)
                    .toOutputStream(output);

            if (output.size() <= maxFileSizeBytes || quality <= 0.1) {
                break;
            }
            // Сначала снижаем качество, потом — размер.
            if (quality > 0.4) {
                quality -= 0.1;
            } else {
                currentWidth = (int) (currentWidth * 0.9);
                currentHeight = (int) (currentHeight * 0.9);
            }
        }

        if (output.size() > maxFileSizeBytes) {
            throw new IOException("Couldn't compress the file to the target size");
        }
        return output.toByteArray();
    }
}
