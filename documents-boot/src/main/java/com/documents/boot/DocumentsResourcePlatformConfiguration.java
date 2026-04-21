package com.documents.boot;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

import io.github.jho951.platform.resource.autoconfigure.OperationalResourcePolicyEnforcer;
import io.github.jho951.platform.resource.autoconfigure.PlatformResourceProperties;
import io.github.jho951.platform.resource.core.InMemoryResourceCatalog;
import io.github.jho951.platform.resource.core.InMemoryResourceLifecycleOutbox;
import io.github.jho951.platform.resource.spi.ResourceCatalog;
import io.github.jho951.platform.resource.spi.ResourceContentStore;
import io.github.jho951.platform.resource.spi.ResourceLifecycleOutbox;
import io.github.jho951.platform.resource.spi.ResourceWriteRequest;
import io.github.jho951.platform.resource.spi.StoredContent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "platform.resource", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({
    DocumentsResourcePlatformConfiguration.DocumentsResourceStorageProperties.class,
    PlatformResourceProperties.class
})
public class DocumentsResourcePlatformConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ResourceContentStore documentsResourceContentStore(DocumentsResourceStorageProperties properties) {
        return new FileSystemResourceContentStore(properties.rootDirectory());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.resource", name = "mode", havingValue = "local")
    public ResourceCatalog documentsLocalResourceCatalog() {
        return new InMemoryResourceCatalog();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.resource", name = "mode", havingValue = "local")
    public ResourceLifecycleOutbox documentsLocalResourceLifecycleOutbox() {
        return new InMemoryResourceLifecycleOutbox();
    }

    @Bean("operationalProfileResolver")
    @ConditionalOnMissingBean(name = "operationalProfileResolver")
    public SharedOperationalProfileResolver operationalProfileResolver() {
        return new SharedOperationalProfileResolver();
    }

    @ConfigurationProperties(prefix = "platform.resource.storage")
    public record DocumentsResourceStorageProperties(Path rootDirectory) {
        public DocumentsResourceStorageProperties {
            if (rootDirectory == null) {
                rootDirectory = Path.of(System.getProperty("java.io.tmpdir"), "documents-platform-resources");
            }
        }
    }

    public static final class SharedOperationalProfileResolver
        implements io.github.jho951.platform.resource.autoconfigure.OperationalProfileResolver,
        io.github.jho951.platform.governance.api.OperationalProfileResolver,
        io.github.jho951.platform.policy.api.OperationalProfileResolver {

        @Override
        public boolean isProduction(Collection<String> activeProfiles, Collection<String> productionProfiles) {
            if (activeProfiles == null || productionProfiles == null) {
                return false;
            }

            for (String activeProfile : activeProfiles) {
                for (String productionProfile : productionProfiles) {
                    if (productionProfile != null && productionProfile.equalsIgnoreCase(activeProfile)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static final class FileSystemResourceContentStore implements ResourceContentStore {
        private final Path rootDirectory;

        private FileSystemResourceContentStore(Path rootDirectory) {
            this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
        }

        @Override
        public StoredContent store(ResourceWriteRequest request) {
            try {
                Files.createDirectories(rootDirectory);
                String id = UUID.randomUUID() + extension(request.originalName());
                Path target = resolve(id);
                try (InputStream input = request.input()) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return new StoredContent(
                    id,
                    request.originalName(),
                    request.contentType(),
                    Files.size(target)
                );
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to store platform resource content", ex);
            }
        }

        @Override
        public InputStream open(String storageFileId) {
            try {
                return Files.newInputStream(resolve(storageFileId));
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to open platform resource content", ex);
            }
        }

        @Override
        public void delete(String storageFileId) {
            try {
                Files.deleteIfExists(resolve(storageFileId));
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to delete platform resource content", ex);
            }
        }

        private Path resolve(String storageFileId) {
            Path resolved = rootDirectory.resolve(storageFileId).normalize();
            if (!resolved.startsWith(rootDirectory)) {
                throw new IllegalArgumentException("Invalid storage file id");
            }
            return resolved;
        }

        private static String extension(String originalName) {
            if (originalName == null || originalName.isBlank()) {
                return "";
            }
            String normalized = originalName.toLowerCase(Locale.ROOT);
            int dot = normalized.lastIndexOf('.');
            if (dot < 0 || dot == normalized.length() - 1) {
                return "";
            }
            String ext = normalized.substring(dot);
            return ext.matches("\\.[a-z0-9]{1,12}") ? ext : "";
        }
    }
}
