# Rate Limit Client

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)
![Coverage](https://img.shields.io/badge/coverage-100.0%25-brightgreen.svg)
![Version](https://img.shields.io/badge/version-1.3.1-blue.svg)

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

- **Minimal Configuration**: Simple interceptor and filter classes you wire into your HTTP clients
- **Multi-Client Support**: Provides interceptors and filters for RestTemplate, RestClient, WebClient, and @HttpExchange interfaces
- **Multi-Bucket Support**: Tracks and respects multiple rate limit buckets (e.g., daily, hourly, per-resource limits)
- **Industry-Standard Headers**: Supports X-RateLimit-*, RateLimit-*, Retry-After, and provider-specific formats (GitHub, Stripe)
- **Intelligent Waiting**: Automatically waits for rate limits to reset within configurable thresholds
- **Non-Blocking Reactive**: WebClient filter uses fully reactive, non-blocking delays
- **Thread-Safe**: Built with concurrent data structures for safe multi-threaded usage
- **Rich Exception Metadata**: Detailed RateLimitExceededException with retry timing information
- **Defensive Design**: Protects external APIs from being overwhelmed by your application
- **Extensible**: Implement custom RateLimitTracker for distributed rate limiting (e.g., Redis-backed)
- **Flexible Configuration**: Configurable wait thresholds and per-host rate limit tracking

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("be.bavodaniels:rate-limit-client:1.1.0-SNAPSHOT")
}
```

Or for `build.gradle`:

```groovy
dependencies {
    implementation 'be.bavodaniels:rate-limit-client:1.1.0-SNAPSHOT'
}
```

For Maven users (`pom.xml`):

```xml
<dependency>
    <groupId>be.bavodaniels</groupId>
    <artifactId>rate-limit-client</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

1. **Add the dependency** (see [Installation](#installation))

2. **Configure rate limiting properties** in `application.yml`:

```yaml
rate-limit:
  enabled: true
  max-wait-time-millis: 5000  # Maximum wait time before throwing exception
```

3. **Wire the interceptor/filter into your HTTP client**:

For **RestClient**:
```java
@Configuration
public class ApiConfig {
    @Bean
    public RestClient apiRestClient(
            RestClient.Builder builder,
            RateLimitTracker tracker,
            RateLimitProperties properties) {
        return builder
            .baseUrl("https://api.example.com")
            .requestInterceptor(
                new RestTemplateRateLimitInterceptor(
                    tracker,
                    properties.getMaxWaitTimeMillis()
                )
            )
            .build();
    }
}
```

For **WebClient**:
```java
@Configuration
public class WebClientConfig {
    @Bean
    public WebClient apiWebClient(
            WebClient.Builder builder,
            RateLimitTracker tracker,
            RateLimitProperties properties) {
        return builder
            .baseUrl("https://api.example.com")
            .filter(
                new WebClientRateLimitFilter(
                    tracker,
                    properties.getMaxWaitTimeMillis()
                )
            )
            .build();
    }
}
```

That's it! The library will:
- Intercept HTTP requests and parse rate limit headers from responses
- Track rate limits per host
- Automatically wait when rate limits are approached
- Throw `RateLimitExceededException` when wait time exceeds your configured threshold

## Configuration

### Basic Configuration (application.yml)

```yaml
rate-limit:
  # Global enable/disable (default: true)
  enabled: true

  # Maximum wait time in milliseconds before throwing exception (default: 5000)
  max-wait-time-millis: 5000
```

### Disabling Rate Limiting

To disable rate limiting globally, set the property:

```yaml
rate-limit:
  enabled: false
```

To disable for a specific client, simply don't wire the interceptor/filter when creating the client.

### Security Note

This library provides **defensive client-side rate limiting** to prevent your application from overwhelming external APIs. It parses rate limit information from HTTP response headers and automatically throttles requests. This is designed to help you be a good API citizen and avoid rate limit violations.

## Usage Examples

### RestTemplate

To use rate limiting with RestTemplate, manually add the interceptor:

```java
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            RateLimitTracker tracker,
            RateLimitProperties properties) {
        RestTemplate restTemplate = builder
            .rootUri("https://api.github.com")
            .build();

        // Add the rate limit interceptor
        restTemplate.getInterceptors().add(
            new RestTemplateRateLimitInterceptor(
                tracker,
                properties.getMaxWaitTimeMillis()
            )
        );

        return restTemplate;
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

Add the rate limiting interceptor when building your RestClient:

```java
@Configuration
public class ApiConfig {

    @Bean
    public RestClient apiRestClient(
            RestClient.Builder builder,
            RateLimitTracker tracker,
            RateLimitProperties properties) {
        return builder
            .baseUrl("https://api.stripe.com")
            .defaultHeader("Authorization", "Bearer ${stripe.api.key}")
            .requestInterceptor(
                new RestTemplateRateLimitInterceptor(
                    tracker,
                    properties.getMaxWaitTimeMillis()
                )
            )
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

Add the rate limiting filter when building your WebClient:

```java
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient apiWebClient(
            WebClient.Builder builder,
            RateLimitTracker tracker,
            RateLimitProperties properties) {
        return builder
            .baseUrl("https://api.twitter.com")
            .defaultHeader("Authorization", "Bearer ${twitter.api.key}")
            .filter(
                new WebClientRateLimitFilter(
                    tracker,
                    properties.getMaxWaitTimeMillis()
                )
            )
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

@HttpExchange interfaces work transparently with rate limiting when backed by a RestClient or WebClient with the interceptor/filter configured:

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
    public ApiClient apiClient(
            RestClient.Builder restClientBuilder,
            RateLimitTracker tracker,
            RateLimitProperties properties) {
        RestClient restClient = restClientBuilder
            .baseUrl("https://api.example.com")
            .requestInterceptor(
                new RestTemplateRateLimitInterceptor(
                    tracker,
                    properties.getMaxWaitTimeMillis()
                )
            )
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
        // Rate limiting works transparently through the backing RestClient
        return apiClient.getUser(id);
    }
}
```

You can also back HTTP interfaces with WebClient - just add the `WebClientRateLimitFilter` to the builder.

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

### Multi-Bucket Rate Limiting

Some APIs implement multiple rate limit buckets, each tracking a different rate limit scope. For example, an API might have separate limits for:
- **Daily limit** (e.g., 10,000,000 requests per day)
- **Hourly limit** (e.g., 1,000 requests per hour)
- **Per-resource limit** (e.g., 1 request per minute for operations on a specific resource)

The library automatically detects and tracks all rate limit buckets present in response headers. Each bucket is identified by a unique name in the header pattern `X-RateLimit-{BucketName}-*`.

#### Example Multi-Bucket Headers

```
X-RateLimit-AppDay-Limit: 10000000
X-RateLimit-AppDay-Remaining: 9999999
X-RateLimit-AppDay-Reset: 1735689600

X-RateLimit-Session-Limit: 120
X-RateLimit-Session-Remaining: 75
X-RateLimit-Session-Reset: 1735689600

X-RateLimit-SessionOrders-Limit: 1
X-RateLimit-SessionOrders-Remaining: 0  # ← Most restrictive bucket
X-RateLimit-SessionOrders-Reset: 1735689660
```

#### How It Works

When multiple buckets are present:
- **Request is allowed only if ALL buckets have capacity**
- **Request is blocked if ANY bucket is exceeded**
- **Wait time is the MAXIMUM across all buckets**

In the example above, the request would be blocked because the `SessionOrders` bucket has 0 remaining requests, even though `AppDay` and `Session` buckets have capacity.

#### Accessing Bucket Information

```java
RateLimitState state = tracker.getState("api.example.com");

// Check if any bucket is exceeded
if (state.isLimitExceeded()) {
    // Handle rate limit
}

// Get bucket-specific information
RateLimitInfo orders = state.getBucketInfo("SessionOrders");
System.out.println("Orders bucket: " + orders.remaining() + "/" + orders.limit());

// Find the most restrictive bucket
String restrictiveBucket = state.getMostRestrictiveBucket();

// Get all tracked buckets
Set<String> buckets = state.getAllBuckets();
```

#### Legacy Single-Bucket Headers

For backwards compatibility, legacy single-bucket headers are automatically mapped to a "default" bucket:

```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 500
X-RateLimit-Reset: 1735689600

// Accessible as: state.getBucketInfo("default")
```

## Configuration Properties Reference

All configuration properties with their defaults:

```yaml
rate-limit:
  # Global enable/disable for auto-configuration
  # Type: boolean
  # Default: true
  enabled: true

  # Maximum wait time in milliseconds before throwing RateLimitExceededException
  # If a request requires waiting longer than this threshold, an exception is thrown
  # Type: long (milliseconds)
  # Default: 5000 (5 seconds)
  max-wait-time-millis: 5000

  # Whether to track rate limits per host
  # Type: boolean
  # Default: true
  per-host: true
```

## How It Works

### Architecture Overview

The rate limiting implementation uses a straightforward, composable design:

1. **Auto-Configuration Layer** (`RateLimitAutoConfiguration`)
   - Provides the default `InMemoryRateLimitTracker` bean
   - Binds configuration properties from `application.yml`
   - Can be globally disabled via `rate-limit.enabled=false`

2. **Interceptor/Filter Layer**
   - **RestTemplate/RestClient**: `RestTemplateRateLimitInterceptor` - wired manually into clients
   - **WebClient**: `WebClientRateLimitFilter` - wired manually into clients
   - **@HttpExchange**: Works through the backing RestClient or WebClient

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

**Q: Rate limiting isn't working**

A: Make sure you've wired the interceptor or filter into your HTTP client when building it:

```java
// ✅ Good - interceptor explicitly added
restTemplate.getInterceptors().add(new RestTemplateRateLimitInterceptor(...));

// ❌ Bad - interceptor not added
RestTemplate restTemplate = new RestTemplate(); // No rate limiting
```

**Q: I'm getting RateLimitExceededException immediately**

A: The API has already rate limited you. Check:
1. The `retryAfter` time in the exception
2. Recent request patterns
3. Consider increasing `max-wait-time-millis` if appropriate

**Q: Rate limiting seems too strict**

A: The library respects the rate limits set by the API. If you need to adjust the behavior:
- Increase `max-wait-time-millis` to allow longer waits
- Disable rate limiting via `rate-limit.enabled: false` if needed
- Or don't wire the interceptor/filter for specific clients you want to exclude

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
    be.bavodaniels.ratelimit: DEBUG
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

**Q: How does multi-bucket rate limiting work?**

A: When an API provides multiple rate limit buckets (e.g., daily, hourly, per-resource), the library tracks all of them simultaneously. A request is allowed only if ALL buckets have capacity. If ANY bucket is exceeded, the request is blocked. The wait time used is the MAXIMUM wait across all exceeded buckets. This ensures compliance with the most restrictive limit.

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
