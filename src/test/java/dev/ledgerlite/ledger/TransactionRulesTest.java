package dev.ledgerlite.ledger;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.ledgerlite.common.Direction;
import dev.ledgerlite.common.LedgerException;

class TransactionRulesTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void acceptsBalancedEntries() {
        List<EntryRequest> entries = List.of(
                debit(a, 1000, "GBP"),
                credit(b, 750, "GBP"),
                credit(c, 250, "GBP"));

        assertThatCode(() -> TransactionRules.checkBalanced(entries)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnbalancedEntries() {
        List<EntryRequest> entries = List.of(debit(a, 1000, "GBP"), credit(b, 999, "GBP"));

        assertThatThrownBy(() -> TransactionRules.checkBalanced(entries))
                .isInstanceOf(LedgerException.class)
                .hasMessageContaining("GBP")
                .hasMessageContaining("= 1)");
    }

    @Test
    void eachCurrencyHasToBalanceOnItsOwn() {
        // same total either way, but 100 GBP is not 100 EUR
        List<EntryRequest> entries = List.of(debit(a, 100, "GBP"), credit(b, 100, "EUR"));

        assertThatThrownBy(() -> TransactionRules.checkBalanced(entries))
                .isInstanceOf(LedgerException.class)
                .extracting(ex -> ((LedgerException) ex).code())
                .isEqualTo("unbalanced_transaction");
    }

    @Test
    void acceptsMultiCurrencyWhenEachSideBalances() {
        List<EntryRequest> entries = List.of(
                debit(a, 100, "GBP"), credit(b, 100, "GBP"),
                debit(c, 120, "EUR"), credit(a, 120, "EUR"));

        assertThatCode(() -> TransactionRules.checkBalanced(entries)).doesNotThrowAnyException();
    }

    private static EntryRequest debit(UUID account, long amount, String currency) {
        return new EntryRequest(account, Direction.DEBIT, amount, currency);
    }

    private static EntryRequest credit(UUID account, long amount, String currency) {
        return new EntryRequest(account, Direction.CREDIT, amount, currency);
    }
}
