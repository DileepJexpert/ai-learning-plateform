package com.dileep.ailearning.invoice;

import org.springframework.stereotype.Component;

/**
 * Builds the prompts that make extraction reliable (Module 1).
 *
 * <p>Key idea: the <b>system prompt is the contract</b> (schema + rules + worked
 * examples) and stays constant; the <b>user prompt is the data</b> (one invoice).
 * Weak/local models honour {@code format: "json"} for <i>validity</i> but still
 * need the schema spelled out and reinforced with few-shot examples to get the
 * <i>shape</i> right — that's why both live here.
 */
@Component
public class InvoicePromptFactory {

    /**
     * The contract. Strict schema, hard rules, and two few-shot examples — the
     * second one deliberately shows missing fields becoming {@code null}.
     */
    public String systemPrompt() {
        return """
                You are a meticulous invoice data-extraction engine. You convert raw invoice
                text into ONE structured JSON object. You never explain, never apologise, and
                never output anything except a single JSON object.

                OUTPUT CONTRACT
                Output exactly one JSON object matching this schema (same keys, same nesting):

                {
                  "invoiceNumber": string,
                  "invoiceDate":   string | null,   // copy EXACTLY as printed on the invoice
                  "vendorName":    string | null,
                  "vendorGstin":   string | null,   // 15-char GSTIN, else null
                  "buyerName":     string | null,
                  "buyerGstin":    string | null,
                  "currency":      string | null,   // e.g. "INR", "USD"
                  "lineItems": [
                    {
                      "description": string,
                      "hsnCode":     string | null,  // HSN/SAC code
                      "quantity":    number | null,
                      "unitPrice":   number | null,
                      "taxRate":     number | null,  // GST percent, e.g. 18 for 18%
                      "lineTotal":   number | null
                    }
                  ],
                  "subtotal":    number | null,
                  "taxAmount":   number | null,
                  "totalAmount": number | null
                }

                RULES
                1. If a value is not present in the invoice, use null. NEVER guess or invent a value.
                2. Numbers must be plain JSON numbers: no currency symbols, no thousands separators
                   (write 1234.50, not "Rs 1,234.50").
                3. Extract EVERY line item, in the order they appear.
                4. Do not add keys that are not in the schema.
                5. "invoiceNumber" and every line item "description" are required and must be non-null.
                6. Output JSON only — no markdown, no code fences, no commentary.

                EXAMPLE 1
                INPUT:
                TAX INVOICE
                Acme Components Pvt Ltd    GSTIN: 27AABCA1234A1Z5
                Invoice No: INV-2025-0042   Date: 12/03/2025
                Bill To: Bharat Motors Ltd  GSTIN: 29AAFCB5678B1Z3
                Item              HSN    Qty    Rate     Amount
                M6 Hex Bolt       7318   1000   2.50     2500.00
                Steel Washer 6mm  7318   1000   0.40      400.00
                Subtotal 2900.00   GST 18% 522.00   Total 3422.00
                Currency: INR
                OUTPUT:
                {"invoiceNumber":"INV-2025-0042","invoiceDate":"12/03/2025","vendorName":"Acme Components Pvt Ltd","vendorGstin":"27AABCA1234A1Z5","buyerName":"Bharat Motors Ltd","buyerGstin":"29AAFCB5678B1Z3","currency":"INR","lineItems":[{"description":"M6 Hex Bolt","hsnCode":"7318","quantity":1000,"unitPrice":2.50,"taxRate":18,"lineTotal":2500.00},{"description":"Steel Washer 6mm","hsnCode":"7318","quantity":1000,"unitPrice":0.40,"taxRate":18,"lineTotal":400.00}],"subtotal":2900.00,"taxAmount":522.00,"totalAmount":3422.00}

                EXAMPLE 2  (note: no GSTIN, no HSN, no per-unit numbers -> null)
                INPUT:
                Invoice #: 7781
                Date: 2025-04-01
                From: Gupta Traders
                Description                       Amount
                Consulting services - April 2025  45000.00
                Total Due                         45000.00
                OUTPUT:
                {"invoiceNumber":"7781","invoiceDate":"2025-04-01","vendorName":"Gupta Traders","vendorGstin":null,"buyerName":null,"buyerGstin":null,"currency":null,"lineItems":[{"description":"Consulting services - April 2025","hsnCode":null,"quantity":null,"unitPrice":null,"taxRate":null,"lineTotal":45000.00}],"subtotal":null,"taxAmount":null,"totalAmount":45000.00}
                """;
    }

    /** The data half: the raw invoice to extract. */
    public String userPrompt(String rawInvoiceText) {
        return """
                Extract this invoice into the JSON schema:

                """ + rawInvoiceText;
    }

    /**
     * Module 2: the data half when the invoice arrives as an image instead of text.
     * The same schema contract (system prompt) applies — only the input modality
     * changes. We reinforce the "don't guess unreadable values" rule because photos
     * can be blurry, skewed, or partially cropped.
     */
    public String visionUserPrompt() {
        return """
                Extract the invoice shown in the attached image into the JSON schema.
                Read every visible line item, top to bottom. If the image is blurry or a
                value is unreadable, use null for that field — do not guess.
                """;
    }

    /**
     * Correction nudge used on retry. We hand the model its own bad output plus the
     * concrete reason it failed, and ask only for a corrected object. Telling it
     * *why* it failed is far more effective than blindly re-asking.
     */
    public String correctionPrompt(String problem) {
        return """
                Your previous response was rejected.
                Problem: %s
                Respond again with ONLY a corrected JSON object that strictly matches the schema.
                Use null for anything not present. No markdown, no commentary.
                """.formatted(problem);
    }
}
