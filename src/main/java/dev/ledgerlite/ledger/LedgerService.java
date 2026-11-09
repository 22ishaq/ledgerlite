package dev.ledgerlite.ledger;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ledgerlite.account.Account;
import dev.ledgerlite.account.AccountRepository;
import dev.ledgerlite.common.LedgerException;

@Service
public class LedgerService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final IdempotencyStore idempotency;

    public LedgerService(AccountRepository accounts, TransactionRepository transactions, IdempotencyStore idempotency) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.idempotency = idempotency;
    }

    /**
     * Posts a balanced transaction. Everything happens in one database transaction, so
     * a failure (e.g. insufficient funds) also releases the idempotency key and the
     * client can retry with the same key once the problem is fixed.
     */
    @Transactional
    public PostingResult post(UUID clientId, String idempotencyKey, PostTransactionRequest request) {
        String fingerprint = request.fingerprint();
        Optional<IdempotencyRecord> previous = idempotency.claim(clientId, idempotencyKey, fingerprint);
        if (previous.isPresent()) {
            return replay(clientId, previous.get(), fingerprint);
        }

        TransactionRules.checkBalanced(request.entries());

        Set<UUID> accountIds = new LinkedHashSet<>();
        request.entries().forEach(entry -> accountIds.add(entry.accountId()));
        Map<UUID, Account> locked = accounts.lockAll(clientId, accountIds).stream()
                .collect(Collectors.toMap(Account::id, Function.identity()));
        for (UUID id : accountIds) {
            if (!locked.containsKey(id)) {
                throw LedgerException.accountNotFound(id);
            }
        }

        Map<UUID, Long> deltas = new HashMap<>();
        for (EntryRequest entry : request.entries()) {
            Account account = locked.get(entry.accountId());
            if (!account.currency().equals(entry.currency())) {
                throw LedgerException.currencyMismatch(account.id(), account.currency(), entry.currency());
            }
            deltas.merge(account.id(), account.signedAmount(entry.direction(), entry.amount()), Math::addExact);
        }

        Map<UUID, Long> newBalances = new HashMap<>();
        deltas.forEach((id, delta) -> {
            Account account = locked.get(id);
            long updated = Math.addExact(account.balance(), delta);
            // only block entries that take money out; an overdrawn account can still be paid into
            if (delta < 0 && updated < 0 && !account.allowNegative()) {
                throw LedgerException.insufficientFunds(id);
            }
            newBalances.put(id, updated);
        });

        LedgerTransaction transaction = transactions.insert(clientId, request);
        newBalances.forEach(accounts::updateBalance);
        idempotency.complete(clientId, idempotencyKey, transaction.id());
        return new PostingResult(transaction, false);
    }

    @Transactional
    public PostingResult pay(UUID clientId, String idempotencyKey, PaymentRequest payment) {
        if (payment.fromAccountId().equals(payment.toAccountId())) {
            throw LedgerException.sameAccount();
        }
        return post(clientId, idempotencyKey, payment.toTransaction());
    }

    @Transactional(readOnly = true)
    public LedgerTransaction get(UUID clientId, UUID transactionId) {
        return transactions.find(clientId, transactionId)
                .orElseThrow(() -> LedgerException.transactionNotFound(transactionId));
    }

    @Transactional(readOnly = true)
    public EntryPage entries(UUID clientId, UUID accountId, long afterId, int limit) {
        if (accounts.findById(clientId, accountId).isEmpty()) {
            throw LedgerException.accountNotFound(accountId);
        }
        // fetch one extra row so we know whether there's another page
        List<AccountEntry> rows = transactions.entriesForAccount(accountId, afterId, limit + 1);
        if (rows.size() <= limit) {
            return new EntryPage(rows, null);
        }
        List<AccountEntry> page = rows.subList(0, limit);
        return new EntryPage(List.copyOf(page), page.get(limit - 1).id());
    }

    private PostingResult replay(UUID clientId, IdempotencyRecord previous, String fingerprint) {
        if (!previous.requestHash().equals(fingerprint)) {
            throw LedgerException.idempotencyKeyReused();
        }
        LedgerTransaction transaction = transactions.find(clientId, previous.transactionId())
                .orElseThrow(() -> new IllegalStateException("Idempotency key " + previous.key() + " points at a missing transaction"));
        return new PostingResult(transaction, true);
    }
}
