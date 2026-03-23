# Publishing to Maven Central

This document explains how to publish the Rate Limit Client library to Maven Central using the **Maven Central API v1.0** (no GPG signing required).

## Prerequisites

### 1. Create a Maven Central Publisher Account

1. Go to https://central.sonatype.com/account
2. Sign up for a Sonatype account
3. Create a new namespace request for `be.bavodaniels`:
   - Go to your account → Namespaces
   - Create namespace request for `be.bavodaniels`
   - Verify you own the domain or GitHub account
4. Once approved, generate publisher credentials:
   - Account Settings → Tokens
   - Create a new "User Token"
   - Save the username and password (you'll need these)

### 2. That's It!

No GPG keys needed with the Maven Central API v1.0. Much simpler! ✨

## GitHub Secrets Setup

Use your existing Sonatype credentials configured on your GitHub repo:

1. **OSSRH_USERNAME**: Your Sonatype/Maven Central publisher username
2. **OSSRH_PASSWORD**: Your Sonatype/Maven Central publisher token/password

The workflow uses these secrets to authenticate with the Maven Central API.

## Local Configuration (Optional)

For publishing from your local machine, add to `~/.gradle/gradle.properties`:

```properties
ossrhUsername=your_sonatype_username
ossrhPassword=your_sonatype_password
```

## Publishing Process

### Automatic (via GitHub) 🚀

1. Commit your changes to the repository
2. Tag a release: `git tag -a v1.1.0 -m "Release v1.1.0"`
3. Push the tag: `git push origin v1.1.0`
4. The GitHub Actions workflow will automatically:
   - Build and test the project
   - Update README with version and coverage
   - Create a publishing bundle on Maven Central
   - Upload JAR, sources, and javadoc
   - Publish with automatic promotion to Maven Central
   - Create a GitHub release with the JAR

The entire process takes 2-5 minutes!

### Manual (from Local Machine)

```bash
# Just build and run the publish step
./gradlew clean build

# Then manually create the upload and publish via curl (see workflow for details)
```

## How It Works

The Maven Central API v1.0 works in three steps:

1. **Create Upload Bundle**: Creates a new publishing activity
2. **Upload Artifacts**: Uploads JAR, sources, and javadoc
3. **Publish with Auto-Release**: Publishes and immediately promotes to Maven Central

No staging repositories, no manual promotion, no GPG signing!

## Verification

After successful publication:

1. Check Maven Central directly: https://search.maven.org/artifact/be.bavodaniels/rate-limit-client
2. Library available within 10-15 minutes (usually faster than traditional method)
3. Use in projects:
   ```gradle
   dependencies {
       implementation("be.bavodaniels:rate-limit-client:1.1.0")
   }
   ```

## Troubleshooting

### "Invalid credentials"
- Ensure you're using a Maven Central **publisher token**, not your account password
- Generate tokens at: https://central.sonatype.com/account
- Check that namespace `be.bavodaniels` is approved

### "Namespace not approved"
- Go to https://central.sonatype.com/account → Namespaces
- Create a namespace request for `be.bavodaniels`
- Verify ownership and wait for approval (usually within 10 minutes)

### "Upload failed: 400"
- Check that artifacts exist: `find build/libs -name "*.jar"`
- Ensure upload ID is valid
- Check Maven Central status page for outages

### "Not found in Maven Central after publishing"
- Indexing takes 5-15 minutes
- Check https://search.maven.org with your coordinates
- Try searching by group ID first to verify it's there

## Security Notes

- Never commit credentials to the repository
- Use GitHub's secret masking for sensitive values
- Maven Central publisher tokens are safer than account passwords
- Rotate tokens periodically in your Sonatype account

## References

- [Maven Central Publishing Guide](https://central.sonatype.org/)
- [Maven Central API v1.0 Docs](https://central.sonatype.org/publish/publish-api-v1/)
- [Creating Publisher Tokens](https://central.sonatype.com/account)
- [Gradle Maven-Publish Plugin](https://docs.gradle.org/current/userguide/publishing_maven.html)
