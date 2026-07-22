package in.sp.main.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentDTO {

    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Roll number is required")
    private String rollNumber;

    @Email(message = "Valid email is required")
    private String email;

    private String phoneNumber;
    private String department;
    private Integer semester;
    private Boolean isActive;
    private String profileImagePath;
    private Boolean hasFaceEncoding;

    // Used only for face registration — not returned in responses
    private String faceImageBase64;
}