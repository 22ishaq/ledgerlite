package dev.ledgerlite.ledger;

import java.util.List;
import java.util.UUID;

import dev.ledgerlite.common.Direction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A two-legged transfer. The source is debited and the destination credited, which
 * moves money the way you'd expect between credit-normal (wallet style) accounts.
 */
public record PaymentRequest(
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @NotNull @Positive @Max(1_000_000_000_000_000L) Long amount,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3 letter currency code") String currency,
        @Size(max = 255) String description,
        @Size(max = 100) String externalRef) {

    PostTransactionRequest toTransaction() {
        return new PostTransactionRequest(description, externalRef, List.of(
                new EntryRequest(fromAccountId, Direction.DEBIT, amount, currency),
                new EntryRequest(toAccountId, Direction.CREDIT, amount, currency)));
    }
}
