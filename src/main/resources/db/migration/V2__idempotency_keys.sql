create table idempotency_key (
    client_id       uuid         not null references api_client (id),
    idem_key        varchar(255) not null,
    request_hash    char(64)     not null,
    transaction_id  uuid references ledger_transaction (id),
    created_at      timestamptz  not null default now(),
    primary key (client_id, idem_key)
);
