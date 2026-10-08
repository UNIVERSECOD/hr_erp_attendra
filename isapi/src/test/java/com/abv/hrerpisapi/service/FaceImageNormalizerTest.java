package com.abv.hrerpisapi.service;

import com.abv.hrerpisapi.exception.FaceImageValidationException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FaceImageNormalizerTest {

    private final FaceImageNormalizer normalizer = new FaceImageNormalizer();

    @Test
    void normalize_convertsPngToBoundedJpegAndPreservesAspectRatio() throws Exception {
        BufferedImage source = new BufferedImage(1200, 1600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(new Color(40, 90, 160, 180));
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.dispose();
        byte[] png = encode(source, "png");

        FaceImageNormalizer.NormalizedFaceImage result = normalizer.normalize(png);

        assertThat(result.bytes()).hasSizeLessThan(FaceImageNormalizer.MAX_OUTPUT_BYTES);
        assertThat(result.width()).isEqualTo(768);
        assertThat(result.height()).isEqualTo(1024);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getType()).isNotEqualTo(BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    void normalize_compressesDetailedLargeImageBelowDeviceLimit() {
        BufferedImage source = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(7L);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.setRGB(x, y, random.nextInt(0x1000000));
            }
        }

        FaceImageNormalizer.NormalizedFaceImage result = normalizer.normalize(encode(source, "jpg"));

        assertThat(result.bytes()).hasSizeLessThan(FaceImageNormalizer.MAX_OUTPUT_BYTES);
        assertThat(Math.max(result.width(), result.height())).isLessThanOrEqualTo(FaceImageNormalizer.MAX_DIMENSION);
    }

    @Test
    void normalize_rejectsNonImageBytes() {
        assertThatThrownBy(() -> normalizer.normalize(new byte[]{1, 2, 3, 4}))
                .isInstanceOf(FaceImageValidationException.class)
                .hasMessageContaining("Şəkil formatı dəstəklənmir");
    }

    private byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, format, output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
