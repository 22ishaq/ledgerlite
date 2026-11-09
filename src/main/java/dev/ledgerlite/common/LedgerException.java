package dev.ledgerlite.common;

import java.util.UUID;

import org.springframework.http.HttpStatus;

public class LedgerException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public LedgerException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static LedgerException accountNotFound(UUID accountId) {
        return new LedgerException(HttpStatus.NOT_FOUND, "account_not_found",
                "Account " + accountId + " does not exist");
    }

    public static LedgerException transactionNotFound(UUID transactionId) {
        return new LedgerException(HttpStatus.NOT_FOUND, "transaction_not_found",
                "Transaction " + transactionId + " does not exist");
    }

    public static LedgerException insufficientFunds(UUID accountId) {
        return new LedgerException(HttpStatus.UNPROCESSABLE_CONTENT, "insufficient_funds",
                "Account " + accountId + " has insufficient funds");
    }

    public static LedgerException unbalanced(String currency, long net) {
        return new LedgerException(HttpStatus.UNPROCESSABLE_CONTENT, "unbalanced_transaction",
                "Entries in " + currency + " do not balance (debits - credits = " + net + ")");
    }

    public static LedgerException currencyMismatch(UUID accountId, String accountCurrency, String entryCurrency) {
        return new LedgerException(HttpStatus.UNPROCESSABLE_CONTENT, "currency_mismatch",
                "Account " + accountId + " is in " + accountCurrency + " but the entry is in " + entryCurrency);
    }

    public static LedgerException sameAccount() {
        return new LedgerException(HttpStatus.UNPROCESSABLE_CONTENT, "same_account",
                "Source and destination accounts must be different");
    }

    public static LedgerException unknownCurrency(String currency) {
        return new LedgerException(HttpStatus.UNPROCESSABLE_CONTENT, "unknown_currency",
                currency + " is not an ISO 4217 currency code");
    }

    public static LedgerException idempotencyKeyReused() {
        return new LedgerException(HttpStatus.UNPROCESSABLE_CONTENT, "idempotency_key_reused",
                "This Idempotency-Key was already used with a different request body");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
