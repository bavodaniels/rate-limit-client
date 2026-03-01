# Rate Limit Client

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)
![Coverage](https://img.shields.io/badge/coverage-100.0%25-brightgreen.svg)
![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)

A Spring Boot auto-configuration library that provides intelligent, defensive client-side rate limiting for HTTP clients. Automatically tracks and respects rate limit headers from APIs to prevent overwhelming external services and avoid rate limit violations.

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Usage Examples](#usage-examples)
  - [RestTemplate](#resttemplate)
  - [RestClient](#restclient)
  - [WebClient](#webclient)
  - [@HttpExchange Interfaces](#httpexchange-interfaces)
- [Supported Header Formats](#supported-header-formats)
- [Configuration Properties Reference](#configuration-properties-reference)
- [How It Works](#how-it-works)
- [Exception Handling](#exception-handling)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Zero Configuration**: Auto-configures rate limiting for all Spring HTTP clients
- **Multi-Client Support**: Works seamlessly with RestTemplate, RestClient, WebClient, and @HttpExchange interfaces
- **Industry-Standard Headers**: Supports X-RateLimit-*, RateLimit-*, Retry-After, and provider-specific formats (GitHub, Stripe)
- **Intelligent Waiting**: Automatically waits for rate limits to reset within configurable thresholds
- **Non-Blocking Reactive**: WebClient uses fully reactive, non-blocking delays
- **Thread-Safe**: Built with concurrent data structures for safe multi-threaded usage
- **Rich Exception Metadata**: Detailed RateLimitExceededException with retry timing information
- **Defensive Design**: Protects external APIs from being overwhelmed by your application
- **Spring Boot Integration**: Leverages Spring's auto-configuration and BeanPostProcessor patterns
- **Flexible Configuration**: Per-client enable/disable controls and configurable wait thresholds

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.bavodaniels:rate-limit-client:1.0.0-SNAPSHOT")
}
```

Or for `build.gradle`:

```groovy
dependencies {
    implementation 'com.bavodaniels:rate-limit-client:1.0.0-SNAPSHOT'
}
```

For Maven users (`pom.xml`):

```xml
<dependency>
    <groupId>com.bavodaniels</groupId>
    <artifactId>rate-limit-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

1. **Add the dependency** (see [Installation](#installation))

2. **Create an application.yml** with optional configuration:

```yaml
rate-limit:
  enabled: true
  max-wait-time-millis: 30000  # Maximum wait time before throwing exception
```

3. **Use any Spring HTTP client** - rate limiting is automatically enabled:

```java
@Service
public class ApiService {

    private final RestClient restClient;

    public ApiService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
            .baseUrl("https://api.example.com")
            .build();
    }

    public String fetchData() {
        // Rate limiting is automatically applied!
        return restClient.get()
            .uri("/data")
            .retrieve()
            .body(String.class);
    }
}
```

That's it! The library automatically:
- Intercepts all HTTP requests
- Parses rate limit headers from responses
- Tracks rate limits per host
- Waits when rate limits are approached
- Throws `RateLimitExceededException` when wait time exceeds threshold

## Configuration

### Basic Configuration (application.yml)

```yaml
rate-limit:
  # Global enable/disable (default: true)
  enabled: true

  # Maximum wait time in milliseconds before throwing exception (default: 30000)
  max-wait-time-millis: 30000

  # Per-client configuration
  clients:
    rest-template:
      enabled: true
    rest-client:
      enabled: true
    web-client:
      enabled: true
    http-interface:
      enabled: true
```

### Disabling Rate Limiting

```yaml
# Disable globally
rate-limit:
  enabled: false

# Or disable per client type
rate-limit:
  clients:
    rest-template:
      enabled: false
```

### Security Note

This library provides **defensive client-side rate limiting** to prevent your application from overwhelming external APIs. It parses rate limit information from HTTP response headers and automatically throttles requests. This is designed to help you be a good API citizen and avoid rate limit violations.

## Usage Examples

### RestTemplate

RestTemplate beans are automatically configured with rate limiting via `BeanPostProcessor`:

```java
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Rate limiting is automatically added to this bean
        return builder
            .rootUri("https://api.github.com")
            .build();
    }
}

@Service
public class GitHubService {

    private final RestTemplate restTemplate;

    public GitHubService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getUser(String username) {
        try {
            return restTemplate.getForObject("/users/{username}", String.class, username);
        } catch (RateLimitExceededException e) {
            // Handle rate limit exceeded
            log.warn("Rate limit exceeded for GitHub API. Retry after: {}",
                e.getRetryAfter());
            throw e;
        }
    }
}
```

### RestClient

RestClient.Builder is auto-configured with the rate limiting interceptor:

```java
@Configuration
public class ApiConfig {

    @Bean
    public RestClient apiRestClient(RestClient.Builder builder) {
        // Rate limiting interceptor is already configured
        return builder
            .baseUrl("https://api.stripe.com")
            .defaultHeader("Authorization", "Bearer ${stripe.api.key}")
            .build();
    }
}

@Service
public class StripeService {

    private final RestClient restClient;

    public StripeService(@Qualifier("apiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public String createCharge(ChargeRequest request) {
        return restClient.post()
            .uri("/v1/charges")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(String.class);
    }
}
```

### WebClient

WebClient.Builder beans are customized with the rate limiting filter:

```java
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient apiWebClient(WebClient.Builder builder) {
        // Rate limiting filter is automatically applied
        return builder
            .baseUrl("https://api.twitter.com")
            .defaultHeader("Authorization", "Bearer ${twitter.api.key}")
            .build();
    }
}

@Service
public class TwitterService {

    private final WebClient webClient;

    public TwitterService(WebClient apiWebClient) {
        this.webClient = apiWebClient;
    }

    public Mono<String> getTweet(String tweetId) {
        return webClient.get()
            .uri("/2/tweets/{id}", tweetId)
            .retrieve()
            .bodyToMono(String.class)
            .onErrorResume(RateLimitExceededException.class, e -> {
                // Reactive error handling
                log.warn("Rate limit exceeded. Wait duration: {} seconds",
                    e.getWaitDuration().toSeconds());
                return Mono.error(e);
            });
    }
}
```

### @HttpExchange Interfaces

Declarative HTTP interfaces work transparently with rate limiting when backed by any configured client:

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

// Configure with RestClient (recommended)
@Configuration
public class HttpInterfaceConfig {

    @Bean
    public ApiClient apiClient(RestClient.Builder restClientBuilder) {
        RestClient restClient = restClientBuilder
            .baseUrl("https://api.example.com")
            .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build();

        return factory.createClient(ApiClient.class);
    }
}

// Use the interface
@Service
public class UserService {

    private final ApiClient apiClient;

    public UserService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public User fetchUser(String id) {
        // Rate limiting works transparently
        return apiClient.getUser(id);
    }
}
```

You can also back HTTP interfaces with RestTemplate or WebClient - rate limiting works with all of them.

## Supported Header Formats

The library automatically detects and parses rate limit information from various header formats:

| Header Name | Format | Example | Providers |
|-------------|--------|---------|-----------|
| `X-RateLimit-Limit` | Integer | `100` | GitHub, Twitter/X, Generic |
| `X-RateLimit-Remaining` | Integer | `75` | GitHub, Twitter/X, Generic |
| `X-RateLimit-Reset` | Unix timestamp | `1735689600` | GitHub, Twitter/X, Generic |
| `RateLimit-Limit` | Integer | `100` | RFC Draft Format |
| `RateLimit-Remaining` | Integer | `75` | RFC Draft Format |
| `RateLimit-Reset` | Unix timestamp | `1735689600` | RFC Draft Format |
| `Retry-After` | Seconds or HTTP-date | `60` or `Wed, 21 Oct 2015 07:28:00 GMT` | Standard HTTP |
| `Stripe-RateLimit-Limit` | Integer | `100` | Stripe |
| `Stripe-RateLimit-Remaining` | Integer | `75` | Stripe |
| `Stripe-RateLimit-Reset` | Unix timestamp | `1735689600` | Stripe |

The parser falls back through multiple header formats and handles both Unix timestamps and RFC 7231 HTTP-date formats.

## Configuration Properties Reference

All configuration properties with their defaults:

```yaml
rate-limit:
  # Global enable/disable
  # Type: boolean
  # Default: true
  enabled: true

  # Maximum wait time in milliseconds before throwing RateLimitExceededException
  # If a request requires waiting longer than this threshold, an exception is thrown
  # Type: long (milliseconds)
  # Default: 30000 (30 seconds)
  max-wait-time-millis: 30000

  # Per-client configuration
  clients:
    # RestTemplate-specific settings
    rest-template:
      # Enable/disable rate limiting for all RestTemplate beans
      # Type: boolean
      # Default: true
      enabled: true

    # RestClient-specific settings
    rest-client:
      # Enable/disable rate limiting for RestClient.Builder
      # Type: boolean
      # Default: true
      enabled: true

    # WebClient-specific settings
    web-client:
      # Enable/disable rate limiting for all WebClient.Builder beans
      # Type: boolean
      # Default: true
      enabled: true

    # HttpServiceProxyFactory-specific settings
    http-interface:
      # Enable/disable rate limiting for @HttpExchange interfaces
      # Type: boolean
      # Default: true
      enabled: true
```

## How It Works

### Architecture Overview

The rate limiting implementation uses a multi-layered approach:

1. **Auto-Configuration Layer** (`RateLimitAutoConfiguration`)
   - Conditionally enables based on classpath detection
   - Registers BeanPostProcessors and customizers for each client type
   - Provides default `InMemoryRateLimitTracker` bean

2. **Client Integration Layer**
   - **RestTemplate**: BeanPostProcessor adds interceptor to all beans
   - **RestClient**: Pre-configures Builder with interceptor
   - **WebClient**: WebClientCustomizer adds reactive filter
   - **@HttpExchange**: Transparently inherits from backing client

3. **Tracking Layer** (`RateLimitTracker`)
   - Thread-safe in-memory storage of rate limit state per host
   - Concurrent data structures for multi-threaded access
   - Extensible interface for custom implementations (e.g., Redis-backed)

4. **Parsing Layer** (`RateLimitHeaderParser`)
   - Detects and parses multiple header formats
   - Falls back through standard and provider-specific headers
   - Handles Unix timestamps and HTTP-date formats

### Request Flow

```
Request → Check Rate Limit State → Wait if Needed → Execute Request → Parse Response Headers → Update State
```

**Detailed Flow:**

1. **Pre-Request Check**: Interceptor/filter checks current rate limit state for target host
2. **Wait Decision**:
   - If remaining requests > 0: proceed immediately
   - If remaining = 0 and reset time not passed: calculate wait time
   - If wait time ≤ threshold: wait (blocking for RestClient/RestTemplate, reactive for WebClient)
   - If wait time > threshold: throw `RateLimitExceededException`
3. **Request Execution**: Proceed with HTTP request
4. **Response Processing**: Parse rate limit headers from response
5. **State Update**: Store updated rate limit information for the host

### Thread Safety

All components are designed for concurrent access:
- `InMemoryRateLimitTracker` uses `ConcurrentHashMap`
- `RateLimitState` is immutable with atomic updates
- Interceptors and filters are stateless

## Exception Handling

### RateLimitExceededException

When the required wait time exceeds the configured threshold, a `RateLimitExceededException` is thrown with comprehensive metadata:

```java
try {
    String result = restClient.get()
        .uri("/api/endpoint")
        .retrieve()
        .body(String.class);
} catch (RateLimitExceededException e) {
    // Access rich exception metadata
    String host = e.getHost();              // "api.example.com"
    String endpoint = e.getEndpoint();      // "/api/endpoint"
    Instant retryAfter = e.getRetryAfter(); // When to retry
    Duration waitDuration = e.getWaitDuration(); // How long to wait

    log.error("Rate limit exceeded for {}{}. Retry after {} (wait {} seconds)",
        host, endpoint, retryAfter, waitDuration.toSeconds());

    // Handle the exception appropriately
    // - Queue for later retry
    // - Return cached data
    // - Return error to user
    // - Implement exponential backoff
}
```

### Exception Message Format

The exception includes a clear, actionable message:

```
Rate limit exceeded for 'api.example.com/users'. Retry after 2026-01-01T12:30:00Z (wait 60 seconds).
Please reduce request rate or implement exponential backoff.
```

## Troubleshooting

### Common Issues

**Q: Rate limiting isn't working for my RestTemplate**

A: Ensure your RestTemplate is defined as a Spring bean. The library uses `BeanPostProcessor` which only affects beans managed by Spring:

```java
// ✅ Good - managed by Spring
@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();
}

// ❌ Bad - not a bean
public void someMethod() {
    RestTemplate restTemplate = new RestTemplate(); // Won't have rate limiting
}
```

**Q: I'm getting RateLimitExceededException immediately**

A: The API has already rate limited you. Check:
1. The `retryAfter` time in the exception
2. Recent request patterns
3. Consider increasing `max-wait-time-millis` if appropriate

**Q: Rate limiting is too aggressive**

A: The library respects the rate limits set by the API. If you need to bypass rate limiting temporarily:
```yaml
rate-limit:
  enabled: false  # Disable globally, or per-client
```

**Q: WebClient isn't blocking when it should**

A: WebClient uses non-blocking reactive delays. The delay happens reactively in the pipeline. This is by design for reactive applications.

**Q: Can I use Redis for distributed rate limiting?**

A: Yes! Implement the `RateLimitTracker` interface and define it as a bean:

```java
@Bean
public RateLimitTracker rateLimitTracker(RedisTemplate<String, RateLimitState> redis) {
    return new RedisRateLimitTracker(redis);
}
```

Your implementation will automatically be used instead of the in-memory tracker.

**Q: How do I debug what rate limits are being tracked?**

A: Enable debug logging:
```yaml
logging:
  level:
    com.bavodaniels.ratelimit: DEBUG
```

### FAQ

**Q: Does this library respect API rate limits?**

A: Yes, that's the primary purpose. It parses rate limit headers and automatically throttles requests.

**Q: Is this library thread-safe?**

A: Yes, all components use thread-safe concurrent data structures.

**Q: Does this work with Spring Boot 3.x?**

A: This library is built for Spring Boot 4.x. For Spring Boot 3.x compatibility, check for a 0.x version.

**Q: Can I customize the rate limit tracker?**

A: Absolutely! Implement `RateLimitTracker` and register it as a bean. The library will use your implementation automatically via `@ConditionalOnMissingBean`.

**Q: What happens if an API doesn't send rate limit headers?**

A: Requests proceed normally. Rate limiting only activates when headers are detected.

## Contributing

Contributions are welcome! Please follow these guidelines:

1. **Fork the repository** and create a feature branch
2. **Write tests** for any new functionality
3. **Follow existing code style** and conventions
4. **Update documentation** for user-facing changes
5. **Run all tests** before submitting: `./gradlew test`
6. **Submit a pull request** with a clear description

### Development Setup

```bash
# Clone the repository
git clone https://github.com/bavodaniels/rate-limit-client.git
cd rate-limit-client

# Build the project
./gradlew build

# Run tests
./gradlew test

# Run tests with coverage
./gradlew test jacocoTestReport
```

### Code of Conduct

- Be respectful and inclusive
- Provide constructive feedback
- Focus on what is best for the community

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

```
Copyright 2024-2026 Bavo Daniels

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

**Built with ❤️ using Spring Boot**

For questions or support, please open an issue on GitHub.
