package dev.ledgerlite.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import dev.ledgerlite.IntegrationTest;

class TransactionApiIT extends IntegrationTest {

    private String cash;
    private String alice;
    private String bob;
    private String fees;

    @BeforeEach
    void openAccounts() throws Exception {
        cash = openAccount("Settlement", "GBP", "DEBIT");
        alice = openAccount("Alice", "GBP", "CREDIT");
        bob = openAccount("Bob", "GBP", "CREDIT");
        fees = openAccount("Fee revenue", "GBP", "CREDIT");
        pay("fund-alice", cash, alice, 10_000).andExpect(status().isCreated());
    }

    @Test
    void postsAMultiLegTransaction() throws Exception {
        String body = """
                {
                  "description": "Order 1042",
                  "externalRef": "order-1042",
                  "entries": [
                    {"accountId": "%s", "direction": "DEBIT",  "amount": 1000, "currency": "GBP"},
                    {"accountId": "%s", "direction": "CREDIT", "amount": 970,  "currency": "GBP"},
                    {"accountId": "%s", "direction": "CREDIT", "amount": 30,   "currency": "GBP"}
                  ]
                }
                """.formatted(alice, bob, fees);

        String created = postTransaction("order-1042", body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mvc.perform(get("/v1/transactions/" + id).header(AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Order 1042"))
                .andExpect(jsonPath("$.externalRef").value("order-1042"))
                .andExpect(jsonPath("$.entries.length()").value(3));

        assertThat(balanceOf(alice)).isEqualTo(9_000);
        assertThat(balanceOf(bob)).isEqualTo(970);
        assertThat(balanceOf(fees)).isEqualTo(30);
    }

    @Test
    void rejectsUnbalancedEntries() throws Exception {
        String body = """
                {"entries": [
                  {"accountId": "%s", "direction": "DEBIT",  "amount": 1000, "currency": "GBP"},
                  {"accountId": "%s", "direction": "CREDIT", "amount": 900,  "currency": "GBP"}
                ]}
                """.formatted(alice, bob);

        postTransaction("unbalanced", body)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("unbalanced_transaction"));

        assertThat(balanceOf(alice)).isEqualTo(10_000);
        assertThat(transactionCount()).isEqualTo(1);
    }

    @Test
    void needsAtLeastTwoEntries() throws Exception {
        String body = """
                {"entries": [{"accountId": "%s", "direction": "DEBIT", "amount": 1000, "currency": "GBP"}]}
                """.formatted(alice);

        postTransaction("one-leg", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries"));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        postTransaction("broken", "{\"entries\": [")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownTransactionIsNotFound() throws Exception {
        mvc.perform(get("/v1/transactions/7f1e6c1e-58a4-4b7a-9d55-3f3c3c0c0c0c").header(AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("transaction_not_found"));
    }

    @Test
    void pagesThroughAnAccountsEntries() throws Exception {
        for (int i = 0; i < 4; i++) {
            pay("p" + i, alice, bob, 100).andExpect(status().isCreated());
        }

        List<Integer> seen = new ArrayList<>();
        Long cursor = 0L;
        int pages = 0;
        while (cursor != null) {
            String page = mvc.perform(get("/v1/accounts/" + alice + "/entries")
                            .param("limit", "2")
                            .param("after", cursor.toString())
                            .header(AUTHORIZATION, bearer()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            seen.addAll(JsonPath.read(page, "$.data[*].id"));
            Number next = JsonPath.read(page, "$.nextCursor");
            cursor = next == null ? null : next.longValue();
            pages++;
        }

        // the funding credit plus four payments
        assertThat(seen).hasSize(5).isSorted().doesNotHaveDuplicates();
        assertThat(pages).isEqualTo(3);
    }

    @Test
    void entryLimitIsValidated() throws Exception {
        mvc.perform(get("/v1/accounts/" + alice + "/entries").param("limit", "500").header(AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest());
    }

    private ResultActions postTransaction(String idempotencyKey, String body) throws Exception {
        return mvc.perform(post("/v1/transactions")
                .header(AUTHORIZATION, bearer())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(APPLICATION_JSON)
                .content(body));
    }

    private long transactionCount() {
        return jdbc.sql("select count(*) from ledger_transaction").query(Long.class).single();
    }
}
