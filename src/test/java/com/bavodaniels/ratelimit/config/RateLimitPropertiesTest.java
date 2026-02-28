package com.bavodaniels.ratelimit.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RateLimitProperties}.
 * Tests property binding, validation, and default values.
 *
 * @since 1.0.0
 */
class RateLimitPropertiesTest {

    private ApplicationContextRunner contextRunner;
    private Validator validator;

    @BeforeEach
    void setUp() {
        contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(TestConfiguration.class);
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldHaveCorrectDefaultValues() {
        contextRunner.run(context -> {
            RateLimitProperties properties = context.getBean(RateLimitProperties.class);

            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getMaxWaitSeconds()).isEqualTo(5);
            assertThat(properties.isPerHost()).isTrue();

            assertThat(properties.getClients()).isNotNull();
            assertThat(properties.getClients().getRestTemplate()).isNotNull();
            assertThat(properties.getClients().getRestTemplate().isEnabled()).isTrue();
            assertThat(properties.getClients().getWebClient()).isNotNull();
            assertThat(properties.getClients().getWebClient().isEnabled()).isTrue();
            assertThat(properties.getClients().getRestClient()).isNotNull();
            assertThat(properties.getClients().getRestClient().isEnabled()).isTrue();
            assertThat(properties.getClients().getHttpInterface()).isNotNull();
            assertThat(properties.getClients().getHttpInterface().isEnabled()).isTrue();
        });
    }

    @Test
    void shouldBindGlobalEnabledProperty() {
        contextRunner
                .withPropertyValues("rate-limit.enabled=false")
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);
                    assertThat(properties.isEnabled()).isFalse();
                });
    }

    @Test
    void shouldBindMaxWaitSecondsProperty() {
        contextRunner
                .withPropertyValues("rate-limit.max-wait-seconds=10")
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);
                    assertThat(properties.getMaxWaitSeconds()).isEqualTo(10);
                });
    }

    @Test
    void shouldBindPerHostProperty() {
        contextRunner
                .withPropertyValues("rate-limit.per-host=false")
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);
                    assertThat(properties.isPerHost()).isFalse();
                });
    }

    @Test
    void shouldBindRestTemplateEnabledProperty() {
        contextRunner
                .withPropertyValues("rate-limit.clients.rest-template.enabled=false")
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);
                    assertThat(properties.getClients().getRestTemplate().isEnabled()).isFalse();
                });
    }

    @Test
    void shouldBindWebClientEnabledProperty() {
        contextRunner
                .withPropertyValues("rate-limit.clients.web-client.enabled=false")
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);
                    assertThat(properties.getClients().getWebClient().isEnabled()).isFalse();
                });
    }

    @Test
    void shouldBindRestClientEnabledProperty() {
        contextRunner
                .withPropertyValues("rate-limit.clients.rest-client.enabled=false")
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);
                    assertThat(properties.getClients().getRestClient().isEnabled()).isFalse();
                });
    }

    @Test
    void shouldBindHttpInterfaceEnabledProperty() {
        contextRunner
                .withPropertyValues("rate-limit.clients.http-interface.enabled=false")
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);
                    assertThat(properties.getClients().getHttpInterface().isEnabled()).isFalse();
                });
    }

    @Test
    void shouldBindAllPropertiesTogether() {
        contextRunner
                .withPropertyValues(
                        "rate-limit.enabled=false",
                        "rate-limit.max-wait-seconds=15",
                        "rate-limit.per-host=false",
                        "rate-limit.clients.rest-template.enabled=false",
                        "rate-limit.clients.web-client.enabled=false",
                        "rate-limit.clients.rest-client.enabled=false",
                        "rate-limit.clients.http-interface.enabled=false"
                )
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);

                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.getMaxWaitSeconds()).isEqualTo(15);
                    assertThat(properties.isPerHost()).isFalse();
                    assertThat(properties.getClients().getRestTemplate().isEnabled()).isFalse();
                    assertThat(properties.getClients().getWebClient().isEnabled()).isFalse();
                    assertThat(properties.getClients().getRestClient().isEnabled()).isFalse();
                    assertThat(properties.getClients().getHttpInterface().isEnabled()).isFalse();
                });
    }

    @Test
    void shouldValidateMaxWaitSecondsIsPositive() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setMaxWaitSeconds(0);

        Set<ConstraintViolation<RateLimitProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("maxWaitSeconds") &&
                        v.getMessage().contains("must be greater than 0")
        );
    }

    @Test
    void shouldValidateMaxWaitSecondsIsPositiveWithNegativeValue() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setMaxWaitSeconds(-1);

        Set<ConstraintViolation<RateLimitProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("maxWaitSeconds")
        );
    }

    @Test
    void shouldPassValidationWithValidMaxWaitSeconds() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setMaxWaitSeconds(10);

        Set<ConstraintViolation<RateLimitProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldAllowSettingAndGettingAllProperties() {
        RateLimitProperties properties = new RateLimitProperties();

        // Test global properties
        properties.setEnabled(false);
        assertThat(properties.isEnabled()).isFalse();

        properties.setMaxWaitSeconds(20);
        assertThat(properties.getMaxWaitSeconds()).isEqualTo(20);

        properties.setPerHost(false);
        assertThat(properties.isPerHost()).isFalse();

        // Test client properties
        RateLimitProperties.Clients clients = new RateLimitProperties.Clients();

        RateLimitProperties.Clients.RestTemplate restTemplate = new RateLimitProperties.Clients.RestTemplate();
        restTemplate.setEnabled(false);
        clients.setRestTemplate(restTemplate);
        assertThat(clients.getRestTemplate().isEnabled()).isFalse();

        RateLimitProperties.Clients.WebClient webClient = new RateLimitProperties.Clients.WebClient();
        webClient.setEnabled(false);
        clients.setWebClient(webClient);
        assertThat(clients.getWebClient().isEnabled()).isFalse();

        RateLimitProperties.Clients.RestClient restClient = new RateLimitProperties.Clients.RestClient();
        restClient.setEnabled(false);
        clients.setRestClient(restClient);
        assertThat(clients.getRestClient().isEnabled()).isFalse();

        RateLimitProperties.Clients.HttpInterface httpInterface = new RateLimitProperties.Clients.HttpInterface();
        httpInterface.setEnabled(false);
        clients.setHttpInterface(httpInterface);
        assertThat(clients.getHttpInterface().isEnabled()).isFalse();

        properties.setClients(clients);
        assertThat(properties.getClients()).isEqualTo(clients);
    }

    @Test
    void shouldSupportKebabCaseAndCamelCasePropertyNames() {
        contextRunner
                .withPropertyValues(
                        "rate-limit.maxWaitSeconds=7",  // camelCase
                        "rate-limit.per-host=false"     // kebab-case
                )
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);
                    assertThat(properties.getMaxWaitSeconds()).isEqualTo(7);
                    assertThat(properties.isPerHost()).isFalse();
                });
    }

    @Test
    void shouldHandlePartialClientConfiguration() {
        contextRunner
                .withPropertyValues(
                        "rate-limit.clients.rest-template.enabled=false"
                        // Other clients should use defaults
                )
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);
                    assertThat(properties.getClients().getRestTemplate().isEnabled()).isFalse();
                    assertThat(properties.getClients().getWebClient().isEnabled()).isTrue();
                    assertThat(properties.getClients().getRestClient().isEnabled()).isTrue();
                    assertThat(properties.getClients().getHttpInterface().isEnabled()).isTrue();
                });
    }

    @Configuration
    @EnableConfigurationProperties(RateLimitProperties.class)
    static class TestConfiguration {
    }
}
