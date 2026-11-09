package dev.ledgerlite.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import dev.ledgerlite.IntegrationTest;

class PaymentApiIT extends IntegrationTest {

    private String cash;
    private String alice;
    private String bob;

    @BeforeEach
    void openAccounts() throws Exception {
        cash = openAccount("Settlement", "GBP", "DEBIT");
        alice = openAccount("Alice", "GBP", "CREDIT");
        bob = openAccount("Bob", "GBP", "CREDIT");
        pay("fund-alice", cash, alice, 10_000).andExpect(status().isCreated());
    }

    @Test
    void movesMoneyBetweenAccounts() throws Exception {
        pay("pay-1", alice, bob, 2_500)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/v1/transactions/")))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].accountId").value(alice))
                .andExpect(jsonPath("$.entries[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.entries[1].accountId").value(bob))
                .andExpect(jsonPath("$.entries[1].direction").value("CREDIT"));

        assertThat(balanceOf(alice)).isEqualTo(7_500);
        assertThat(balanceOf(bob)).isEqualTo(2_500);
        assertThat(balanceOf(cash)).isEqualTo(10_000);
    }

    @Test
    void retryWithSameKeyReturnsTheOriginalTransaction() throws Exception {
        String first = pay("pay-1", alice, bob, 2_500)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String retry = pay("pay-1", alice, bob, 2_500)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(retry, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat(balanceOf(alice)).isEqualTo(7_500);
        assertThat(balanceOf(bob)).isEqualTo(2_500);
    }

    @Test
    void reusingAKeyForADifferentPaymentIsRejected() throws Exception {
        pay("pay-1", alice, bob, 2_500).andExpect(status().isCreated());

        pay("pay-1", alice, bob, 3_000)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("idempotency_key_reused"));

        assertThat(balanceOf(bob)).isEqualTo(2_500);
    }

    @Test
    void keysAreScopedToTheClient() throws Exception {
        String otherKey = newClientKey("other-client");
        String otherCash = openAccount(otherKey, "Other settlement", "GBP", "DEBIT");
        String otherWallet = openAccount(otherKey, "Other wallet", "GBP", "CREDIT");

        pay("pay-1", alice, bob, 100).andExpect(status().isCreated());
        mvc.perform(post("/v1/payments")
                        .header(AUTHORIZATION, "Bearer " + otherKey)
                        .header("Idempotency-Key", "pay-1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fromAccountId": "%s", "toAccountId": "%s", "amount": 100, "currency": "GBP"}
                                """.formatted(otherCash, otherWallet)))
                .andExpect(status().isCreated());
    }

    @Test
    void insufficientFundsLeavesBalancesAlone() throws Exception {
        pay("pay-big", alice, bob, 10_001)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("insufficient_funds"));

        assertThat(balanceOf(alice)).isEqualTo(10_000);
        assertThat(balanceOf(bob)).isZero();
    }

    @Test
    void failedPaymentCanBeRetriedWithTheSameKey() throws Exception {
        pay("pay-big", alice, bob, 12_000).andExpect(status().is(422));
        pay("top-up", cash, alice, 5_000).andExpect(status().isCreated());

        pay("pay-big", alice, bob, 12_000).andExpect(status().isCreated());

        assertThat(balanceOf(alice)).isEqualTo(3_000);
        assertThat(balanceOf(bob)).isEqualTo(12_000);
    }

    @Test
    void cannotPayTheSameAccount() throws Exception {
        pay("self", alice, alice, 100)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("same_account"));
    }

    @Test
    void currencyHasToMatchTheAccounts() throws Exception {
        String euros = openAccount("Euro wallet", "EUR", "CREDIT");

        pay("fx", alice, euros, 100)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("currency_mismatch"));
    }

    @Test
    void unknownAccountIsNotFound() throws Exception {
        pay("missing", alice, UUID.randomUUID().toString(), 100)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("account_not_found"));
    }

    @Test
    void idempotencyKeyIsRequired() throws Exception {
        mvc.perform(post("/v1/payments")
                        .header(AUTHORIZATION, bearer())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fromAccountId": "%s", "toAccountId": "%s", "amount": 100, "currency": "GBP"}
                                """.formatted(alice, bob)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidBodyListsTheBadFields() throws Exception {
        pay("negative", alice, bob, -5, "gbp")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"))
                .andExpect(jsonPath("$.errors[?(@.field == 'amount')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'currency')]").exists());
    }
}
