package dev.ledgerlite.ledger;

import java.time.Instant;
import java.util.UUID;

import dev.ledgerlite.common.Direction;

/** An entry as seen from one account's statement. */
public record AccountEntry(
        long id,
        UUID transactionId,
        Direction direction,
        long amount,
        String currency,
        Instant createdAt) {
}
