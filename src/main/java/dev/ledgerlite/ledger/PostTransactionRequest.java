package dev.ledgerlite.ledger;

import java.util.List;

import dev.ledgerlite.common.Hashing;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostTransactionRequest(
        @Size(max = 255) String description,
        @Size(max = 100) String externalRef,
        @NotNull @Size(min = 2, max = 50) List<@Valid @NotNull EntryRequest> entries) {

    /**
     * Stable hash of everything that affects the outcome, used to spot a client reusing
     * an idempotency key for a different request. Strings are length-prefixed so that
     * moving text between fields can't produce the same input.
     */
    String fingerprint() {
        StringBuilder sb = new StringBuilder();
        appendField(sb, description);
        appendField(sb, externalRef);
        for (EntryRequest entry : entries) {
            sb.append(entry.accountId()).append('|')
                    .append(entry.direction()).append('|')
                    .append(entry.amount()).append('|')
                    .append(entry.currency()).append(';');
        }
        return Hashing.sha256Hex(sb.toString());
    }

    private static void appendField(StringBuilder sb, String value) {
        if (value == null) {
            sb.append("-1:");
        } else {
            sb.append(value.length()).append(':').append(value);
        }
    }
}
