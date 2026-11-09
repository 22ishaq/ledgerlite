package dev.ledgerlite.account;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.ledgerlite.common.Direction;

@Repository
public class AccountRepository {

    private static final String COLUMNS =
            "id, client_id, name, currency, normal_balance, allow_negative, balance, created_at";

    private static final RowMapper<Account> MAPPER = (rs, rowNum) -> new Account(
            rs.getObject("id", UUID.class),
            rs.getObject("client_id", UUID.class),
            rs.getString("name"),
            rs.getString("currency"),
            Direction.valueOf(rs.getString("normal_balance")),
            rs.getBoolean("allow_negative"),
            rs.getLong("balance"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    private final JdbcClient jdbc;

    public AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Account insert(UUID clientId, String name, String currency, Direction normalBalance, boolean allowNegative) {
        return jdbc.sql("insert into account (id, client_id, name, currency, normal_balance, allow_negative) "
                        + "values (:id, :clientId, :name, :currency, :normalBalance, :allowNegative) "
                        + "returning " + COLUMNS)
                .param("id", UUID.randomUUID())
                .param("clientId", clientId)
                .param("name", name)
                .param("currency", currency)
                .param("normalBalance", normalBalance.name())
                .param("allowNegative", allowNegative)
                .query(MAPPER)
                .single();
    }

    public Optional<Account> findById(UUID clientId, UUID id) {
        return jdbc.sql("select " + COLUMNS + " from account where id = :id and client_id = :clientId")
                .param("id", id)
                .param("clientId", clientId)
                .query(MAPPER)
                .optional();
    }

    public List<Account> findAll(UUID clientId, int limit) {
        return jdbc.sql("select " + COLUMNS + " from account where client_id = :clientId order by created_at, id limit :limit")
                .param("clientId", clientId)
                .param("limit", limit)
                .query(MAPPER)
                .list();
    }

    /**
     * Locks the given accounts for the rest of the current transaction. Rows are locked
     * in id order, so two postings that touch the same accounts always queue up behind
     * each other instead of deadlocking.
     */
    public List<Account> lockAll(UUID clientId, Collection<UUID> ids) {
        return jdbc.sql("select " + COLUMNS + " from account where client_id = :clientId and id in (:ids) order by id for update")
                .param("clientId", clientId)
                .param("ids", ids)
                .query(MAPPER)
                .list();
    }

    public void updateBalance(UUID id, long balance) {
        jdbc.sql("update account set balance = :balance where id = :id")
                .param("balance", balance)
                .param("id", id)
                .update();
    }
}
