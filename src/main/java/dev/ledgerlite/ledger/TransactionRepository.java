package dev.ledgerlite.ledger;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.ledgerlite.common.Direction;

@Repository
public class TransactionRepository {

    private static final RowMapper<Entry> ENTRY_MAPPER = (rs, rowNum) -> new Entry(
            rs.getLong("id"),
            rs.getObject("account_id", UUID.class),
            Direction.valueOf(rs.getString("direction")),
            rs.getLong("amount"),
            rs.getString("currency"));

    private final JdbcClient jdbc;

    public TransactionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public LedgerTransaction insert(UUID clientId, PostTransactionRequest request) {
        UUID id = UUID.randomUUID();
        OffsetDateTime createdAt = jdbc.sql("""
                        insert into ledger_transaction (id, client_id, description, external_ref)
                        values (:id, :clientId, :description, :externalRef)
                        returning created_at
                        """)
                .param("id", id)
                .param("clientId", clientId)
                .param("description", request.description())
                .param("externalRef", request.externalRef())
                .query(OffsetDateTime.class)
                .single();

        List<Entry> entries = new ArrayList<>(request.entries().size());
        for (EntryRequest entry : request.entries()) {
            long entryId = jdbc.sql("""
                            insert into ledger_entry (transaction_id, account_id, direction, amount, currency)
                            values (:transactionId, :accountId, :direction, :amount, :currency)
                            returning id
                            """)
                    .param("transactionId", id)
                    .param("accountId", entry.accountId())
                    .param("direction", entry.direction().name())
                    .param("amount", entry.amount())
                    .param("currency", entry.currency())
                    .query(Long.class)
                    .single();
            entries.add(new Entry(entryId, entry.accountId(), entry.direction(), entry.amount(), entry.currency()));
        }
        return new LedgerTransaction(id, request.description(), request.externalRef(), createdAt.toInstant(), entries);
    }

    public Optional<LedgerTransaction> find(UUID clientId, UUID id) {
        return jdbc.sql("select id, description, external_ref, created_at from ledger_transaction where id = :id and client_id = :clientId")
                .param("id", id)
                .param("clientId", clientId)
                .query((rs, rowNum) -> new LedgerTransaction(
                        rs.getObject("id", UUID.class),
                        rs.getString("description"),
                        rs.getString("external_ref"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        List.of()))
                .optional()
                .map(txn -> txn.withEntries(entriesOf(txn.id())));
    }

    public List<AccountEntry> entriesForAccount(UUID accountId, long afterId, int limit) {
        return jdbc.sql("""
                        select e.id, e.transaction_id, e.direction, e.amount, e.currency, t.created_at
                        from ledger_entry e
                        join ledger_transaction t on t.id = e.transaction_id
                        where e.account_id = :accountId and e.id > :afterId
                        order by e.id
                        limit :limit
                        """)
                .param("accountId", accountId)
                .param("afterId", afterId)
                .param("limit", limit)
                .query((rs, rowNum) -> new AccountEntry(
                        rs.getLong("id"),
                        rs.getObject("transaction_id", UUID.class),
                        Direction.valueOf(rs.getString("direction")),
                        rs.getLong("amount"),
                        rs.getString("currency"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    private List<Entry> entriesOf(UUID transactionId) {
        return jdbc.sql("select id, account_id, direction, amount, currency from ledger_entry where transaction_id = :id order by id")
                .param("id", transactionId)
                .query(ENTRY_MAPPER)
                .list();
    }
}
