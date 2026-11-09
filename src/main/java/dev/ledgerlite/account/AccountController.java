package dev.ledgerlite.account;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.ledgerlite.security.ApiClient;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    ResponseEntity<AccountResponse> open(@AuthenticationPrincipal ApiClient client,
                                         @Valid @RequestBody CreateAccountRequest request) {
        Account account = accounts.open(client.id(), request);
        return ResponseEntity.created(URI.create("/v1/accounts/" + account.id()))
                .body(AccountResponse.from(account));
    }

    @GetMapping
    List<AccountResponse> list(@AuthenticationPrincipal ApiClient client) {
        return accounts.list(client.id()).stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{accountId}")
    AccountResponse get(@AuthenticationPrincipal ApiClient client, @PathVariable UUID accountId) {
        return AccountResponse.from(accounts.get(client.id(), accountId));
    }
}
