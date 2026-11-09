package dev.ledgerlite.account;

import java.time.Instant;
import java.util.UUID;

import dev.ledgerlite.common.Direction;

public record AccountResponse(
        UUID id,
        String name,
        String currency,
        Direction normalBalance,
        boolean allowNegative,
        long balance,
        Instant createdAt) {

    static AccountResponse from(Account account) {
        return new AccountResponse(account.id(), account.name(), account.currency(), account.normalBalance(),
                account.allowNegative(), account.balance(), account.createdAt());
    }
}
