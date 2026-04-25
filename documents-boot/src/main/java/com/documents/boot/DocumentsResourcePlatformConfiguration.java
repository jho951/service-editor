package com.documents.boot;

import io.github.jho951.platform.resource.spi.ResourceContentStore;
import io.github.jho951.platform.resource.spi.ResourceWriteRequest;
import io.github.jho951.platform.resource.spi.StoredContent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@ConditionalOnProperty(prefix = "platform.resource", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DocumentsResourcePlatformConfiguration {

    @Bean("operationalProfileResolver")
    @ConditionalOnMissingBean(name = "operationalProfileResolver")
    public SharedOperationalProfileResolver operationalProfileResolver() {
        return new SharedOperationalProfileResolver();
    }

    @Bean
    @Profile({"prod", "production", "live"})
    @ConditionalOnMissingBean(ResourceContentStore.class)
    public ResourceContentStore platformResourceContentStore(
        @Value("${platform.resource.storage.root-directory:${java.io.tmpdir}/documents-platform-resources}")
        Path rootDirectory,
        @Value("${platform.resource.storage.create-directories-if-not-exist:true}")
        boolean createDirectoriesIfNotExist
    ) {
        return new LocalFilesystemResourceContentStore(rootDirectory, createDirectoriesIfNotExist);
    }

    public static final class SharedOperationalProfileResolver
        implements io.github.jho951.platform.resource.api.OperationalProfileResolver,
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

    static final class LocalFilesystemResourceContentStore implements ResourceContentStore {
        private final Path rootDirectory;

        LocalFilesystemResourceContentStore(Path rootDirectory, boolean createDirectoriesIfNotExist) {
            if (rootDirectory == null) {
                throw new IllegalArgumentException("rootDirectory must not be null");
            }
            this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
            initializeRootDirectory(createDirectoriesIfNotExist);
        }

        @Override
        public StoredContent store(ResourceWriteRequest request) {
            String storageFileId = UUID.randomUUID().toString();
            Path target = resolveSafe(storageFileId);
            try (InputStream input = request.input()) {
                Files.copy(input, target);
                return new StoredContent(
                    storageFileId,
                    request.originalName(),
                    request.contentType(),
                    Files.size(target)
                );
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to store resource content to " + target, ex);
            }
        }

        @Override
        public InputStream open(String storageFileId) {
            Path target = resolveExisting(storageFileId);
            try {
                return Files.newInputStream(target);
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to open resource content " + target, ex);
            }
        }

        @Override
        public void delete(String storageFileId) {
            Path target = resolveExisting(storageFileId);
            try {
                Files.deleteIfExists(target);
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to delete resource content " + target, ex);
            }
        }

        private void initializeRootDirectory(boolean createDirectoriesIfNotExist) {
            try {
                if (createDirectoriesIfNotExist && !Files.exists(rootDirectory)) {
                    Files.createDirectories(rootDirectory);
                }
                if (!Files.isDirectory(rootDirectory)) {
                    throw new IllegalStateException("Invalid resource storage root directory: " + rootDirectory);
                }
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to initialize resource storage root directory " + rootDirectory, ex);
            }
        }

        private Path resolveExisting(String storageFileId) {
            Path target = resolveSafe(storageFileId);
            if (!Files.exists(target)) {
                throw new IllegalStateException("Stored resource content not found: " + storageFileId);
            }
            return target;
        }

        private Path resolveSafe(String storageFileId) {
            if (storageFileId == null || storageFileId.isBlank()) {
                throw new IllegalArgumentException("storageFileId must not be blank");
            }
            if (storageFileId.contains("/") || storageFileId.contains("\\") || storageFileId.contains("..")) {
                throw new IllegalArgumentException("Invalid storageFileId: " + storageFileId);
            }
            Path target = rootDirectory.resolve(storageFileId).normalize();
            if (!target.startsWith(rootDirectory)) {
                throw new IllegalArgumentException("Invalid storageFileId: " + storageFileId);
            }
            return target;
        }
    }
}
