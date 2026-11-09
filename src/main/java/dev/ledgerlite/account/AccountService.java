package dev.ledgerlite.account;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.ledgerlite.common.LedgerException;

@Service
public class AccountService {

    private static final int MAX_LISTED = 100;

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public Account open(UUID clientId, CreateAccountRequest request) {
        if (!isIsoCurrency(request.currency())) {
            throw LedgerException.unknownCurrency(request.currency());
        }
        return accounts.insert(clientId, request.name().trim(), request.currency(), request.normalBalance(),
                Boolean.TRUE.equals(request.allowNegative()));
    }

    public Account get(UUID clientId, UUID accountId) {
        return accounts.findById(clientId, accountId)
                .orElseThrow(() -> LedgerException.accountNotFound(accountId));
    }

    // TODO: cursor pagination like the entries endpoint once anyone has more than 100 accounts
    public List<Account> list(UUID clientId) {
        return accounts.findAll(clientId, MAX_LISTED);
    }

    private static boolean isIsoCurrency(String code) {
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
