package com.hic.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Blocks production boot when secrets still use committed/dev defaults.
 */
@Component
public class ProductionSecretsValidator implements ApplicationRunner {

    static final String DEFAULT_JWT =
            "HicCentralJwtSecretKey2024AzerbaijanHRERPSystemSecretKeyMustBe64CharsLong!!";
    static final String DEFAULT_ENCRYPTION = "HicEncryptKey32CharactersLongKey!";

    private final Environment environment;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${encryption.secret-key}")
    private String encryptionSecret;

    @Value("${isapi.api-key:}")
    private String isapiApiKey;

    public ProductionSecretsValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProd()) {
            return;
        }
        if (DEFAULT_JWT.equals(jwtSecret) || jwtSecret == null || jwtSecret.isBlank() || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "Refusing to start with default/weak JWT_SECRET in prod. Set a strong JWT_SECRET env var.");
        }
        if (DEFAULT_ENCRYPTION.equals(encryptionSecret) || encryptionSecret == null || encryptionSecret.isBlank()) {
            throw new IllegalStateException(
                    "Refusing to start with default ENCRYPTION_SECRET_KEY in prod. Set ENCRYPTION_SECRET_KEY.");
        }
        if (isapiApiKey == null || isapiApiKey.isBlank()) {
            throw new IllegalStateException(
                    "Refusing to start without ISAPI_API_KEY in prod. Set a shared secret for backend↔isapi.");
        }
    }

    private boolean isProd() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
