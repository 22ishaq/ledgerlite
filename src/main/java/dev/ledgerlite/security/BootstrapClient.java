package dev.ledgerlite.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Creates an API client from config on startup so there's something to call the API
 * with locally. There's no admin API for issuing keys yet.
 */
@Component
@ConditionalOnProperty("ledgerlite.bootstrap.api-key")
class BootstrapClient implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapClient.class);

    private final ApiClientRepository clients;
    private final String apiKey;
    private final String clientName;

    BootstrapClient(ApiClientRepository clients,
                    @Value("${ledgerlite.bootstrap.api-key}") String apiKey,
                    @Value("${ledgerlite.bootstrap.client-name:local-dev}") String clientName) {
        this.clients = clients;
        this.apiKey = apiKey;
        this.clientName = clientName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (apiKey.isBlank() || clients.findActiveByKeyHash(ApiKeys.hash(apiKey)).isPresent()) {
            return;
        }
        ApiClient client = clients.create(clientName, apiKey);
        log.info("Created bootstrap API client '{}' ({})", client.name(), client.id());
    }
}
