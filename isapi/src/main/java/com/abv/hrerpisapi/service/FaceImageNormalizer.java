package com.abv.hrerpisapi.service;

import com.abv.hrerpisapi.exception.FaceImageValidationException;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

@Component
public class FaceImageNormalizer {

    static final int MAX_OUTPUT_BYTES = 190_000;
    static final int MAX_DIMENSION = 1024;
    private static final int MIN_LONG_EDGE = 640;
    private static final float[] JPEG_QUALITIES = {0.90f, 0.82f, 0.74f, 0.66f, 0.58f, 0.50f};

    public NormalizedFaceImage normalize(byte[] sourceBytes) {
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new FaceImageValidationException("Şəkil faylı boşdur.");
        }

        final BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(sourceBytes));
        } catch (IOException exception) {
            throw new FaceImageValidationException("Şəkil faylı oxunmadı. JPG və ya PNG faylı seçin.", exception);
        }
        if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0) {
            throw new FaceImageValidationException("Şəkil formatı dəstəklənmir. JPG və ya PNG faylı seçin.");
        }

        BufferedImage current = resizeToLongEdge(decoded, MAX_DIMENSION);
        while (true) {
            for (float quality : JPEG_QUALITIES) {
                byte[] jpeg = encodeJpeg(current, quality);
                if (jpeg.length < MAX_OUTPUT_BYTES) {
                    return new NormalizedFaceImage(jpeg, current.getWidth(), current.getHeight());
                }
            }

            int currentLongEdge = Math.max(current.getWidth(), current.getHeight());
            if (currentLongEdge <= MIN_LONG_EDGE) {
                break;
            }
            int nextLongEdge = Math.max(MIN_LONG_EDGE, Math.round(currentLongEdge * 0.85f));
            if (nextLongEdge >= currentLongEdge) {
                break;
            }
            current = resizeToLongEdge(current, nextLongEdge);
        }

        throw new FaceImageValidationException(
                "Şəkil cihaz limitinə uyğunlaşdırıla bilmədi. Daha sadə və aydın şəkil seçin.");
    }

    private BufferedImage resizeToLongEdge(BufferedImage source, int maxLongEdge) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int sourceLongEdge = Math.max(sourceWidth, sourceHeight);
        double scale = sourceLongEdge > maxLongEdge ? (double) maxLongEdge / sourceLongEdge : 1.0d;
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));

        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new FaceImageValidationException("Server JPEG şəkli yarada bilmir.");
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), params);
            imageOutput.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new FaceImageValidationException("Şəkil cihaz formatına çevrilmədi.", exception);
        } finally {
            writer.dispose();
        }
    }

    public record NormalizedFaceImage(byte[] bytes, int width, int height) {
    }
}
