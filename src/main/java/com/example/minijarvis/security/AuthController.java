package com.example.minijarvis.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.minijarvis.config.JarvisProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** The only token-issuing endpoint. It accepts just the single admin account from ADMIN_USERNAME/ADMIN_PASSWORD. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;
    private final JarvisProperties.Security security;

    public AuthController(JwtService jwtService, JarvisProperties properties) {
        this.jwtService = jwtService;
        this.security = properties.security();
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        if (!credentialsMatch(request.username(), request.password())) {
            throw new BadCredentialsException();
        }
        return new TokenResponse(jwtService.issue(security.adminUsername()), "Bearer", jwtService.ttlSeconds());
    }

    private boolean credentialsMatch(String username, String password) {
        String expectedPassword = security.adminPassword();
        if (expectedPassword == null || expectedPassword.isBlank()) {
            return false; // login disabled until ADMIN_PASSWORD is set
        }
        // Evaluate both comparisons so timing does not reveal which part was wrong.
        boolean userOk = constantTimeEquals(username, security.adminUsername());
        boolean passOk = constantTimeEquals(password, expectedPassword);
        return userOk & passOk;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
    }

    public static class BadCredentialsException extends RuntimeException {
        public BadCredentialsException() {
            super("Invalid username or password");
        }
    }
}
