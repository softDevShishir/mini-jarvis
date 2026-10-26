package com.example.minijarvis.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.minijarvis.config.JarvisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Uses the REAL provider beans configured with fake keys (no network calls are made here) and checks the keys,
 * JWT secret and admin password never appear in API responses or logs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class SecretLeakTest {

    private static final List<String> SECRETS = List.of(
            "sk-ant-TEST-SECRET-claude-key-do-not-leak",
            "TEST-SECRET-gemini-key-do-not-leak",
            "test-jwt-secret-that-is-definitely-longer-than-32-bytes",
            "test-password");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JarvisProperties properties;

    @Test
    void secretsNeverAppearInResponsesOrLogs(CapturedOutput output) throws Exception {
        List<String> bodies = new ArrayList<>();

        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"test-password\"}"))
                .andReturn().getResponse().getContentAsString();
        bodies.add(login);
        String auth = "Bearer " + objectMapper.readTree(login).get("accessToken").asText();

        bodies.add(mvc.perform(get("/api/providers").header("Authorization", auth)).andReturn().getResponse().getContentAsString());
        bodies.add(mvc.perform(get("/api/agents").header("Authorization", auth)).andReturn().getResponse().getContentAsString());
        bodies.add(mvc.perform(get("/api/recommendations").header("Authorization", auth)).andReturn().getResponse().getContentAsString());
        bodies.add(mvc.perform(get("/api/audit-log").header("Authorization", auth)).andReturn().getResponse().getContentAsString());
        bodies.add(mvc.perform(post("/api/agents/cash-flow/run").param("provider", "nope").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString());
        bodies.add(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}")).andReturn().getResponse().getContentAsString());
        bodies.add(mvc.perform(get("/api/providers")).andReturn().getResponse().getContentAsString());

        assertThat(bodies.get(1)).contains("claude").contains("gemini").doesNotContainIgnoringCase("apiKey");
        for (String body : bodies) {
            for (String secret : SECRETS) {
                assertThat(body).doesNotContain(secret);
            }
        }
        for (String secret : SECRETS) {
            assertThat(output.getAll()).doesNotContain(secret);
        }
    }

    @Test
    void configToStringMasksSecrets() {
        String rendered = properties.toString();
        for (String secret : SECRETS) {
            assertThat(rendered).doesNotContain(secret);
        }
    }
}
