package dev.ledgerlite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Auth is API keys only, so skip Boot's default in-memory user and generated password.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class LedgerLiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerLiteApplication.class, args);
    }
}
