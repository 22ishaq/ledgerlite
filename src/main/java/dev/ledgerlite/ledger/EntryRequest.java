package dev.ledgerlite.ledger;

import java.util.UUID;

import dev.ledgerlite.common.Direction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record EntryRequest(
        @NotNull UUID accountId,
        @NotNull Direction direction,
        // capped so that summing a full transaction can never overflow a long
        @NotNull @Positive @Max(1_000_000_000_000_000L) Long amount,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3 letter currency code") String currency) {
}
