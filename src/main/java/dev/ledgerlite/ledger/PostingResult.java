package dev.ledgerlite.ledger;

/**
 * @param replayed true when the transaction was created by an earlier request with the
 *                 same idempotency key and we're just handing it back
 */
public record PostingResult(LedgerTransaction transaction, boolean replayed) {
}
