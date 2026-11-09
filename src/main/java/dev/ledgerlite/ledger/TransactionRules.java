package dev.ledgerlite.ledger;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.ledgerlite.common.Direction;
import dev.ledgerlite.common.LedgerException;

final class TransactionRules {

    private TransactionRules() {
    }

    /** Debits must equal credits in every currency the transaction touches. */
    static void checkBalanced(List<EntryRequest> entries) {
        Map<String, Long> net = new TreeMap<>();
        for (EntryRequest entry : entries) {
            long signed = entry.direction() == Direction.DEBIT ? entry.amount() : -entry.amount();
            net.merge(entry.currency(), signed, Math::addExact);
        }
        for (Map.Entry<String, Long> total : net.entrySet()) {
            if (total.getValue() != 0) {
                throw LedgerException.unbalanced(total.getKey(), total.getValue());
            }
        }
    }
}
