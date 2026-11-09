create table api_client (
    id          uuid primary key,
    name        varchar(100) not null,
    key_hash    char(64)     not null unique,
    active      boolean      not null default true,
    created_at  timestamptz  not null default now()
);

create table account (
    id              uuid primary key,
    client_id       uuid         not null references api_client (id),
    name            varchar(100) not null,
    currency        char(3)      not null,
    normal_balance  varchar(6)   not null check (normal_balance in ('DEBIT', 'CREDIT')),
    allow_negative  boolean      not null default false,
    -- minor units, in the account's normal direction
    balance         bigint       not null default 0,
    created_at      timestamptz  not null default now()
);

create index account_client_idx on account (client_id, created_at);

create table ledger_transaction (
    id            uuid primary key,
    client_id     uuid         not null references api_client (id),
    description   varchar(255),
    external_ref  varchar(100),
    created_at    timestamptz  not null default now()
);

create table ledger_entry (
    id              bigserial primary key,
    transaction_id  uuid       not null references ledger_transaction (id),
    account_id      uuid       not null references account (id),
    direction       varchar(6) not null check (direction in ('DEBIT', 'CREDIT')),
    amount          bigint     not null check (amount > 0),
    currency        char(3)    not null
);

create index ledger_entry_account_idx on ledger_entry (account_id, id);
create index ledger_entry_transaction_idx on ledger_entry (transaction_id);
