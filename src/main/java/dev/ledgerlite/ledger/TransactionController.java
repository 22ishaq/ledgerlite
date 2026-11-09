package dev.ledgerlite.ledger;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.ledgerlite.security.ApiClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/v1/transactions")
public class TransactionController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";

    private final LedgerService ledger;

    public TransactionController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @PostMapping
    ResponseEntity<LedgerTransaction> post(@AuthenticationPrincipal ApiClient client,
                                           @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 255) String idempotencyKey,
                                           @Valid @RequestBody PostTransactionRequest request) {
        return respond(ledger.post(client.id(), idempotencyKey, request));
    }

    @GetMapping("/{transactionId}")
    LedgerTransaction get(@AuthenticationPrincipal ApiClient client, @PathVariable UUID transactionId) {
        return ledger.get(client.id(), transactionId);
    }

    static ResponseEntity<LedgerTransaction> respond(PostingResult result) {
        LedgerTransaction transaction = result.transaction();
        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAYED, "true").body(transaction);
        }
        return ResponseEntity.created(URI.create("/v1/transactions/" + transaction.id())).body(transaction);
    }
}
