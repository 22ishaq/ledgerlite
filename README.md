# LedgerLite

[![CI](https://github.com/22ishaq/ledgerlite/actions/workflows/ci.yml/badge.svg)](https://github.com/22ishaq/ledgerlite/actions/workflows/ci.yml)
[![CodeQL](https://github.com/22ishaq/ledgerlite/actions/workflows/codeql.yml/badge.svg)](https://github.com/22ishaq/ledgerlite/actions/workflows/codeql.yml)

A small double-entry ledger API for moving money between accounts. Java 21, Spring Boot 4, PostgreSQL.

Still a work in progress, see the [roadmap](#roadmap).

## What it does

- Accounts hold a balance in minor units (pence, cents). Money is a `long`, never a float.
- Every transaction is a list of debit and credit entries, and debits have to equal credits in each currency.
- `POST /v1/payments` covers the simple "move X from A to B" case. `POST /v1/transactions` takes any number of legs for things like fees and split payments.
- Every write needs an `Idempotency-Key` header. Retrying with the same key gives back the original transaction instead of posting it twice.
- Clients authenticate with an API key and can only see and move money in their own accounts.

## Running it

```bash
docker compose up --build
```

This starts Postgres and the API on `localhost:8080`, with a dev client whose key is `ll_local_dev_key`.

```bash
KEY="Authorization: Bearer ll_local_dev_key"

# a settlement account to fund from (debit-normal, like a bank account on your books)
curl -s -H "$KEY" -H 'Content-Type: application/json' localhost:8080/v1/accounts \
  -d '{"name": "Settlement", "currency": "GBP", "normalBalance": "DEBIT"}'

# a customer wallet (credit-normal: it's money you owe the customer)
curl -s -H "$KEY" -H 'Content-Type: application/json' localhost:8080/v1/accounts \
  -d '{"name": "Alice", "currency": "GBP", "normalBalance": "CREDIT"}'

# fund the wallet with £15.00
curl -s -H "$KEY" -H 'Idempotency-Key: fund-alice-1' -H 'Content-Type: application/json' \
  localhost:8080/v1/payments \
  -d '{"fromAccountId": "<settlement id>", "toAccountId": "<alice id>", "amount": 1500, "currency": "GBP"}'
```

Run the last command again and you get `200` with `Idempotent-Replayed: true` and the same transaction id. The balance only moves once.

## Tests

The integration tests run against a real Postgres, not H2, because the locking and `on conflict` behaviour is the whole point. `docker compose up -d db` creates a `ledgerlite_test` database for them.

```bash
./mvnw verify
```

That runs the unit tests, the integration tests (`*IT`) and SpotBugs. Point the tests at another database with `LEDGERLITE_TEST_DB_URL`, `LEDGERLITE_TEST_DB_USER` and `LEDGERLITE_TEST_DB_PASSWORD`.

After every integration test, `IntegrationTest` checks that each account's stored balance still matches the sum of its entries and that debits equal credits overall. A bug in the posting logic shows up there even if the test itself didn't look for it.

## API

| Method | Path | |
|---|---|---|
| `POST` | `/v1/accounts` | open an account |
| `GET` | `/v1/accounts` | list your accounts |
| `GET` | `/v1/accounts/{id}` | account with current balance |
| `GET` | `/v1/accounts/{id}/entries?after=&limit=` | statement, cursor paginated |
| `POST` | `/v1/payments` | two-leg transfer, needs `Idempotency-Key` |
| `POST` | `/v1/transactions` | multi-leg transaction, needs `Idempotency-Key` |
| `GET` | `/v1/transactions/{id}` | transaction with its entries |
| `GET` | `/actuator/health` | public |

Errors use [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details with an extra `code` field:

```json
{
  "type": "about:blank",
  "title": "Unprocessable Content",
  "status": 422,
  "detail": "Account 2f7c3b66-... has insufficient funds",
  "code": "insufficient_funds"
}
```

Codes: `invalid_request`, `unauthorized`, `account_not_found`, `transaction_not_found`, `insufficient_funds`, `unbalanced_transaction`, `currency_mismatch`, `same_account`, `unknown_currency`, `idempotency_key_reused`, `amount_out_of_range`.

## Design notes

**Balances.** Each account stores its balance, which is updated in the same database transaction as the entries. Summing entries on every read would be simpler but gets slow, and the invariant check in the tests catches drift.

**Normal balance.** Accounts are either debit-normal or credit-normal, and the balance is expressed in that direction. A customer wallet is credit-normal, so crediting it increases it. That keeps balances positive in the common case without the API having to know about asset and liability account types.

**Concurrency.** Posting locks every account involved with `select ... for update`, always in id order. Two payments touching the same accounts queue up instead of deadlocking, and the balance check runs on locked rows, so concurrent payments can't overdraw an account. `ConcurrencyIT` fires 25 payments at a wallet that can only afford 10 and checks exactly 10 succeed.

**Idempotency.** The key is inserted with `on conflict do nothing` in the same transaction as the posting. If a duplicate arrives while the first request is still running, Postgres makes the second insert wait on the unique index until the first commits. Then the second request sees the key and replays the result. If the first request fails, for example on insufficient funds, the key rolls back with it, so the client can fix the problem and retry with the same key. Reusing a key with a different body returns `422 idempotency_key_reused`. Keys are scoped per client.

**Append-only entries.** A trigger rejects `update` and `delete` on `ledger_entry`. Corrections should be reversing transactions (not built yet).

**API keys.** Only the SHA-256 of a key is stored. Keys are 32 random bytes, so a slow password hash wouldn't add anything, and a deterministic hash means a client can be looked up directly by its key.

## CI

Every push and pull request runs:

- `./mvnw verify` against a Postgres service container: unit tests, integration tests and SpotBugs
- CodeQL with the `security-extended` queries
- a Docker image build
- on pull requests, GitHub's dependency review, which fails on any new dependency with a high or critical advisory

Every action is pinned to a full commit SHA. Dependabot keeps Maven, the action pins and the base images up to date.

## Roadmap

- [x] accounts, payments, multi-leg transactions
- [x] idempotency keys
- [x] API key auth and per-client isolation
- [x] append-only entries
- [ ] reversals (`POST /v1/transactions/{id}/reverse`)
- [ ] expire idempotency keys after 24h
- [ ] admin endpoint for issuing and rotating API keys
- [ ] OpenAPI spec
- [ ] per-client rate limiting
