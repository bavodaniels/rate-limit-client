package com.bavodaniels.ratelimit.config;

import com.bavodaniels.ratelimit.interceptor.RestTemplateRateLimitInterceptor;
import com.bavodaniels.ratelimit.tracker.InMemoryRateLimitTracker;
import com.bavodaniels.ratelimit.tracker.RateLimitTracker;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration for rate limiting functionality.
 * This configuration automatically sets up rate limit tracking and interceptors
 * for RestTemplate beans when the necessary classes are on the classpath.
 *
 * <p>Can be controlled via properties:
 * <ul>
 *   <li>rate-limit.enabled - Global enable/disable (default: true)</li>
 *   <li>rate-limit.clients.rest-template.enabled - RestTemplate-specific enable/disable (default: true)</li>
 *   <li>rate-limit.max-wait-time-millis - Maximum wait time before throwing exception (default: 30000)</li>
 * </ul>
 *
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(RestTemplate.class)
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

    /**
     * Creates a BeanPostProcessor that adds the rate limit interceptor to all RestTemplate beans.
     * This post-processor is ordered to run after other post-processors (Ordered.LOWEST_PRECEDENCE - 100)
     * to ensure it doesn't interfere with other configurations while still being applied before
     * most user-defined post-processors.
     *
     * @param rateLimitTracker the tracker to use for rate limiting
     * @param properties the rate limit configuration properties
     * @return a BeanPostProcessor that adds the rate limit interceptor
     */
    @Bean
    @ConditionalOnProperty(prefix = "rate-limit.clients.rest-template", name = "enabled", havingValue = "true", matchIfMissing = true)
    public static RestTemplateRateLimitBeanPostProcessor restTemplateRateLimitBeanPostProcessor(
            RateLimitTracker rateLimitTracker,
            RateLimitProperties properties) {
        return new RestTemplateRateLimitBeanPostProcessor(rateLimitTracker, properties);
    }

    /**
     * BeanPostProcessor that automatically adds the rate limit interceptor to all RestTemplate beans.
     */
    static class RestTemplateRateLimitBeanPostProcessor implements BeanPostProcessor, Ordered {

        private final RateLimitTracker rateLimitTracker;
        private final RateLimitProperties properties;

        public RestTemplateRateLimitBeanPostProcessor(RateLimitTracker rateLimitTracker, RateLimitProperties properties) {
            this.rateLimitTracker = rateLimitTracker;
            this.properties = properties;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof RestTemplate restTemplate) {
                RestTemplateRateLimitInterceptor interceptor = new RestTemplateRateLimitInterceptor(
                        rateLimitTracker,
                        properties.getMaxWaitTimeMillis()
                );

                // Add the interceptor to the existing interceptor list
                // This preserves any interceptors that were already configured
                restTemplate.getInterceptors().add(interceptor);
            }
            return bean;
        }

        @Override
        public int getOrder() {
            // Run after most other post-processors but before user-defined ones
            return Ordered.LOWEST_PRECEDENCE - 100;
        }
    }
}
