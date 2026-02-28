plugins {
    java
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.bavodaniels"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
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
