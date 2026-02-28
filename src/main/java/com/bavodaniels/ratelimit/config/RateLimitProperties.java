package com.bavodaniels.ratelimit.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for rate limiting functionality.
 * Allows enabling/disabling rate limiting globally and for specific clients.
 *
 * <p>Example configuration:
 * <pre>
 * rate-limit:
 *   enabled: true
 *   max-wait-seconds: 5
 *   per-host: true
 *   clients:
 *     rest-template:
 *       enabled: true
 *     web-client:
 *       enabled: true
 *     rest-client:
 *       enabled: true
 *     http-interface:
 *       enabled: true
 * </pre>
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "rate-limit")
@Validated
public class RateLimitProperties {

    /**
     * Whether rate limiting is enabled globally.
     * When disabled, no rate limiting will be applied regardless of client-specific settings.
     * Default is true.
     */
    private boolean enabled = true;

    /**
     * Maximum time to wait (in seconds) before throwing a RateLimitExceededException.
     * Must be greater than 0.
     * Default is 5 seconds.
     */
    @Positive(message = "max-wait-seconds must be greater than 0")
    private int maxWaitSeconds = 5;

    /**
     * Whether to track rate limits per host (true) or globally (false).
     * When true, rate limits are tracked separately for each host.
     * When false, rate limits are tracked globally across all hosts.
     * Default is true.
     */
    private boolean perHost = true;

    /**
     * Client-specific rate limit settings.
     */
    @Valid
    private Clients clients = new Clients();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxWaitSeconds() {
        return maxWaitSeconds;
    }

    public void setMaxWaitSeconds(int maxWaitSeconds) {
        this.maxWaitSeconds = maxWaitSeconds;
    }

    public boolean isPerHost() {
        return perHost;
    }

    public void setPerHost(boolean perHost) {
        this.perHost = perHost;
    }

    public Clients getClients() {
        return clients;
    }

    public void setClients(Clients clients) {
        this.clients = clients;
    }

    /**
     * Client-specific rate limit settings.
     * Each client type can be independently enabled or disabled.
     */
    public static class Clients {

        /**
         * RestTemplate-specific settings.
         * Controls rate limiting for Spring's RestTemplate HTTP client.
         */
        private RestTemplate restTemplate = new RestTemplate();

        /**
         * RestClient-specific settings.
         * Controls rate limiting for Spring's RestClient HTTP client.
         */
        private RestClient restClient = new RestClient();

        /**
         * WebClient-specific settings.
         * Controls rate limiting for Spring WebFlux's WebClient HTTP client.
         */
        private WebClient webClient = new WebClient();

        /**
         * HTTP Interface-specific settings.
         * Controls rate limiting for Spring's HTTP Interface declarative clients.
         */
        private HttpInterface httpInterface = new HttpInterface();

        public RestTemplate getRestTemplate() {
            return restTemplate;
        }

        public void setRestTemplate(RestTemplate restTemplate) {
            this.restTemplate = restTemplate;
        }

        public RestClient getRestClient() {
            return restClient;
        }

        public void setRestClient(RestClient restClient) {
            this.restClient = restClient;
        }

        public WebClient getWebClient() {
            return webClient;
        }

        public void setWebClient(WebClient webClient) {
            this.webClient = webClient;
        }

        public HttpInterface getHttpInterface() {
            return httpInterface;
        }

        public void setHttpInterface(HttpInterface httpInterface) {
            this.httpInterface = httpInterface;
        }

        /**
         * RestTemplate client settings.
         */
        public static class RestTemplate {

            /**
             * Whether rate limiting is enabled for RestTemplate.
             * Default is true.
             */
            private boolean enabled = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        /**
         * RestClient client settings.
         */
        public static class RestClient {

            /**
             * Whether rate limiting is enabled for RestClient.
             * Default is true.
             */
            private boolean enabled = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        /**
         * WebClient client settings.
         */
        public static class WebClient {

            /**
             * Whether rate limiting is enabled for WebClient.
             * Default is true.
             */
            private boolean enabled = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        /**
         * HTTP Interface client settings.
         */
        public static class HttpInterface {

            /**
             * Whether rate limiting is enabled for HTTP Interface declarative clients.
             * Default is true.
             */
            private boolean enabled = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }
    }
}
