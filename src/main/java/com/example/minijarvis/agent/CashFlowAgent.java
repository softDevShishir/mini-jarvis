package com.example.minijarvis.agent;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.example.minijarvis.domain.Expense;
import com.example.minijarvis.domain.ExpenseRepository;
import com.example.minijarvis.domain.Invoice;
import com.example.minijarvis.domain.InvoiceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Looks at overdue invoices, the last 30 days of cash in vs out, and the biggest unpaid invoices. */
@Component
public class CashFlowAgent implements Agent {

    public static final String NAME = "cash-flow";

    static final String SYSTEM_PROMPT =
            "You are a cash-flow advisor for the director. Use ONLY the data provided. Never invent numbers. "
                    + "Return only valid JSON.";

    private static final String USER_PROMPT_TEMPLATE = """
            Here is the current cash-flow data for a small plumbing business (all amounts in AUD):

            %s

            Analyse it and respond with a JSON object of exactly this shape:
            {
              "summary": "2-4 sentences for the director",
              "risk_level": "LOW" | "MEDIUM" | "HIGH",
              "recommendations": [
                { "title": "short title", "reason": "why, citing figures from the data", "suggested_action": "what a human could do" }
              ]
            }
            Give between 1 and 5 recommendations. Write money as $1,234.56 using only figures present in the data.
            You are advisory only: suggest actions for a human, never claim to have done anything.
            Return only valid JSON.""";

    private final InvoiceRepository invoices;
    private final ExpenseRepository expenses;
    private final StructuredAdvisor advisor;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CashFlowAgent(InvoiceRepository invoices, ExpenseRepository expenses, StructuredAdvisor advisor,
            ObjectMapper objectMapper, Clock clock) {
        this.invoices = invoices;
        this.expenses = expenses;
        this.advisor = advisor;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    // Deliberately not @Transactional: never hold a DB transaction open across a slow AI call.
    @Override
    public AgentResult run(String providerOverride) {
        Snapshot snapshot = collect(LocalDate.now(clock));
        String userPrompt = USER_PROMPT_TEMPLATE.formatted(toJson(snapshot.data()));

        StructuredAdvisor.Response response = advisor.advise(providerOverride, SYSTEM_PROMPT, userPrompt);
        AdvicePayload payload = response.payload();

        List<String> notes = new ArrayList<>();
        checkFigures("summary", payload.summary(), snapshot.knownFigures(), notes);
        for (int i = 0; i < payload.recommendations().size(); i++) {
            AdviceItem item = payload.recommendations().get(i);
            checkFigures("recommendations[" + i + "].reason", item.reason(), snapshot.knownFigures(), notes);
            checkFigures("recommendations[" + i + "].suggested_action", item.suggestedAction(), snapshot.knownFigures(), notes);
        }

        return new AgentResult(NAME, payload.summary(), payload.riskLevel(), payload.recommendations(),
                response.routed().providerId(), response.routed().result().model(), response.routed().fallbackUsed(),
                !notes.isEmpty(), List.copyOf(notes), response.inputTokens(), response.outputTokens());
    }

    private static void checkFigures(String field, String text, Set<BigDecimal> known, List<String> notes) {
        List<String> unsupported = MoneyFigureChecker.findUnsupported(text, known);
        if (!unsupported.isEmpty()) {
            notes.add(field + " mentions figures not found in the source data: " + String.join(", ", unsupported));
        }
    }

    /** Package-private for tests: the exact data sent to the model, plus every money figure it contains. */
    Snapshot collect(LocalDate today) {
        Set<BigDecimal> known = new HashSet<>();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("as_of", today.toString());
        data.put("currency", "AUD");

        List<Invoice> overdue = invoices.findOverdue(today);
        List<Map<String, Object>> overdueRows = new ArrayList<>();
        Map<String, BigDecimal> overdueByCustomer = new TreeMap<>();
        BigDecimal overdueTotal = BigDecimal.ZERO;
        for (Invoice inv : overdue) {
            overdueRows.add(invoiceRow(inv, today));
            overdueTotal = overdueTotal.add(inv.getAmount());
            overdueByCustomer.merge(inv.getCustomer(), inv.getAmount(), BigDecimal::add);
            known.add(inv.getAmount());
        }
        known.addAll(overdueByCustomer.values());
        known.add(overdueTotal);
        Map<String, Object> overdueSection = new LinkedHashMap<>();
        overdueSection.put("count", overdue.size());
        overdueSection.put("total", overdueTotal);
        overdueSection.put("invoices", overdueRows);
        data.put("overdue_invoices", overdueSection);

        LocalDate from = today.minusDays(30);
        BigDecimal income = invoices.totalPaidBetween(from, today);
        List<Expense> recentExpenses = expenses.findBetween(from, today);
        Map<String, BigDecimal> byCategory = new TreeMap<>();
        BigDecimal expenseTotal = BigDecimal.ZERO;
        for (Expense e : recentExpenses) {
            expenseTotal = expenseTotal.add(e.getAmount());
            byCategory.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
            known.add(e.getAmount());
        }
        known.addAll(byCategory.values());
        BigDecimal net = income.subtract(expenseTotal);
        known.add(income);
        known.add(expenseTotal);
        known.add(net);
        Map<String, Object> last30 = new LinkedHashMap<>();
        last30.put("from", from.toString());
        last30.put("to", today.toString());
        last30.put("income_received", income);
        last30.put("expenses_paid", expenseTotal);
        last30.put("net_cash_flow", net);
        last30.put("expenses_by_category", byCategory);
        data.put("last_30_days", last30);

        List<Invoice> largest = invoices.findLargestUnpaid(PageRequest.of(0, 5));
        List<Map<String, Object>> largestRows = new ArrayList<>();
        BigDecimal largestTotal = BigDecimal.ZERO;
        for (Invoice inv : largest) {
            largestRows.add(invoiceRow(inv, today));
            largestTotal = largestTotal.add(inv.getAmount());
            known.add(inv.getAmount());
        }
        known.add(largestTotal);
        BigDecimal totalUnpaid = invoices.totalUnpaid();
        known.add(totalUnpaid);
        Map<String, Object> unpaid = new LinkedHashMap<>();
        unpaid.put("total_unpaid_all_invoices", totalUnpaid);
        unpaid.put("top_5_total", largestTotal);
        unpaid.put("top_5", largestRows);
        data.put("largest_unpaid_invoices", unpaid);

        return new Snapshot(data, Set.copyOf(known));
    }

    private static Map<String, Object> invoiceRow(Invoice inv, LocalDate today) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("invoice_id", inv.getId());
        row.put("customer", inv.getCustomer());
        row.put("amount", inv.getAmount());
        row.put("issue_date", inv.getIssueDate().toString());
        row.put("due_date", inv.getDueDate().toString());
        long daysOverdue = ChronoUnit.DAYS.between(inv.getDueDate(), today);
        row.put("days_overdue", Math.max(0, daysOverdue));
        return row;
    }

    private String toJson(Object data) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise agent data", e);
        }
    }

    record Snapshot(Map<String, Object> data, Set<BigDecimal> knownFigures) {
    }
}
