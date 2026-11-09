package dev.ledgerlite.security;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import dev.ledgerlite.IntegrationTest;

class AuthIT extends IntegrationTest {

    @Test
    void requestsWithoutAKeyAreRejected() throws Exception {
        mvc.perform(get("/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void unknownKeyIsRejected() throws Exception {
        mvc.perform(get("/v1/accounts").header(AUTHORIZATION, "Bearer " + ApiKeys.generate()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyBearerIsRejected() throws Exception {
        mvc.perform(get("/v1/accounts").header(AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedClientIsRejected() throws Exception {
        jdbc.sql("update api_client set active = false where id = :id").param("id", client.id()).update();

        mvc.perform(get("/v1/accounts").header(AUTHORIZATION, bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientsCannotSeeEachOthersAccounts() throws Exception {
        String mine = openAccount("Mine", "GBP", "CREDIT");
        String otherKey = newClientKey("someone-else");

        mvc.perform(get("/v1/accounts/" + mine).header(AUTHORIZATION, "Bearer " + otherKey))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/accounts/" + mine + "/entries").header(AUTHORIZATION, "Bearer " + otherKey))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/accounts").header(AUTHORIZATION, "Bearer " + otherKey))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void clientsCannotSeeEachOthersTransactions() throws Exception {
        String cash = openAccount("Settlement", "GBP", "DEBIT");
        String wallet = openAccount("Wallet", "GBP", "CREDIT");
        String created = pay("fund", cash, wallet, 500).andReturn().getResponse().getContentAsString();
        String otherKey = newClientKey("someone-else");

        mvc.perform(get("/v1/transactions/" + JsonPath.read(created, "$.id"))
                        .header(AUTHORIZATION, "Bearer " + otherKey))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotMoveMoneyOutOfSomeoneElsesAccount() throws Exception {
        String victimCash = openAccount("Settlement", "GBP", "DEBIT");
        String victim = openAccount("Victim", "GBP", "CREDIT");
        pay("fund", victimCash, victim, 5_000);

        apiKey = newClientKey("attacker");
        String attacker = openAccount("Attacker", "GBP", "CREDIT");

        pay("steal", victim, attacker, 5_000)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("account_not_found"));
    }

    @Test
    void healthCheckIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
