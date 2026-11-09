package dev.ledgerlite.ledger;

import java.util.UUID;

import dev.ledgerlite.common.Direction;

public record Entry(long id, UUID accountId, Direction direction, long amount, String currency) {
}
