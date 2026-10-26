package com.example.minijarvis.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.minijarvis.ai.ClaudeProvider;
import com.example.minijarvis.ai.GeminiProvider;
import com.example.minijarvis.ai.OpenAiProvider;

/** CORS for a separately hosted UI (mini-jarvis-ui): only the configured exact origin is allowed. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "jarvis.security.cors-allowed-origins=https://jarvis.example.com/")
class CorsTest {

    private static final String UI = "https://jarvis.example.com";

    @Autowired MockMvc mvc;

    @MockitoBean ClaudeProvider claude;
    @MockitoBean GeminiProvider gemini;
    @MockitoBean OpenAiProvider openai;

    @Test
    void preflightFromConfiguredOriginIsAllowed() throws Exception {
        mvc.perform(options("/api/agents/cash-flow/run")
                        .header("Origin", UI)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", UI))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void otherOriginsAreRefused() throws Exception {
        mvc.perform(options("/api/providers")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void corsDoesNotBypassAuthentication() throws Exception {
        mvc.perform(get("/api/providers").header("Origin", UI))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wildcardsAndPathsAreRejectedAtStartup() {
        assertThatThrownBy(() -> SecurityConfig.corsSource(List.of("*")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CORS_ALLOWED_ORIGINS");
        assertThatThrownBy(() -> SecurityConfig.corsSource(List.of("https://*.example.com")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SecurityConfig.corsSource(List.of("https://example.com/app")))
                .isInstanceOf(IllegalStateException.class);
    }
}
