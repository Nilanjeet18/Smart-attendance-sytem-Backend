package in.sp.main.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "qr_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QRToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", unique = true, nullable = false)
    private AttendanceSession session;

    @Column(name = "token", unique = true, nullable = false, length = 512)
    private String token;

    // QR image stored as Base64 string
    @Column(name = "qr_image_base64", columnDefinition = "LONGTEXT")
    private String qrImageBase64;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_used")
    @Builder.Default
    private Boolean isUsed = false;

    @Column(name = "scan_count")
    @Builder.Default
    private Integer scanCount = 0;

    @Column(name = "max_scans")
    private Integer maxScans; // null = unlimited

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !isExpired() && !isUsed;
    }

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) {
            generatedAt = LocalDateTime.now();
        }
    }
}