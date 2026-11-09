package dev.ledgerlite.ledger;

import java.util.UUID;

record IdempotencyRecord(String key, String requestHash, UUID transactionId) {
}
