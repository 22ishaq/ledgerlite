package dev.ledgerlite.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.ledgerlite.common.Direction;

class AccountTest {

    @Test
    void creditNormalAccountGoesUpOnCredit() {
        Account wallet = account(Direction.CREDIT);

        assertThat(wallet.signedAmount(Direction.CREDIT, 500)).isEqualTo(500);
        assertThat(wallet.signedAmount(Direction.DEBIT, 500)).isEqualTo(-500);
    }

    @Test
    void debitNormalAccountGoesUpOnDebit() {
        Account cash = account(Direction.DEBIT);

        assertThat(cash.signedAmount(Direction.DEBIT, 500)).isEqualTo(500);
        assertThat(cash.signedAmount(Direction.CREDIT, 500)).isEqualTo(-500);
    }

    private static Account account(Direction normalBalance) {
        return new Account(UUID.randomUUID(), UUID.randomUUID(), "test", "GBP", normalBalance, false, 0, Instant.now());
    }
}
