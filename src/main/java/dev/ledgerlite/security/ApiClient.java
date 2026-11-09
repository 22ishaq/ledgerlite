package dev.ledgerlite.security;

import java.util.UUID;

/** A caller of the API. Every account and transaction belongs to exactly one client. */
public record ApiClient(UUID id, String name) {
}
