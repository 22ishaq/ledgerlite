package dev.ledgerlite.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeysTest {

    @Test
    void generatedKeysArePrefixedAndUnique() {
        String first = ApiKeys.generate();
        String second = ApiKeys.generate();

        assertThat(first).startsWith("ll_").hasSizeGreaterThan(40);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hashIsStableHex() {
        String key = ApiKeys.generate();

        assertThat(ApiKeys.hash(key))
                .isEqualTo(ApiKeys.hash(key))
                .matches("[0-9a-f]{64}");
    }
}
