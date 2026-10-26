package com.example.minijarvis.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort hallucination check: finds money figures written as "$1,234.56", "AUD 1234", "$12.5k" and checks each
 * is close to a figure that was actually in the data given to the model. Figures not found are returned.
 */
public final class MoneyFigureChecker {

    private static final Pattern MONEY = Pattern.compile(
            "(?:A?\\$|AUD\\s?)\\s?(\\d{1,3}(?:,\\d{3})+|\\d+)(\\.\\d{1,2})?\\s?([kK])?(?![\\d,])");

    private MoneyFigureChecker() {
    }

    public static List<String> findUnsupported(String text, Collection<BigDecimal> knownFigures) {
        List<String> unsupported = new ArrayList<>();
        if (text == null) {
            return unsupported;
        }
        Matcher m = MONEY.matcher(text);
        while (m.find()) {
            BigDecimal value = new BigDecimal(m.group(1).replace(",", "") + (m.group(2) != null ? m.group(2) : ""));
            boolean thousands = m.group(3) != null;
            if (thousands) {
                value = value.multiply(BigDecimal.valueOf(1000));
            }
            if (!matchesAny(value, thousands, knownFigures)) {
                unsupported.add(m.group().trim());
            }
        }
        return unsupported;
    }

    private static boolean matchesAny(BigDecimal value, boolean abbreviated, Collection<BigDecimal> known) {
        // "$12.5k" is a rounded figure, so allow 5%; otherwise allow $1 or 1% for rounding to whole dollars.
        for (BigDecimal k : known) {
            BigDecimal ref = k.abs();
            BigDecimal pct = ref.multiply(abbreviated ? new BigDecimal("0.05") : new BigDecimal("0.01"));
            BigDecimal tolerance = pct.max(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);
            if (value.subtract(ref).abs().compareTo(tolerance) <= 0) {
                return true;
            }
        }
        return false;
    }
}
