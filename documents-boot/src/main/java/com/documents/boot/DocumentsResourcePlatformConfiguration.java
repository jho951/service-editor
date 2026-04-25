package com.documents.boot;

import com.filestorage.core.config.FileStorageConfig;
import com.filestorage.core.local.LocalFileStorage;
import io.github.jho951.platform.resource.filestorage.FileStorageContentStore;
import io.github.jho951.platform.resource.spi.ResourceContentStore;
import java.nio.file.Path;
import java.util.Collection;
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
        FileStorageConfig config = FileStorageConfig
            .builder(rootDirectory)
            .createDirectoriesIfNotExist(createDirectoriesIfNotExist)
            .build();
        return new FileStorageContentStore(new LocalFileStorage(config));
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
}
