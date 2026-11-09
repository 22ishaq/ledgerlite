package dev.ledgerlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import dev.ledgerlite.security.ApiClient;
import dev.ledgerlite.security.ApiClientRepository;
import dev.ledgerlite.security.ApiKeys;

/**
 * Runs against a real Postgres (see application-test.yml). Tables are wiped before
 * each test and the ledger invariants are checked after each one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    private ApiClientRepository clients;

    protected ApiClient client;
    protected String apiKey;

    @BeforeEach
    void resetDatabase() {
        jdbc.sql("truncate idempotency_key, ledger_entry, ledger_transaction, account, api_client").update();
        apiKey = ApiKeys.generate();
        client = clients.create("test-client", apiKey);
    }

    @AfterEach
    void ledgerStillAddsUp() {
        List<UUID> drifted = jdbc.sql("""
                        select a.id
                        from account a
                        left join ledger_entry e on e.account_id = a.id
                        group by a.id
                        having a.balance <> coalesce(sum(case when e.direction = a.normal_balance then e.amount else -e.amount end), 0)
                        """)
                .query(UUID.class)
                .list();
        assertThat(drifted).as("accounts whose stored balance doesn't match their entries").isEmpty();

        List<String> unbalanced = jdbc.sql("""
                        select currency
                        from ledger_entry
                        group by currency
                        having sum(case when direction = 'DEBIT' then amount else -amount end) <> 0
                        """)
                .query(String.class)
                .list();
        assertThat(unbalanced).as("currencies where debits != credits").isEmpty();
    }

    protected String newClientKey(String name) {
        String key = ApiKeys.generate();
        clients.create(name, key);
        return key;
    }

    protected String bearer() {
        return "Bearer " + apiKey;
    }

    protected String openAccount(String name, String currency, String normalBalance) throws Exception {
        return openAccount(apiKey, name, currency, normalBalance);
    }

    protected String openAccount(String key, String name, String currency, String normalBalance) throws Exception {
        String response = mvc.perform(post("/v1/accounts")
                        .header(AUTHORIZATION, "Bearer " + key)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "currency": "%s", "normalBalance": "%s"}
                                """.formatted(name, currency, normalBalance)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    protected long balanceOf(String accountId) throws Exception {
        String response = mvc.perform(get("/v1/accounts/" + accountId).header(AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.balance")).longValue();
    }

    protected ResultActions pay(String idempotencyKey, String from, String to, long amount) throws Exception {
        return pay(idempotencyKey, from, to, amount, "GBP");
    }

    protected ResultActions pay(String idempotencyKey, String from, String to, long amount, String currency) throws Exception {
        return mvc.perform(post("/v1/payments")
                .header(AUTHORIZATION, bearer())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(APPLICATION_JSON)
                .content("""
                        {"fromAccountId": "%s", "toAccountId": "%s", "amount": %d, "currency": "%s"}
                        """.formatted(from, to, amount, currency)));
    }
}
