package dev.ledgerlite.ledger;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class IdempotencyStore {

    private final JdbcClient jdbc;

    IdempotencyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tries to take the key for the current transaction. Returns empty if we got it,
     * or the earlier request's record if the key was already used.
     *
     * <p>If another request with the same key is still in flight, Postgres makes this
     * insert wait until that transaction finishes. Then we either see its committed row,
     * or it rolled back and the key is ours. Either way we never post twice.
     */
    Optional<IdempotencyRecord> claim(UUID clientId, String key, String requestHash) {
        int inserted = jdbc.sql("""
                        insert into idempotency_key (client_id, idem_key, request_hash)
                        values (:clientId, :key, :hash)
                        on conflict do nothing
                        """)
                .param("clientId", clientId)
                .param("key", key)
                .param("hash", requestHash)
                .update();
        if (inserted == 1) {
            return Optional.empty();
        }
        return Optional.of(find(clientId, key));
    }

    void complete(UUID clientId, String key, UUID transactionId) {
        jdbc.sql("update idempotency_key set transaction_id = :txId where client_id = :clientId and idem_key = :key")
                .param("txId", transactionId)
                .param("clientId", clientId)
                .param("key", key)
                .update();
    }

    private IdempotencyRecord find(UUID clientId, String key) {
        return jdbc.sql("select idem_key, request_hash, transaction_id from idempotency_key where client_id = :clientId and idem_key = :key")
                .param("clientId", clientId)
                .param("key", key)
                .query((rs, rowNum) -> new IdempotencyRecord(
                        rs.getString("idem_key"),
                        rs.getString("request_hash"),
                        rs.getObject("transaction_id", UUID.class)))
                .single();
    }
}
