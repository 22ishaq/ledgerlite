package dev.ledgerlite.account;

import java.time.Instant;
import java.util.UUID;

import dev.ledgerlite.common.Direction;

/**
 * Balances are stored in minor units (pence, cents) and expressed in the account's
 * normal direction, so a credit-normal wallet with money in it has a positive balance.
 */
public record Account(
        UUID id,
        UUID clientId,
        String name,
        String currency,
        Direction normalBalance,
        boolean allowNegative,
        long balance,
        Instant createdAt) {

    /** How much an entry moves this account's balance. */
    public long signedAmount(Direction direction, long amount) {
        return direction == normalBalance ? amount : -amount;
    }
}
