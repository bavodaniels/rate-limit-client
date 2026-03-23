package be.bavodaniels.ratelimit.config;

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
    void shouldBindAllPropertiesTogether() {
        contextRunner
                .withPropertyValues(
                        "rate-limit.enabled=false",
                        "rate-limit.max-wait-seconds=15",
                        "rate-limit.per-host=false"
                )
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);

                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.getMaxWaitSeconds()).isEqualTo(15);
                    assertThat(properties.isPerHost()).isFalse();
                });
    }

    @Test
    void shouldValidateMaxWaitSecondsIsPositive() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setMaxWaitSeconds(0);

        Set<ConstraintViolation<RateLimitProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("maxWaitTimeMillis") &&
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
                v.getPropertyPath().toString().equals("maxWaitTimeMillis")
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

        properties.setEnabled(false);
        assertThat(properties.isEnabled()).isFalse();

        properties.setMaxWaitSeconds(20);
        assertThat(properties.getMaxWaitSeconds()).isEqualTo(20);

        properties.setPerHost(false);
        assertThat(properties.isPerHost()).isFalse();
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

    @Configuration
    @EnableConfigurationProperties(RateLimitProperties.class)
    static class TestConfiguration {
    }
}
