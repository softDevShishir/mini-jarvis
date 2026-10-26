package com.example.minijarvis.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MoneyFigureCheckerTest {

    private final Set<BigDecimal> known = Set.of(new BigDecimal("8940.00"), new BigDecimal("40056.20"),
            new BigDecimal("-34996.20"));

    @Test
    void acceptsFiguresFromTheData() {
        assertThat(MoneyFigureChecker.findUnsupported(
                "Coastal owes $8,940.00; expenses were A$40,056 and net was -$34,996.20 (about $40k out).", known, "AUD"))
                .isEmpty();
    }

    @Test
    void flagsInventedFigures() {
        assertThat(MoneyFigureChecker.findUnsupported("Revenue was $125,000.00 and AUD 777 is missing.", known, "AUD"))
                .containsExactly("$125,000.00", "AUD 777");
    }

    @Test
    void ignoresNumbersThatAreNotMoney() {
        assertThat(MoneyFigureChecker.findUnsupported("8 invoices are 30 days overdue.", known, "AUD")).isEmpty();
    }

    @Test
    void usesTheConfiguredCurrency() {
        assertThat(MoneyFigureChecker.findUnsupported("EUR 8,940.00 owed, €500 invented, AUD 999 ignored.", known, "EUR"))
                .containsExactly("€500");
    }
}
