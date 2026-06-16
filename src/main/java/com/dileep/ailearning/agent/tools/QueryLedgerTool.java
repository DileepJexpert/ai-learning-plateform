package com.dileep.ailearning.agent.tools;

import com.dileep.ailearning.agent.Tool;
import com.dileep.ailearning.agent.ToolExecutionException;
import com.dileep.ailearning.agent.tools.ErpDataStore.LedgerEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool: return the entries and net balance for a ledger account (Module 5).
 */
@Component
public class QueryLedgerTool implements Tool {

    private final ErpDataStore erp;
    private final ObjectMapper objectMapper;

    public QueryLedgerTool(ErpDataStore erp, ObjectMapper objectMapper) {
        this.erp = erp;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "query_ledger";
    }

    @Override
    public String description() {
        return "Return the recent ledger entries and the net balance (credits minus debits) "
                + "for an account code (e.g. \"4000-SALES\").";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "account", Map.of(
                                "type", "string",
                                "description", "The ledger account code, e.g. 4000-SALES")),
                "required", List.of("account"));
    }

    @Override
    public List<String> requiredParameters() {
        return List.of("account");
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String account = String.valueOf(arguments.get("account")).trim();
        List<LedgerEntry> entries = erp.findLedger(account)
                .orElseThrow(() -> new ToolExecutionException(
                        "No ledger for account '" + account + "'. Known accounts: " + erp.accountCodes()));

        BigDecimal credits = entries.stream().map(LedgerEntry::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal debits = entries.stream().map(LedgerEntry::debit).reduce(BigDecimal.ZERO, BigDecimal::add);

        // LinkedHashMap to keep a readable, stable key order in the JSON result.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("account", account);
        result.put("entries", entries);
        result.put("totalCredits", credits);
        result.put("totalDebits", debits);
        result.put("netBalance", credits.subtract(debits));
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new ToolExecutionException("Failed to serialize ledger: " + e.getMessage());
        }
    }
}
