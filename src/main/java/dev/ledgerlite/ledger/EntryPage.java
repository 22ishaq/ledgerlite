package dev.ledgerlite.ledger;

import java.util.List;

public record EntryPage(List<AccountEntry> data, Long nextCursor) {
}
