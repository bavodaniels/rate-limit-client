package be.bavodaniels.ratelimit.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for rate limiting functionality.
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "rate-limit")
@Validated
public class RateLimitProperties {

    /**
     * Whether rate limiting is enabled globally.
     * Default is true.
     */
    private boolean enabled = true;

    /**
     * Maximum wait time in milliseconds before throwing a RateLimitExceededException.
     * Default is 5000 (5 seconds).
     */
    @Positive(message = "maxWaitTimeMillis must be greater than 0")
    private long maxWaitTimeMillis = 5000;

    /**
     * Whether rate limiting is applied per-host.
     * Default is true.
     */
    private boolean perHost = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getMaxWaitTimeMillis() {
        return maxWaitTimeMillis;
    }

    public void setMaxWaitTimeMillis(long maxWaitTimeMillis) {
        this.maxWaitTimeMillis = maxWaitTimeMillis;
    }

    public int getMaxWaitSeconds() {
        return (int) (maxWaitTimeMillis / 1000);
    }

    public void setMaxWaitSeconds(int maxWaitSeconds) {
        this.maxWaitTimeMillis = maxWaitSeconds * 1000L;
    }

    public boolean isPerHost() {
        return perHost;
    }

    public void setPerHost(boolean perHost) {
        this.perHost = perHost;
    }
}
