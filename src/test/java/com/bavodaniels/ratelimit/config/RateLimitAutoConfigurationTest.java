package com.bavodaniels.ratelimit.config;

import com.bavodaniels.ratelimit.interceptor.RestTemplateRateLimitInterceptor;
import com.bavodaniels.ratelimit.tracker.InMemoryRateLimitTracker;
import com.bavodaniels.ratelimit.tracker.RateLimitTracker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RateLimitAutoConfiguration.
 * Tests auto-configuration behavior with various property configurations
 * and multiple RestTemplate beans.
 *
 * @since 1.0.0
 */
class RateLimitAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RateLimitAutoConfiguration.class));

    @Test
    void autoConfigurationShouldCreateBeansWhenEnabled() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(RateLimitTracker.class);
                    assertThat(context).hasSingleBean(BeanPostProcessor.class);
                    assertThat(context).getBean(RateLimitTracker.class)
                            .isInstanceOf(InMemoryRateLimitTracker.class);
                });
    }

    @Test
    void autoConfigurationShouldNotCreateBeansWhenGloballyDisabled() {
        contextRunner
                .withPropertyValues("rate-limit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RateLimitTracker.class);
                    assertThat(context).doesNotHaveBean(BeanPostProcessor.class);
                });
    }

    @Test
    void autoConfigurationShouldNotCreateBeanPostProcessorWhenRestTemplateDisabled() {
        contextRunner
                .withPropertyValues("rate-limit.clients.rest-template.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(RateLimitTracker.class);
                    assertThat(context).doesNotHaveBean(BeanPostProcessor.class);
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
    void beanPostProcessorShouldAddInterceptorToRestTemplate() {
        contextRunner
                .withUserConfiguration(SingleRestTemplateConfiguration.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);

                    assertThat(restTemplate.getInterceptors())
                            .hasSize(1)
                            .first()
                            .isInstanceOf(RestTemplateRateLimitInterceptor.class);
                });
    }

    @Test
    void beanPostProcessorShouldNotBreakExistingInterceptors() {
        contextRunner
                .withUserConfiguration(RestTemplateWithInterceptorConfiguration.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);

                    assertThat(restTemplate.getInterceptors())
                            .hasSize(2)
                            .anyMatch(interceptor -> interceptor instanceof RestTemplateRateLimitInterceptor);
                });
    }

    @Test
    void maxWaitTimePropertyShouldBeRespected() {
        contextRunner
                .withPropertyValues("rate-limit.max-wait-time-millis=60000")
                .withUserConfiguration(SingleRestTemplateConfiguration.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);

                    RestTemplateRateLimitInterceptor interceptor = (RestTemplateRateLimitInterceptor)
                            restTemplate.getInterceptors().get(0);
                    assertThat(interceptor.getMaxWaitTimeMillis()).isEqualTo(60000);
                });
    }

    @Test
    void propertiesShouldHaveCorrectDefaults() {
        contextRunner
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);

                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getMaxWaitTimeMillis()).isEqualTo(30000);
                    assertThat(properties.getClients().getRestTemplate().isEnabled()).isTrue();
                });
    }

    @Test
    void multipleRestTemplateBeansShouldAllHaveInterceptor() {
        contextRunner
                .withUserConfiguration(MultipleRestTemplatesConfiguration.class)
                .run(context -> {
                    RestTemplate restTemplate1 = (RestTemplate) context.getBean("restTemplate1");
                    RestTemplate restTemplate2 = (RestTemplate) context.getBean("restTemplate2");

                    // Both should have the rate limit interceptor
                    assertThat(restTemplate1.getInterceptors())
                            .anyMatch(interceptor -> interceptor instanceof RestTemplateRateLimitInterceptor);
                    assertThat(restTemplate2.getInterceptors())
                            .anyMatch(interceptor -> interceptor instanceof RestTemplateRateLimitInterceptor);
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

    @Configuration
    static class SingleRestTemplateConfiguration {
        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }
    }

    @Configuration
    static class RestTemplateWithInterceptorConfiguration {
        @Bean
        public RestTemplate restTemplate() {
            RestTemplate restTemplate = new RestTemplate();
            // Add existing interceptor
            ClientHttpRequestInterceptor existingInterceptor = (request, body, execution) -> execution.execute(request, body);
            restTemplate.getInterceptors().add(existingInterceptor);
            return restTemplate;
        }
    }

    @Configuration
    static class MultipleRestTemplatesConfiguration {
        @Bean
        public RestTemplate restTemplate1() {
            return new RestTemplate();
        }

        @Bean
        public RestTemplate restTemplate2() {
            return new RestTemplate();
        }
    }
}
