package com.documents.boot;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import io.github.jho951.platform.resource.spi.ResourceContentStore;
import io.github.jho951.platform.resource.spi.ResourceWriteRequest;
import io.github.jho951.platform.resource.spi.StoredContent;
import io.github.jho951.platform.security.ratelimit.PlatformRateLimitPort;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
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

        StoredContent storedContent = contentStore.store(
            new ResourceWriteRequest(
                "snapshot.json",
                "application/json",
                null,
                java.util.Map.of("kind", "document-snapshot"),
                new ByteArrayInputStream("{\"ok\":true}".getBytes(StandardCharsets.UTF_8))
            )
        );

        assertNotNull(storedContent.id());

        try (InputStream input = contentStore.open(storedContent.id())) {
            assertNotNull(input);
            assertTrue(input.readAllBytes().length > 0);
        } catch (java.io.IOException ex) {
            throw new RuntimeException(ex);
        }

        contentStore.delete(storedContent.id());
        assertThrows(IllegalStateException.class, () -> contentStore.open(storedContent.id()));
    }
}
