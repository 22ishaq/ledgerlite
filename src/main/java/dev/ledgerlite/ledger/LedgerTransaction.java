package dev.ledgerlite.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LedgerTransaction(
        UUID id,
        String description,
        String externalRef,
        Instant createdAt,
        List<Entry> entries) {

    public LedgerTransaction {
        entries = List.copyOf(entries);
    }

    LedgerTransaction withEntries(List<Entry> entries) {
        return new LedgerTransaction(id, description, externalRef, createdAt, entries);
    }
}
