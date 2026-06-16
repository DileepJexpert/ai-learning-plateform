package com.dileep.ailearning.agent.tools;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A tiny in-memory stand-in for ERP/ledger systems (Module 5), so the agent tools
 * have real data to return without any external dependency. In a real system these
 * lookups would hit Postgres / a downstream service.
 */
@Component
public class ErpDataStore {

    public record Customer(String id, String name, String tier,
                           BigDecimal creditLimit, BigDecimal outstanding) {
    }

    public record LedgerEntry(String date, String description, BigDecimal debit, BigDecimal credit) {
    }

    private final Map<String, Customer> customers = Map.of(
            "C-100", new Customer("C-100", "Bharat Motors Ltd", "GOLD",
                    new BigDecimal("500000.00"), new BigDecimal("142500.00")),
            "C-200", new Customer("C-200", "Northwind Retail Ltd", "SILVER",
                    new BigDecimal("200000.00"), new BigDecimal("198750.00")),
            "C-300", new Customer("C-300", "Sunrise Electricals Pvt Ltd", "BRONZE",
                    new BigDecimal("75000.00"), new BigDecimal("12000.00")));

    private final Map<String, List<LedgerEntry>> ledgers = Map.of(
            "4000-SALES", List.of(
                    new LedgerEntry("2025-05-02", "Invoice SE/2025/1187", BigDecimal.ZERO, new BigDecimal("30149.00")),
                    new LedgerEntry("2025-05-14", "Invoice SE/2025/1192", BigDecimal.ZERO, new BigDecimal("11800.00")),
                    new LedgerEntry("2025-05-20", "Credit note CN/2025/044", new BigDecimal("2360.00"), BigDecimal.ZERO)),
            "2000-PAYABLES", List.of(
                    new LedgerEntry("2025-05-05", "Vendor bill VB-9001", new BigDecimal("18500.00"), BigDecimal.ZERO),
                    new LedgerEntry("2025-05-18", "Payment to vendor", BigDecimal.ZERO, new BigDecimal("18500.00"))));

    public Optional<Customer> findCustomer(String id) {
        return Optional.ofNullable(customers.get(id));
    }

    public List<String> customerIds() {
        return customers.keySet().stream().sorted().toList();
    }

    public Optional<List<LedgerEntry>> findLedger(String account) {
        return Optional.ofNullable(ledgers.get(account));
    }

    public List<String> accountCodes() {
        return ledgers.keySet().stream().sorted().toList();
    }
}
