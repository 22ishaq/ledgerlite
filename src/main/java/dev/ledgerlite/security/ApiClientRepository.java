package dev.ledgerlite.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ApiClientRepository {

    private final JdbcClient jdbc;

    public ApiClientRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ApiClient> findActiveByKeyHash(String keyHash) {
        return jdbc.sql("select id, name from api_client where key_hash = :hash and active")
                .param("hash", keyHash)
                .query((rs, rowNum) -> new ApiClient(rs.getObject("id", UUID.class), rs.getString("name")))
                .optional();
    }

    public ApiClient create(String name, String rawKey) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into api_client (id, name, key_hash) values (:id, :name, :hash)")
                .param("id", id)
                .param("name", name)
                .param("hash", ApiKeys.hash(rawKey))
                .update();
        return new ApiClient(id, name);
    }
}
