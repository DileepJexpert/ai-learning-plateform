package com.dileep.ailearning.invoice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * One row on an invoice.
 *
 * <p>Money/quantity fields are {@link BigDecimal} — never {@code double}. In a
 * payments/ERP context, binary floating point silently corrupts totals; the
 * model returns plain JSON numbers and Jackson maps them to exact decimals.
 *
 * <p>Nullable fields use {@code null} when the value isn't on the invoice — the
 * prompt forbids the model from inventing them. {@code @PositiveOrZero} passes on
 * {@code null}, so it only rejects genuinely-bad (negative) numbers.
 *
 * @param description human-readable item (required)
 * @param hsnCode     HSN/SAC code for GST classification, or null
 * @param quantity    quantity, or null
 * @param unitPrice   price per unit, or null
 * @param taxRate     GST percentage (e.g. 18 for 18%), or null
 * @param lineTotal   line amount, or null
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LineItem(
        @NotBlank String description,
        String hsnCode,
        @PositiveOrZero BigDecimal quantity,
        @PositiveOrZero BigDecimal unitPrice,
        @PositiveOrZero BigDecimal taxRate,
        @PositiveOrZero BigDecimal lineTotal
) {
}
