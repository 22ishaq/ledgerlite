package dev.ledgerlite.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.ledgerlite.common.Direction;

class PostTransactionRequestTest {

    private final UUID from = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID to = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void sameRequestGivesSameFingerprint() {
        assertThat(request("rent", "ref-1", 500).fingerprint())
                .isEqualTo(request("rent", "ref-1", 500).fingerprint())
                .hasSize(64);
    }

    @Test
    void changingTheAmountChangesTheFingerprint() {
        assertThat(request("rent", "ref-1", 500).fingerprint())
                .isNotEqualTo(request("rent", "ref-1", 501).fingerprint());
    }

    @Test
    void textCantBeShuffledBetweenFields() {
        assertThat(request("ab", "c", 500).fingerprint())
                .isNotEqualTo(request("a", "bc", 500).fingerprint());
    }

    @Test
    void nullAndEmptyDescriptionAreDifferent() {
        assertThat(request(null, "ref", 500).fingerprint())
                .isNotEqualTo(request("", "ref", 500).fingerprint());
    }

    private PostTransactionRequest request(String description, String ref, long amount) {
        return new PostTransactionRequest(description, ref, List.of(
                new EntryRequest(from, Direction.DEBIT, amount, "GBP"),
                new EntryRequest(to, Direction.CREDIT, amount, "GBP")));
    }
}
