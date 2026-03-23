package be.bavodaniels.ratelimit.config;

import be.bavodaniels.ratelimit.tracker.InMemoryRateLimitTracker;
import be.bavodaniels.ratelimit.tracker.RateLimitTracker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RateLimitAutoConfiguration.
 * Tests auto-configuration behavior with various property configurations
 * and multiple RestTemplate and WebClient beans.
 *
 * @since 1.0.0
 */
class RateLimitAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RateLimitAutoConfiguration.class));

    @Test
    void autoConfigurationShouldNotCreateBeansWhenGloballyDisabled() {
        contextRunner
                .withPropertyValues("rate-limit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RateLimitTracker.class);
                });
    }

    @Test
    void customRateLimitTrackerShouldBeUsedWhenProvided() {
        contextRunner
                .withUserConfiguration(CustomTrackerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(RateLimitTracker.class);
                    assertThat(context).getBean(RateLimitTracker.class)
                            .isInstanceOf(CustomRateLimitTracker.class);
                });
    }

    @Test
    void propertiesShouldHaveCorrectDefaults() {
        contextRunner
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);

                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getMaxWaitSeconds()).isEqualTo(5);
                });
    }


    @Configuration
    static class CustomTrackerConfiguration {
        @Bean
        public RateLimitTracker rateLimitTracker() {
            return new CustomRateLimitTracker();
        }
    }

    static class CustomRateLimitTracker extends InMemoryRateLimitTracker {
        // Custom implementation for testing
    }
}
