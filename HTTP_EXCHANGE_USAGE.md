# HTTP Interface (@HttpExchange) Support

This library provides transparent rate limiting support for Spring's `@HttpExchange` declarative HTTP interfaces when backed by RestClient, RestTemplate, or WebClient.

## How It Works

`HttpServiceProxyFactory` creates proxy implementations of `@HttpExchange` interfaces and can be backed by:
- `RestClient` (recommended for new applications)
- `RestTemplate` (legacy support)
- `WebClient` (reactive applications)

Since these backing clients are configured with rate limiting interceptors/filters through auto-configuration or manual setup, **rate limiting automatically works with @HttpExchange interfaces** without any additional configuration.

## Usage Examples

### 1. RestClient-Backed HTTP Interface (Recommended)

```java
// Define your HTTP interface
@HttpExchange("https://api.example.com")
public interface ApiClient {

    @GetExchange("/users/{id}")
    User getUser(@PathVariable String id);

    @PostExchange("/users")
    User createUser(@RequestBody User user);

    @PutExchange("/users/{id}")
    User updateUser(@PathVariable String id, @RequestBody User user);

    @DeleteExchange("/users/{id}")
    void deleteUser(@PathVariable String id);
}

// Configure with RestClient (rate limiting auto-configured)
@Configuration
public class ApiConfig {

    @Bean
    public ApiClient apiClient(RestClient.Builder restClientBuilder) {
        RestClient restClient = restClientBuilder
                .baseUrl("https://api.example.com")
                .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClient.RestClientAdapter.create(restClient))
                .build();

        return factory.createClient(ApiClient.class);
    }
}
```

### 2. RestTemplate-Backed HTTP Interface

```java
@Configuration
public class ApiConfig {

    @Bean
    public ApiClient apiClient(RestTemplate restTemplate) {
        // RestTemplate already has rate limiting interceptor via auto-configuration
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestTemplateAdapter.create(restTemplate))
                .build();

        return factory.createClient(ApiClient.class);
    }
}
```

### 3. WebClient-Backed HTTP Interface (Reactive)

```java
@Configuration
public class ApiConfig {

    @Bean
    public ApiClient apiClient(WebClient.Builder webClientBuilder) {
        WebClient webClient = webClientBuilder
                .baseUrl("https://api.example.com")
                .build();

        // WebClient already has rate limiting filter via auto-configuration
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(webClient))
                .build();

        return factory.createClient(ApiClient.class);
    }
}
```

## Supported Annotations

All Spring Web Service annotations are supported:
- `@HttpExchange` - Base annotation
- `@GetExchange` - HTTP GET requests
- `@PostExchange` - HTTP POST requests
- `@PutExchange` - HTTP PUT requests
- `@PatchExchange` - HTTP PATCH requests
- `@DeleteExchange` - HTTP DELETE requests

## Rate Limiting Behavior

When using HTTP interfaces:

1. **Pre-request**: The rate limit interceptor/filter checks the current rate limit state for the target host
2. **Waiting**: If rate limited, the request will wait (blocking for RestClient/RestTemplate, non-blocking for WebClient)
3. **Threshold**: If the wait time exceeds the configured threshold (default 30s), a `RateLimitExceededException` is thrown
4. **Post-response**: Rate limit headers from the response are parsed and tracked automatically

## Configuration

Rate limiting for HTTP interfaces uses the same configuration as the underlying client:

```properties
# Enable/disable rate limiting globally
rate-limit.enabled=true

# Configure maximum wait time (milliseconds)
rate-limit.max-wait-time-millis=30000

# Client-specific configuration
rate-limit.clients.rest-template.enabled=true
rate-limit.clients.rest-client.enabled=true
rate-limit.clients.web-client.enabled=true
```

## Exception Handling

```java
@Service
public class ApiService {

    private final ApiClient apiClient;

    public User getUserSafely(String id) {
        try {
            return apiClient.getUser(id);
        } catch (RateLimitExceededException e) {
            // Handle rate limit exceeded
            logger.warn("Rate limit exceeded for host: {} - wait time: {}ms",
                e.getHost(), e.getWaitTimeMillis());
            throw new ServiceUnavailableException("API rate limit exceeded", e);
        }
    }
}
```

## Thread Safety

All rate limiting implementations are thread-safe and can be used with concurrent requests. The `InMemoryRateLimitTracker` uses concurrent data structures to safely track rate limits across multiple threads.

## Testing

The integration tests demonstrate HTTP Interface support with all three backing clients:
- `HttpExchangeRateLimitRestClientTest` - RestClient backend
- `HttpExchangeRateLimitRestTemplateTest` - RestTemplate backend
- `HttpExchangeRateLimitWebClientTest` - WebClient backend

Each test suite verifies:
- All HTTP methods (@GetExchange, @PostExchange, @PutExchange, @PatchExchange, @DeleteExchange)
- Rate limit tracking and enforcement
- Waiting behavior (blocking vs non-blocking)
- Exception handling when threshold is exceeded
- Header parsing and state updates
