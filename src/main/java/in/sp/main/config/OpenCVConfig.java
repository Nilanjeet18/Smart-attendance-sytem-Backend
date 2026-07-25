package in.sp.main.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenCV Configuration uses a pure Java implementation
 * so no native library is required
 * FaceDetectionUtil uses pure Java with AWT/ImageIO.
 */
@Slf4j
@Configuration
public class OpenCVConfig {

    @Bean(name = "cascadeFilePath")
    public String cascadeFilePath() {
        log.info("✅ Pure Java face detection mode — OpenCV native library not required");
        return "not-required";
    }

    @Bean(name = "openCVVersion")
    public String openCVVersion() {
        String javaVersion = System.getProperty("java.version");
        log.info("☕ Running on Java {} — Pure Java face detection active", javaVersion);
        return "Pure-Java-" + javaVersion;
    }
}
