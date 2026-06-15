package com.dileep.ailearning.invoice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/**
 * The fixed domain object we map every messy invoice onto (Module 1's target schema).
 *
 * <p>This is the "contract" half of structured extraction: the model is told to
 * produce exactly these keys, and Bean Validation annotations here act as a
 * <b>guardrail</b> — if the model omits {@code invoiceNumber} or returns zero line
 * items, validation fails and the service re-asks (and ultimately rejects) the output
 * before it could ever reach a database.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} keeps us robust: if a model
 * hallucinates an extra key we drop it rather than blowing up.
 *
 * @param invoiceNumber required; the invoice's identifier
 * @param invoiceDate   raw date string as printed on the invoice (normalise downstream)
 * @param vendorName    seller name, or null
 * @param vendorGstin   seller's 15-char GSTIN, or null
 * @param buyerName     buyer name, or null
 * @param buyerGstin    buyer's GSTIN, or null
 * @param currency      ISO currency such as "INR"/"USD", or null
 * @param lineItems     at least one row (required)
 * @param subtotal      pre-tax total, or null
 * @param taxAmount     total tax, or null
 * @param totalAmount   grand total, or null
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Invoice(
        @NotEmpty String invoiceNumber,
        String invoiceDate,
        String vendorName,
        String vendorGstin,
        String buyerName,
        String buyerGstin,
        String currency,
        @NotEmpty List<@Valid LineItem> lineItems,
        @PositiveOrZero BigDecimal subtotal,
        @PositiveOrZero BigDecimal taxAmount,
        @PositiveOrZero BigDecimal totalAmount
) {
}
