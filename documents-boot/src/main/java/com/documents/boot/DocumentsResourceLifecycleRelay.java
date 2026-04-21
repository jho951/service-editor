package com.documents.boot;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import io.github.jho951.platform.resource.api.ResourceLifecycleOutboxEntry;
import io.github.jho951.platform.resource.spi.ResourceLifecycleOutbox;
import io.github.jho951.platform.resource.spi.ResourceLifecyclePublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DocumentsResourceLifecycleRelay {

    private final ResourceLifecycleOutbox resourceLifecycleOutbox;
    private final ObjectProvider<ResourceLifecyclePublisher> lifecyclePublishers;
    private final Clock clock;

    public DocumentsResourceLifecycleRelay(
        ResourceLifecycleOutbox resourceLifecycleOutbox,
        ObjectProvider<ResourceLifecyclePublisher> lifecyclePublishers,
        Clock clock
    ) {
        this.resourceLifecycleOutbox = resourceLifecycleOutbox;
        this.lifecyclePublishers = lifecyclePublishers;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${documents.resource.outbox.relay-delay-ms:1000}")
    public void relay() {
        List<ResourceLifecyclePublisher> publishers = lifecyclePublishers.orderedStream().toList();
        if (publishers.isEmpty()) {
            return;
        }

        for (ResourceLifecycleOutboxEntry entry : resourceLifecycleOutbox.pending(100)) {
            try {
                for (ResourceLifecyclePublisher publisher : publishers) {
                    publisher.publish(entry.event());
                }
                resourceLifecycleOutbox.markPublished(entry.id(), Instant.now(clock));
            } catch (RuntimeException ex) {
                resourceLifecycleOutbox.markFailed(entry.id(), ex.getMessage(), Instant.now(clock));
            }
        }
    }
}
