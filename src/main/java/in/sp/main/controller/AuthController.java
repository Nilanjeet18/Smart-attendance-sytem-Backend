package in.sp.main.controller;

import in.sp.main.config.SecurityConfig.JwtUtil;
import in.sp.main.exception.AttendanceException;
import in.sp.main.model.Teacher;
import in.sp.main.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> creds) {

        String email = creds.get("email");
        String password = creds.get("password");

        Teacher teacher = teacherRepository.findByEmail(email)
                .orElseThrow(() ->
                        new AttendanceException("Invalid email or password", "BAD_CREDENTIALS"));

        if (!passwordEncoder.matches(password, teacher.getPassword())) {
            throw new AttendanceException("Invalid email or password", "BAD_CREDENTIALS");
        }

        String token = jwtUtil.generateToken(
                teacher.getEmail(),
                teacher.getRole().name());

        return ResponseEntity.ok(Map.of(
                "token", token,
                "teacherId", teacher.getId(),
                "name", teacher.getName(),
                "role", teacher.getRole().name(),
                "email", teacher.getEmail()
        ));
    }
}
