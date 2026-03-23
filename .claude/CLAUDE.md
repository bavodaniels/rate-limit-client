# Rate Limit Client - Claude Code Configuration

## Project Overview
This is a **Gradle-based Java project** that implements intelligent client-side rate limiting for Spring Boot applications. The library provides automatic rate limit tracking and request throttling across multiple HTTP clients.

## Build System
- **Build Tool**: Gradle 9.3.1
- **Java Version**: Java 25
- **Spring Boot**: 4.0.3

## Common Commands

### Testing
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "RestTemplateIntegrationTest"

# Run specific test method
./gradlew test --tests "RestTemplateIntegrationTest.restTemplateShouldBlockWhenAnyMultiBucketExceeded"

# Run tests with detailed output
./gradlew test --info

# Generate test report
./gradlew jacocoTestReport
```

### Building
```bash
# Build the project
./gradlew build

# Clean build artifacts
./gradlew clean

# Build without running tests
./gradlew build -x test

# Show project structure
./gradlew projects
```

### Code Quality
```bash
# Check test coverage
./gradlew jacocoTestReport

# View test report
open build/reports/tests/test/index.html

# View coverage report
open build/reports/jacoco/test/html/index.html
```

## Project Structure

```
rate-limit-client/
├── src/
│   ├── main/java/be/bavodaniels/ratelimit/
│   │   ├── config/          # Auto-configuration classes
│   │   ├── exception/       # Custom exceptions
│   │   ├── filter/          # WebClient filters
│   │   ├── interceptor/     # RestTemplate/RestClient interceptors
│   │   ├── model/           # Data models (RateLimitInfo, RateLimitState)
│   │   ├── parser/          # Header parsing logic
│   │   ├── tracker/         # Rate limit state tracking
│   │   └── httpexchange/    # @HttpExchange support
│   └── test/java/           # Comprehensive test suite
├── build.gradle.kts         # Gradle build configuration
├── README.md                # User documentation
└── .claude/CLAUDE.md        # This file (Claude Code configuration)
```

## Key Implementation Areas

### Multi-Bucket Rate Limiting (v1.1.0+)
- **Parser**: `RateLimitHeaderParser.parseAllBuckets()` - Detects and parses multiple rate limit buckets
- **State Management**: `RateLimitState` - Manages multiple buckets with proper concurrency control
- **Tests**: Comprehensive tests in integration and unit test files
- **Documentation**: README section "Multi-Bucket Rate Limiting" with examples

### Client Support
- **RestTemplate**: `RestTemplateRateLimitInterceptor` + `RateLimitAutoConfiguration`
- **RestClient**: `RestClientRateLimitInterceptor` + `RestClientCustomizer`
- **WebClient**: `WebClientRateLimitFilter` + `WebClientCustomizer` (fully reactive)
- **@HttpExchange**: Transparent support via backing client integration

## Recent Changes (2026-03-02)

### Completed Issues
- **#31**: Multi-bucket header parsing ✓ (already implemented)
- **#32**: RateLimitState multi-bucket support ✓ (already implemented)
- **#33**: InMemoryRateLimitTracker multi-bucket support ✓ (already implemented)
- **#34**: Comprehensive tests for multi-bucket rate limiting ✓ (implemented)
- **#35**: Documentation updates for multi-bucket support ✓ (implemented)

### Changes Made
1. **Tests Added**:
   - `RestTemplateIntegrationTest`: Multi-bucket blocking/allowing scenarios
   - `WebClientIntegrationTest`: Multi-bucket reactive scenarios
   - `InMemoryRateLimitTrackerTest`: Already had comprehensive multi-bucket tests

2. **Documentation Updated**:
   - README: Multi-bucket section with examples and behavior explanation
   - README Features: Added "Multi-Bucket Support" feature
   - README FAQ: Added multi-bucket behavior question
   - JavaDoc: Updated classes with multi-bucket information:
     - `RateLimitHeaderParser` - pattern documentation
     - `RateLimitState` - behavior and usage examples
     - `RateLimitTracker` - interface documentation

## Development Tips

### Running Tests While Developing
```bash
# Fast feedback during development
./gradlew test --daemon

# Watch mode isn't built-in, but you can use this pattern:
while true; do ./gradlew test -q; sleep 2; done
```

### Understanding Test Failures
```bash
# Get full stack trace
./gradlew test --stacktrace

# See which tests failed
./gradlew test --info | grep FAILED

# Run with debugging
./gradlew test --debug
```

### Key Test Files to Know
- `RateLimitHeaderParserTest` - Tests header parsing including multi-bucket detection
- `RateLimitStateTest` - Tests bucket management and request decision logic
- `InMemoryRateLimitTrackerTest` - Tests tracker behavior with various bucket scenarios
- `RestTemplateIntegrationTest` - Integration tests including multi-bucket scenarios
- `WebClientIntegrationTest` - Reactive integration tests including multi-bucket scenarios

## Important Notes

### Thread Safety
All components use thread-safe data structures:
- `RateLimitState`: Uses `ReadWriteLock` for concurrent access
- `InMemoryRateLimitTracker`: Uses `ConcurrentHashMap`
- Fully thread-safe for multi-threaded request scenarios

### Backwards Compatibility
Legacy single-bucket headers (X-RateLimit-Limit, etc.) are automatically mapped to a "default" bucket, maintaining full backwards compatibility.

### Configuration
Via `application.yml`:
```yaml
rate-limit:
  enabled: true
  max-wait-time-millis: 30000
  per-host: true
```

## Gradle Tips

### Speed up builds
```bash
# Enable configuration cache
echo "org.gradle.configuration-cache=true" >> gradle.properties

# Use more parallel threads
./gradlew test --parallel --max-workers=4
```

### Check dependencies
```bash
# Show dependency tree
./gradlew dependencies

# Check for updates
./gradlew dependencyUpdates
```

## Version Info
- **Current Version**: 1.1.0 (with multi-bucket support)
- **Previous Version**: 1.0.0
- **Java Target**: Java 25+
- **Spring Boot**: 4.0.3+

## Further Reading
- See README.md for user-facing documentation
- See GitHub Issues #31-#35 for implementation details
- Test files have extensive documentation comments
