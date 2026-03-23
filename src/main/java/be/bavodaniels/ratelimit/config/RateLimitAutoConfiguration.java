package be.bavodaniels.ratelimit.config;

import be.bavodaniels.ratelimit.tracker.InMemoryRateLimitTracker;
import be.bavodaniels.ratelimit.tracker.RateLimitTracker;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for rate limiting functionality.
 * This configuration provides the default rate limit tracker bean.
 *
 * <p>Interceptors and filters must be manually added to individual HTTP clients.
 *
 * <p>Can be controlled via properties:
 * <ul>
 *   <li>rate-limit.enabled - Global enable/disable (default: true)</li>
 *   <li>rate-limit.max-wait-time-millis - Maximum wait time before throwing exception (default: 5000)</li>
 * </ul>
 *
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitAutoConfiguration {

    /**
     * Creates a default RateLimitTracker bean if none is already defined.
     * Uses an in-memory implementation suitable for single-instance deployments.
     *
     * @return a new InMemoryRateLimitTracker instance
     */
    @Bean
    @ConditionalOnMissingBean(RateLimitTracker.class)
    public RateLimitTracker rateLimitTracker() {
        return new InMemoryRateLimitTracker();
    }
}
