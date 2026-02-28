package com.bavodaniels.ratelimit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for rate limiting functionality.
 * Allows enabling/disabling rate limiting globally and for specific clients.
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /**
     * Whether rate limiting is enabled globally.
     * Default is true.
     */
    private boolean enabled = true;

    /**
     * Client-specific rate limit settings.
     */
    private Clients clients = new Clients();

    /**
     * Maximum wait time in milliseconds before throwing a RateLimitExceededException.
     * Default is 30000 (30 seconds).
     */
    private long maxWaitTimeMillis = 30000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Clients getClients() {
        return clients;
    }

    public void setClients(Clients clients) {
        this.clients = clients;
    }

    public long getMaxWaitTimeMillis() {
        return maxWaitTimeMillis;
    }

    public void setMaxWaitTimeMillis(long maxWaitTimeMillis) {
        this.maxWaitTimeMillis = maxWaitTimeMillis;
    }

    /**
     * Client-specific rate limit settings.
     */
    public static class Clients {

        /**
         * RestTemplate-specific settings.
         */
        private RestTemplate restTemplate = new RestTemplate();

        /**
         * RestClient-specific settings.
         */
        private RestClient restClient = new RestClient();

        /**
         * WebClient-specific settings.
         */
        private WebClient webClient = new WebClient();

        /**
         * HttpInterface (HttpServiceProxyFactory)-specific settings.
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
         * HttpInterface (HttpServiceProxyFactory) client settings.
         */
        public static class HttpInterface {

            /**
             * Whether rate limiting is enabled for HttpServiceProxyFactory beans.
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
