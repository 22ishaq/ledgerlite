package dev.ledgerlite.account;

import dev.ledgerlite.common.Direction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3 letter currency code") String currency,
        @NotNull Direction normalBalance,
        Boolean allowNegative) {
}
