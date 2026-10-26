package com.example.minijarvis.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.minijarvis.TestFixtures;
import com.example.minijarvis.config.JarvisProperties;

class JwtServiceTest {

    private static JarvisProperties withSecret(String secret) {
        JarvisProperties base = TestFixtures.properties("claude", "");
        return new JarvisProperties(base.timezone(), base.maxAgentRunsPerDay(), base.ai(),
                new JarvisProperties.Security("admin", "pw", secret, 60, List.of()), base.scheduler(), base.telegram(), base.business());
    }

    @Test
    void refusesMissingSecret() {
        assertThatThrownBy(() -> new JwtService(withSecret(""), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new JwtService(withSecret(null), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtService(withSecret("x".repeat(31)), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void issuesAndValidatesTokens() {
        JwtService service = new JwtService(withSecret("x".repeat(32)), Clock.systemUTC());
        String token = service.issue("admin");

        assertThat(service.validate(token)).contains("admin");
        assertThat(service.validate(token + "tampered")).isEmpty();
        assertThat(service.validate("not-a-jwt")).isEmpty();
    }

    @Test
    void rejectsTokensSignedWithAnotherSecret() {
        String foreign = new JwtService(withSecret("y".repeat(40)), Clock.systemUTC()).issue("admin");
        assertThat(new JwtService(withSecret("x".repeat(40)), Clock.systemUTC()).validate(foreign)).isEmpty();
    }
}
