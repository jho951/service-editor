package com.documents.boot;

import io.github.jho951.platform.security.ratelimit.PlatformRateLimitDecision;
import io.github.jho951.platform.security.ratelimit.PlatformRateLimitKeyType;
import io.github.jho951.platform.security.ratelimit.PlatformRateLimitPort;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@Profile({"prod", "production", "live"})
public class DocumentsPlatformOperationalConfiguration {

    @Bean
    public PlatformRateLimitPort platformSecurityRateLimiter(
        StringRedisTemplate redisTemplate,
        @Value("${PLATFORM_SECURITY_RATE_LIMIT_REDIS_PREFIX:platform-security:rate-limit:editor-service:}")
        String keyPrefix
    ) {
        return new RedisFixedWindowPlatformRateLimitPort(redisTemplate, keyPrefix, Clock.systemUTC());
    }

    private static final class RedisFixedWindowPlatformRateLimitPort implements PlatformRateLimitPort {
        private final StringRedisTemplate redisTemplate;
        private final String keyPrefix;
        private final Clock clock;

        private RedisFixedWindowPlatformRateLimitPort(
            StringRedisTemplate redisTemplate,
            String keyPrefix,
            Clock clock
        ) {
            this.redisTemplate = redisTemplate;
            this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
            this.clock = clock;
        }

        @Override
        public PlatformRateLimitDecision evaluate(
            io.github.jho951.platform.security.ratelimit.PlatformRateLimitRequest request
        ) {
            long windowSeconds = Math.max(1L, request.windowSeconds());
            long nowSeconds = clock.instant().getEpochSecond();
            long windowIndex = nowSeconds / windowSeconds;
            long windowEndSeconds = (windowIndex + 1L) * windowSeconds;
            String redisKey = keyPrefix
                + keyTypeSegment(request.keyType())
                + ":"
                + request.key()
                + ":"
                + windowIndex;

            Long current = redisTemplate.opsForValue().increment(redisKey, request.permits());
            if (current == null) {
                return PlatformRateLimitDecision.deny(request.key(), "rate limit backend unavailable");
            }
            if (current == request.permits()) {
                redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds + 1L));
            }

            if (current <= request.limit()) {
                return PlatformRateLimitDecision.allow(request.key(), "within rate limit");
            }

            long retryAfterSeconds = Math.max(0L, windowEndSeconds - nowSeconds);
            return PlatformRateLimitDecision.deny(
                request.key(),
                "rate limit exceeded for " + request.key() + "; retry_after_seconds=" + retryAfterSeconds
            );
        }

        private String keyTypeSegment(PlatformRateLimitKeyType keyType) {
            return keyType == PlatformRateLimitKeyType.USER ? "user" : "ip";
        }
    }
}
