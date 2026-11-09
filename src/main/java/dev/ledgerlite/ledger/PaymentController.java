package dev.ledgerlite.ledger;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import dev.ledgerlite.security.ApiClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
public class PaymentController {

    private final LedgerService ledger;

    public PaymentController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @PostMapping("/v1/payments")
    ResponseEntity<LedgerTransaction> pay(@AuthenticationPrincipal ApiClient client,
                                          @RequestHeader(TransactionController.IDEMPOTENCY_KEY) @NotBlank @Size(max = 255) String idempotencyKey,
                                          @Valid @RequestBody PaymentRequest payment) {
        return TransactionController.respond(ledger.pay(client.id(), idempotencyKey, payment));
    }
}
