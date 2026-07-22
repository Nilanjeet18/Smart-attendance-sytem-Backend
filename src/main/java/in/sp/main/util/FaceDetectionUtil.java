package in.sp.main.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Pure Java face detection — OpenCV native library chi garaj nahi!
 * Skin color detection + region analysis vaaparoon face detect karto.
 * Production madhe AWS Rekognition / Google Vision API vaapara.
 */
@Slf4j
@Component
public class FaceDetectionUtil {

    @Value("${attendance.face.confidence-threshold:0.6}")
    private double confidenceThreshold;

    // ── Face Detection ────────────────────────────────────────────────────

    public List<FaceRegion> detectFaces(String imageBase64) {
        try {
            BufferedImage image = base64ToImage(imageBase64);
            List<FaceRegion> faces = detectSkinRegions(image);
            log.info("Face detection complete — found {} face(s)", faces.size());
            return faces;
        } catch (Exception e) {
            log.error("Face detection error: {}", e.getMessage());
            throw new RuntimeException("Face detection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Face encoding kadhto — image cha color histogram feature vector
     */
    public String extractFaceEncoding(String imageBase64) {
        try {
            BufferedImage image = base64ToImage(imageBase64);

            // Image resize kara — standard 64x64
            BufferedImage resized = resizeImage(image, 64, 64);

            // Grayscale convert kara
            BufferedImage gray = toGrayscale(resized);

            // Histogram feature vector kadhto
            float[] histogram = computeHistogram(gray);

            // Base64 string madhe convert kara
            return histogramToBase64(histogram);

        } catch (Exception e) {
            log.error("Face encoding extraction failed: {}", e.getMessage());
            throw new RuntimeException("Face encoding failed: " + e.getMessage(), e);
        }
    }

    /**
     * Don encoding compare karto — cosine similarity
     */
    public double compareFaceEncodings(String encoding1, String encoding2) {
        try {
            float[] vec1 = base64ToHistogram(encoding1);
            float[] vec2 = base64ToHistogram(encoding2);

            if (vec1.length != vec2.length) return 0.0;

            double dot = 0, norm1 = 0, norm2 = 0;
            for (int i = 0; i < vec1.length; i++) {
                dot   += vec1[i] * vec2[i];
                norm1 += vec1[i] * vec1[i];
                norm2 += vec2[i] * vec2[i];
            }

            if (norm1 == 0 || norm2 == 0) return 0.0;
            double score = dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
            log.debug("Face similarity score: {}", score);
            return score;

        } catch (Exception e) {
            log.error("Face comparison failed: {}", e.getMessage());
            return 0.0;
        }
    }

    public boolean isSamePerson(String encoding1, String encoding2) {
        double score = compareFaceEncodings(encoding1, encoding2);
        return score >= confidenceThreshold;
    }

    // ── Detect only (no marking) ──────────────────────────────────────────

    public List<FaceRegion> detectFacesOnly(String imageBase64) {
        return detectFaces(imageBase64);
    }

    // ── Private Helpers ───────────────────────────────────────────────────

    /**
     * Skin color pixels shodhtoo aani rectangular regions return karto
     */
    private List<FaceRegion> detectSkinRegions(BufferedImage image) {
        int width  = image.getWidth();
        int height = image.getHeight();

        boolean[][] skinMask = new boolean[height][width];
        int skinPixelCount   = 0;

        // Har pixel check karo — skin color range madhe aahe ka?
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = new Color(image.getRGB(x, y));
                if (isSkinColor(c)) {
                    skinMask[y][x] = true;
                    skinPixelCount++;
                }
            }
        }

        List<FaceRegion> regions = new ArrayList<>();
        double skinRatio = (double) skinPixelCount / (width * height);

        log.debug("Skin pixel ratio: {}", skinRatio);

        // Skin pixels 5% - 70% asel tar face detected mhana
        // (< 5% = no face, > 70% = full body/too close)
        if (skinRatio >= 0.05 && skinRatio <= 0.70) {
            // Face bounding box estimate karo
            int[] bounds = findSkinBounds(skinMask, width, height);
            if (bounds != null) {
                int fx = bounds[0];
                int fy = bounds[1];
                int fw = bounds[2] - bounds[0];
                int fh = bounds[3] - bounds[1];

                // Minimum face size check
                if (fw > 30 && fh > 30) {
                    regions.add(new FaceRegion(fx, fy, fw, fh));
                    log.debug("Face region detected: x={}, y={}, w={}, h={}", fx, fy, fw, fh);
                }
            }
        }

        return regions;
    }

    /**
     * RGB value skin color range madhe aahe ka te tharavato
     */
    private boolean isSkinColor(Color c) {
        int r = c.getRed();
        int g = c.getGreen();
        int b = c.getBlue();

        // Skin color detection rules (RGB space):
        // Various skin tones cover karto — fair to dark skin
        return (r > 60 && g > 40 && b > 20)   // minimum brightness
            && (r > g && r > b)                 // red dominant
            && (r - Math.min(g, b) > 15)        // red-green difference
            && (Math.abs(r - g) > 10 || r > 150) // not too gray
            && (r < 250 && g < 240 && b < 230); // not pure white
    }

    /**
     * Skin pixels cha bounding box shodhtoo
     */
    private int[] findSkinBounds(boolean[][] mask, int width, int height) {
        int minX = width, minY = height, maxX = 0, maxY = 0;
        boolean found = false;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[y][x]) {
                    minX  = Math.min(minX, x);
                    minY  = Math.min(minY, y);
                    maxX  = Math.max(maxX, x);
                    maxY  = Math.max(maxY, y);
                    found = true;
                }
            }
        }

        return found ? new int[]{minX, minY, maxX, maxY} : null;
    }

    private BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    private BufferedImage toGrayscale(BufferedImage color) {
        BufferedImage gray = new BufferedImage(
            color.getWidth(), color.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(color, 0, 0, null);
        g.dispose();
        return gray;
    }

    private float[] computeHistogram(BufferedImage grayImage) {
        int bins   = 256;
        float[] hist = new float[bins];
        int total  = grayImage.getWidth() * grayImage.getHeight();

        for (int y = 0; y < grayImage.getHeight(); y++) {
            for (int x = 0; x < grayImage.getWidth(); x++) {
                int pixel = grayImage.getRGB(x, y) & 0xFF;
                hist[pixel]++;
            }
        }

        // Normalize
        for (int i = 0; i < bins; i++) {
            hist[i] /= total;
        }
        return hist;
    }

    private BufferedImage base64ToImage(String imageBase64) throws Exception {
        String base64Data = imageBase64.contains(",")
            ? imageBase64.split(",")[1]
            : imageBase64;
        byte[] bytes = Base64.getDecoder().decode(base64Data);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) {
            throw new RuntimeException("Invalid image data — could not decode");
        }
        return img;
    }

    private String histogramToBase64(float[] histogram) {
        byte[] bytes = new byte[histogram.length * 4];
        for (int i = 0; i < histogram.length; i++) {
            int bits = Float.floatToIntBits(histogram[i]);
            bytes[i * 4]     = (byte) (bits >> 24);
            bytes[i * 4 + 1] = (byte) (bits >> 16);
            bytes[i * 4 + 2] = (byte) (bits >> 8);
            bytes[i * 4 + 3] = (byte)  bits;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private float[] base64ToHistogram(String base64) {
        byte[] bytes  = Base64.getDecoder().decode(base64);
        float[] result = new float[bytes.length / 4];
        for (int i = 0; i < result.length; i++) {
            int bits = ((bytes[i * 4]     & 0xFF) << 24)
                     | ((bytes[i * 4 + 1] & 0xFF) << 16)
                     | ((bytes[i * 4 + 2] & 0xFF) << 8)
                     |  (bytes[i * 4 + 3] & 0xFF);
            result[i] = Float.intBitsToFloat(bits);
        }
        return result;
    }

    /** Simple DTO for a detected face bounding box */
    public record FaceRegion(int x, int y, int width, int height) {}
}
