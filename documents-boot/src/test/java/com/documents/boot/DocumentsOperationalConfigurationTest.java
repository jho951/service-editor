package com.documents.boot;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jho951.platform.resource.spi.ResourceContentStore;
import io.github.jho951.platform.security.ratelimit.PlatformRateLimitPort;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.StringRedisTemplate;

class DocumentsOperationalConfigurationTest {

    @TempDir
    Path tempDir;

    @Test
    void productionRateLimiterExposesPlatformPort() {
        DocumentsPlatformOperationalConfiguration configuration =
            new DocumentsPlatformOperationalConfiguration();

        PlatformRateLimitPort rateLimitPort = configuration.platformSecurityRateLimiter(
            new StringRedisTemplate(),
            "platform-security:rate-limit:editor-service:"
        );

        assertNotNull(rateLimitPort);
    }

    @Test
    void productionResourceStoreUsesConfiguredRootDirectory() {
        DocumentsResourcePlatformConfiguration configuration =
            new DocumentsResourcePlatformConfiguration();
        ResourceContentStore contentStore = configuration.platformResourceContentStore(tempDir, true);

        assertNotNull(contentStore);
        assertTrue(tempDir.toFile().exists());
    }
}
