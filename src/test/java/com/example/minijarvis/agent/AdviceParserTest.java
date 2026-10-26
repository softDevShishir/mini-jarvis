package com.example.minijarvis.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.minijarvis.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;

class AdviceParserTest {

    private final AdviceParser parser = new AdviceParser(new ObjectMapper());

    @Test
    void parsesValidAdvice() {
        AdvicePayload payload = parser.parse(TestFixtures.VALID_ADVICE);

        assertThat(payload.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(payload.summary()).startsWith("Cash is tight");
        assertThat(payload.recommendations()).hasSize(2);
        assertThat(payload.recommendations().get(0).suggestedAction()).isEqualTo("Call accounts payable");
    }

    @Test
    void stripsJsonCodeFence() {
        AdvicePayload payload = parser.parse("```json\n" + TestFixtures.VALID_ADVICE + "\n```");
        assertThat(payload.recommendations()).hasSize(2);
    }

    @Test
    void stripsBareCodeFenceWithSurroundingWhitespace() {
        AdvicePayload payload = parser.parse("  \n```\n" + TestFixtures.VALID_ADVICE + "\n```  \n");
        assertThat(payload.riskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void acceptsLowercaseRiskLevel() {
        String json = TestFixtures.VALID_ADVICE.replace("\"HIGH\"", "\"medium\"");
        assertThat(parser.parse(json).riskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "Sure! Here is my analysis: cash is low.",
            "{ \"summary\": \"x\", ",
            "[1,2,3]",
            "{\"risk_level\":\"LOW\",\"recommendations\":[]}",
            "{\"summary\":\"x\",\"risk_level\":\"CATASTROPHIC\",\"recommendations\":[]}",
            "{\"summary\":\"x\",\"risk_level\":\"LOW\",\"recommendations\":\"none\"}",
            "{\"summary\":\"x\",\"risk_level\":\"LOW\",\"recommendations\":[{\"title\":\"t\",\"reason\":\"r\"}]}",
            "{\"summary\":\"   \",\"risk_level\":\"LOW\",\"recommendations\":[]}",
            "{\"summary\":42,\"risk_level\":\"LOW\",\"recommendations\":[]}"
    })
    void rejectsInvalidOutput(String raw) {
        assertThatThrownBy(() -> parser.parse(raw)).isInstanceOf(InvalidAdviceException.class);
    }

    @Test
    void rejectsTooManyRecommendations() {
        StringBuilder recs = new StringBuilder();
        for (int i = 0; i <= AdviceParser.MAX_RECOMMENDATIONS; i++) {
            recs.append(i == 0 ? "" : ",").append("{\"title\":\"t\",\"reason\":\"r\",\"suggested_action\":\"a\"}");
        }
        String json = "{\"summary\":\"x\",\"risk_level\":\"LOW\",\"recommendations\":[" + recs + "]}";
        assertThatThrownBy(() -> parser.parse(json)).hasMessageContaining("Too many");
    }
}
