package in.sp.main.util;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class QRCodeUtil {

    @Value("${attendance.qr.width:300}")
    private int qrWidth;

    @Value("${attendance.qr.height:300}")
    private int qrHeight;

    /**
     * Generates a QR code image as Base64 string from the given content.
     *
     * @param content the text/URL to encode in the QR
     * @return Base64-encoded PNG image string (without prefix)
     */
    public String generateQRCodeBase64(String content) {
        try {
            QRCodeWriter qrWriter = new QRCodeWriter();

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix bitMatrix = qrWriter.encode(content, BarcodeFormat.QR_CODE,
                qrWidth, qrHeight, hints);

            BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(qrImage, "PNG", baos);

            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            log.debug("QR Code generated successfully for content length: {}", content.length());
            return base64;

        } catch (Exception e) {
            log.error("Failed to generate QR code: {}", e.getMessage(), e);
            throw new RuntimeException("QR code generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a data URI string suitable for embedding directly in HTML img src.
     */
    public String generateQRCodeDataUri(String content) {
        return "data:image/png;base64," + generateQRCodeBase64(content);
    }

    /**
     * Decodes a QR code from a Base64-encoded image.
     *
     * @param imageBase64 Base64 string of the image (with or without data URI prefix)
     * @return decoded text content
     */
    public String decodeQRCode(String imageBase64) {
        try {
            // Strip data URI prefix if present
            String base64Data = imageBase64;
            if (imageBase64.contains(",")) {
                base64Data = imageBase64.split(",")[1];
            }

            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));

            LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            Map<DecodeHintType, Object> hints = new HashMap<>();
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

            Result result = new MultiFormatReader().decode(bitmap, hints);
            return result.getText();

        } catch (NotFoundException e) {
            log.warn("No QR code found in the provided image");
            throw new RuntimeException("No QR code found in image");
        } catch (Exception e) {
            log.error("Failed to decode QR code: {}", e.getMessage(), e);
            throw new RuntimeException("QR code decoding failed: " + e.getMessage(), e);
        }
    }
}