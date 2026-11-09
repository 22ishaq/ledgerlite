package dev.ledgerlite.ledger;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.ledgerlite.security.ApiClient;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

@RestController
public class EntryController {

    private final LedgerService ledger;

    public EntryController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @GetMapping("/v1/accounts/{accountId}/entries")
    EntryPage list(@AuthenticationPrincipal ApiClient client,
                   @PathVariable UUID accountId,
                   @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
                   @RequestParam(defaultValue = "0") @PositiveOrZero long after) {
        return ledger.entries(client.id(), accountId, after, limit);
    }
}
