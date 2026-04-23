package com.documents.boot;

import java.util.Collection;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "platform.resource", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DocumentsResourcePlatformConfiguration {

    @Bean("operationalProfileResolver")
    @ConditionalOnMissingBean(name = "operationalProfileResolver")
    public SharedOperationalProfileResolver operationalProfileResolver() {
        return new SharedOperationalProfileResolver();
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
