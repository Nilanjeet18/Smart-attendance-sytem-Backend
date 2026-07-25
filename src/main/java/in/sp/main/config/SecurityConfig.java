package in.sp.main.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import in.sp.main.model.Teacher;
import in.sp.main.repository.TeacherRepository;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TeacherRepository teacherRepository;
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(
                        "/api/auth/**",
                        "/api/attendance/qr/scan",
                        "/api/face/scan",
                        "/api/face/detect",
                        "/api/attendance/sessions/date/**",
                        "/api/attendance/sessions/active",
                        "/actuator/health"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/teachers/**")
                .hasAnyRole("ADMIN", "TEACHER")
                .requestMatchers("/api/teachers/**")
                .hasRole("ADMIN")
                .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                "https://*.vercel.app"
        ));

        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"
        ));

        configuration.setExposedHeaders(List.of("Authorization"));

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return email -> {
            Teacher teacher = teacherRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("Teacher not found: " + email));
            return org.springframework.security.core.userdetails.User
                    .withUsername(teacher.getEmail())
                    .password(teacher.getPassword())
                    .authorities(new SimpleGrantedAuthority("ROLE_" + teacher.getRole().name()))
                    .build();
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // ── JWT Filter ────────────────────────────────────────────────────────
    @Slf4j
    @Component
    @RequiredArgsConstructor
    public static class JwtAuthFilter extends OncePerRequestFilter {

        private final JwtUtil jwtUtil;
        private final TeacherRepository teacherRepository;

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {

            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                chain.doFilter(request, response);
                return;
            }

            try {
                String token = header.substring(7);
                String email = jwtUtil.extractEmail(token);

                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    Teacher teacher = teacherRepository.findByEmail(email).orElse(null);
                    if (teacher != null && jwtUtil.isTokenValid(token, email)) {
                        var authToken = new UsernamePasswordAuthenticationToken(
                                email, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + teacher.getRole().name()))
                        );
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (Exception e) {
                log.warn("JWT filter error: {}", e.getMessage());
            }
            chain.doFilter(request, response);
        }
    }

    // ── JWT Utility ───────────────────────────────────────────────────────
    @Component
    public static class JwtUtil {

        @Value("${jwt.secret}")
        private String secretKey;

        @Value("${jwt.expiration:86400000}")
        private long expirationMs;

        private SecretKey getKey() {
            return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        }

        public String generateToken(String email, String role) {
            return Jwts.builder()
                    .setSubject(email)
                    .claim("role", role)
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                    .signWith(getKey(), SignatureAlgorithm.HS256)
                    .compact();
        }

        public String extractEmail(String token) {
            return extractClaims(token).getSubject();
        }

        public boolean isTokenValid(String token, String email) {
            try {
                Claims claims = extractClaims(token);
                return claims.getSubject().equals(email)
                        && claims.getExpiration().after(new Date());
            } catch (Exception e) {
                return false;
            }
        }

        private Claims extractClaims(String token) {
            return Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        }
    }
}
