package com.dileep.ailearning.guardrail;

import com.dileep.ailearning.invoice.model.Invoice;
import com.dileep.ailearning.invoice.model.LineItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Business-rule guardrail that runs AFTER schema validation, BEFORE anything is
 * persisted (Module 6). Schema validation (Module 1) checks the <i>shape</i>; this
 * checks the <i>content</i> makes business sense:
 * <ul>
 *   <li><b>Allow-list:</b> currency must be one we accept.</li>
 *   <li><b>Format:</b> GSTINs must match the 15-char GSTIN pattern.</li>
 *   <li><b>Arithmetic sanity:</b> subtotal + tax ≈ total; quantity × price ≈ line total.</li>
 * </ul>
 *
 * <p>A model can emit perfectly-shaped JSON that is still nonsense (a total that
 * doesn't add up, a malformed GSTIN). This layer catches that so bad data never
 * reaches the ledger. Pure logic — fully unit-testable.
 */
@Component
public class InvoiceGuardrail {

    /** Currencies we accept — anything else is rejected (allow-list, not deny-list). */
    static final Set<String> ALLOWED_CURRENCIES = Set.of("INR", "USD", "EUR", "GBP", "AED", "SGD");

    /** 15-char GSTIN: 2 state digits, 10-char PAN, entity digit, 'Z', checksum char. */
    private static final Pattern GSTIN = Pattern.compile("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]");

    /** Tolerances for floating arithmetic / rounding. */
    private static final BigDecimal TOTALS_TOLERANCE = new BigDecimal("0.05");
    private static final BigDecimal LINE_TOLERANCE = new BigDecimal("0.50");

    public List<String> check(Invoice invoice) {
        List<String> violations = new ArrayList<>();

        if (invoice.currency() != null
                && !ALLOWED_CURRENCIES.contains(invoice.currency().trim().toUpperCase())) {
            violations.add("currency '" + invoice.currency() + "' is not in the allow-list " + ALLOWED_CURRENCIES);
        }

        checkGstin("vendorGstin", invoice.vendorGstin(), violations);
        checkGstin("buyerGstin", invoice.buyerGstin(), violations);

        // subtotal + tax ≈ total
        if (allPresent(invoice.subtotal(), invoice.taxAmount(), invoice.totalAmount())) {
            BigDecimal sum = invoice.subtotal().add(invoice.taxAmount());
            if (sum.subtract(invoice.totalAmount()).abs().compareTo(TOTALS_TOLERANCE) > 0) {
                violations.add("subtotal + taxAmount (" + sum + ") does not equal totalAmount ("
                        + invoice.totalAmount() + ")");
            }
        }

        // quantity × unitPrice ≈ lineTotal, per line
        List<LineItem> items = invoice.lineItems();
        for (int i = 0; items != null && i < items.size(); i++) {
            LineItem li = items.get(i);
            if (allPresent(li.quantity(), li.unitPrice(), li.lineTotal())) {
                BigDecimal computed = li.quantity().multiply(li.unitPrice());
                if (computed.subtract(li.lineTotal()).abs().compareTo(LINE_TOLERANCE) > 0) {
                    violations.add("line " + i + " (" + li.description() + "): quantity × unitPrice ("
                            + computed + ") does not equal lineTotal (" + li.lineTotal() + ")");
                }
            }
        }
        return violations;
    }

    /** True if the invoice passes every business rule. */
    public boolean isClean(Invoice invoice) {
        return check(invoice).isEmpty();
    }

    private static void checkGstin(String field, String value, List<String> violations) {
        if (value != null && !GSTIN.matcher(value.trim()).matches()) {
            violations.add(field + " '" + value + "' is not a valid 15-character GSTIN");
        }
    }

    private static boolean allPresent(BigDecimal... values) {
        for (BigDecimal v : values) {
            if (v == null) {
                return false;
            }
        }
        return true;
    }
}
