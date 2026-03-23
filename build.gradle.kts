plugins {
    java
    `maven-publish`
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
}

group = "be.bavodaniels"
version = "1.3.1"

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Spring Boot dependencies - only what we need
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-web")

    // Validation for @ConfigurationProperties
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Optional WebFlux support for WebClient
    compileOnly("org.springframework:spring-webflux")
    compileOnly("org.springframework.boot:spring-boot-webclient")
    compileOnly("io.projectreactor.netty:reactor-netty")

    // Annotation processing for configuration properties
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework:spring-webflux")
    testImplementation("org.springframework.boot:spring-boot-webclient")
    testImplementation("io.projectreactor.netty:reactor-netty")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test> {
    jvmArgs("--enable-preview")
}

// Don't create executable jar
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}

// =======================================================================================
// JaCoCo Code Coverage Configuration
// =======================================================================================
// JaCoCo provides code coverage analysis for Java projects
// Reports are generated in build/reports/jacoco/test/html/index.html
// Run: ./gradlew test jacocoTestReport to generate coverage reports
// Run: ./gradlew test jacocoTestCoverageVerification to verify coverage thresholds

jacoco {
    toolVersion = "0.8.14"  // Latest stable JaCoCo version
}

// Configure the jacocoTestReport task to generate HTML and XML reports
tasks.jacocoTestReport {
    // Make test report generation depend on test execution
    dependsOn(tasks.test)

    reports {
        // Generate HTML report for human-readable coverage analysis
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/test/html"))

        // Generate XML report for CI/CD integration (e.g., SonarQube, Codecov)
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"))

        // CSV report (optional, disabled by default)
        csv.required.set(false)
    }

    // Configure source sets for report generation
    sourceDirectories.setFrom(files(sourceSets.main.get().allSource.srcDirs))
    classDirectories.setFrom(files(sourceSets.main.get().output))
    executionData.setFrom(files("${layout.buildDirectory.get()}/jacoco/test.exec"))

    // Exclude auto-generated classes from coverage report
    // Add patterns here if you have generated code (e.g., Lombok, MapStruct)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    // Exclude Spring Boot configuration metadata classes
                    "**/Spring*ConfigurationMetadata.class",
                    // Add other exclusions as needed, e.g.:
                    // "**/generated/**",
                    // "**/*MapperImpl.class",
                )
            }
        })
    )
}

// Configure coverage verification with quality gates
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)

    violationRules {
        rule {
            // Enable this rule to enforce coverage thresholds
            enabled = true

            // Rule applies to the entire project (BUNDLE)
            // Other options: CLASS, SOURCEFILE, PACKAGE, METHOD
            element = "BUNDLE"

            // Define coverage thresholds
            limit {
                // INSTRUCTION: individual bytecode instructions
                // LINE: source code lines
                // BRANCH: if/else, switch, loops branches
                // COMPLEXITY: cyclomatic complexity
                // METHOD: methods
                // CLASS: classes
                counter = "LINE"
                value = "COVEREDRATIO"  // Ratio of covered items to total items
                minimum = "0.80".toBigDecimal()  // 80% line coverage required
            }

            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()  // 75% branch coverage required
            }

            limit {
                counter = "CLASS"
                value = "MISSEDCOUNT"
                maximum = "0".toBigDecimal()  // No classes should be completely uncovered
            }
        }
    }

    // Use the same exclusions as the report task
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/Spring*ConfigurationMetadata.class",
                )
            }
        })
    )
}

// Optional: Make 'check' task also verify coverage thresholds
// Uncomment the line below to fail the build if coverage is below thresholds
// tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }

// Make test execution generate JaCoCo execution data
tasks.test {
    finalizedBy(tasks.jacocoTestReport)  // Generate report after tests run

    // Optional: Also verify coverage after tests (uncomment to enable)
    // finalizedBy(tasks.jacocoTestCoverageVerification)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("Rate Limit Client")
                description.set("Intelligent client-side rate limiting for Spring Boot applications")
                url.set("https://github.com/bavodaniels/rate-limit-client")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://opensource.org/licenses/Apache-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("bavodaniels")
                        name.set("Bavo Daniels")
                        email.set("bavo.daniels@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/bavodaniels/rate-limit-client.git")
                    developerConnection.set("scm:git:ssh://git@github.com/bavodaniels/rate-limit-client.git")
                    url.set("https://github.com/bavodaniels/rate-limit-client")
                }
            }
        }
    }

    repositories {
        maven {
            name = "MavenCentral"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")

            credentials {
                username = System.getenv("OSSRH_USERNAME") ?: project.findProperty("ossrhUsername")?.toString() ?: ""
                password = System.getenv("OSSRH_PASSWORD") ?: project.findProperty("ossrhPassword")?.toString() ?: ""
            }
        }
    }
}

tasks.named<org.gradle.api.publish.tasks.GenerateModuleMetadata>(
    "generateMetadataFileForMavenJavaPublication"
) {
    suppressedValidationErrors.add("dependencies-without-versions")
}
