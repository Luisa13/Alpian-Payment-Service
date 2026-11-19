plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
    alias(libs.plugins.jib)
    alias(libs.plugins.openapi.generator)
    java
    jacoco
    `java-test-fixtures`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// ─── Source sets ──────────────────────────────────────────────────────────────

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated/openapi").map { it.dir("src/main/java") })
        }
    }
    // The default `test` source set is intentionally disconnected — all test execution
    // goes through the three custom tasks below (unitTest, integrationTest, e2eTest).
    // src/test/java/ acts as the parent directory for the three subdirectories.
    test {
        java.setSrcDirs(emptyList<File>())
        resources.setSrcDirs(emptyList<File>())
    }
    create("unitTest") {
        java.srcDir("src/test/java/unitTest")
        resources.srcDir("src/test/resources/unitTest")
        compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
        runtimeClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
    }
    create("integrationTest") {
        java.srcDir("src/test/java/integrationTest")
        resources.srcDir("src/test/resources/integrationTest")
        compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
        runtimeClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
    }
    create("e2eTest") {
        java.srcDir("src/test/java/e2eTest")
        resources.srcDir("src/test/resources/e2eTest")
        compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
        runtimeClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
    }
}

// ─── Dependencies ─────────────────────────────────────────────────────────────

dependencies {
    // BOM — manages all Spring Boot ecosystem versions
    implementation(platform(libs.spring.boot.bom))

    // Web + Validation
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Data — Spring Data JDBC + JdbcClient
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")

    // Flyway
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Structured logging
    implementation(libs.logstash.logback.encoder)

    // ─── Test fixtures (shared between integrationTest and e2eTest) ───────────
    testFixturesImplementation(platform(libs.spring.boot.bom))
    testFixturesImplementation("org.springframework.boot:spring-boot-starter-test")
    testFixturesImplementation("org.springframework.boot:spring-boot-testcontainers")
    testFixturesImplementation("org.testcontainers:junit-jupiter")
    testFixturesImplementation("org.testcontainers:postgresql")
    testFixturesImplementation("org.testcontainers:kafka")

    // ─── Unit tests ───────────────────────────────────────────────────────────
    "unitTestImplementation"(platform(libs.spring.boot.bom))
    "unitTestImplementation"("org.springframework.boot:spring-boot-starter-test")
    "unitTestImplementation"(libs.archunit.junit5)

    // ─── Integration tests ────────────────────────────────────────────────────
    "integrationTestImplementation"(platform(libs.spring.boot.bom))
    "integrationTestImplementation"("org.springframework.boot:spring-boot-starter-test")
    "integrationTestImplementation"("org.springframework.boot:spring-boot-testcontainers")
    "integrationTestImplementation"("org.springframework.kafka:spring-kafka-test")
    "integrationTestImplementation"("org.testcontainers:junit-jupiter")
    "integrationTestImplementation"("org.testcontainers:postgresql")
    "integrationTestImplementation"("org.testcontainers:kafka")
    "integrationTestImplementation"(testFixtures(project(":")))

    // ─── E2E tests ────────────────────────────────────────────────────────────
    "e2eTestImplementation"(platform(libs.spring.boot.bom))
    "e2eTestImplementation"("org.springframework.boot:spring-boot-starter-test")
    "e2eTestImplementation"("org.springframework.boot:spring-boot-testcontainers")
    "e2eTestImplementation"("org.springframework.kafka:spring-kafka-test")
    "e2eTestImplementation"("org.testcontainers:junit-jupiter")
    "e2eTestImplementation"("org.testcontainers:postgresql")
    "e2eTestImplementation"("org.testcontainers:kafka")
    "e2eTestImplementation"(testFixtures(project(":")))
}

// ─── Test tasks ───────────────────────────────────────────────────────────────

val unitTest by tasks.registering(Test::class) {
    description = "Runs unit tests (no Spring context, no infrastructure)."
    group = "verification"
    testClassesDirs = sourceSets["unitTest"].output.classesDirs
    classpath = sourceSets["unitTest"].runtimeClasspath
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    finalizedBy(tasks.named("jacocoTestReport"))
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests (Spring context + Testcontainers)."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    maxParallelForks = 1
    finalizedBy(tasks.named("jacocoTestReport"))
}

val e2eTest by tasks.registering(Test::class) {
    description = "Runs end-to-end tests (full flow with Testcontainers)."
    group = "verification"
    testClassesDirs = sourceSets["e2eTest"].output.classesDirs
    classpath = sourceSets["e2eTest"].runtimeClasspath
    useJUnitPlatform()
    maxParallelForks = 1
    finalizedBy(tasks.named("jacocoTestReport"))
}

// Disable the default test task — all testing goes through the three custom tasks
tasks.test {
    enabled = false
}

// ─── Spotless ─────────────────────────────────────────────────────────────────

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(libs.versions.palantirJavaFormat.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ─── JaCoCo ───────────────────────────────────────────────────────────────────

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    // Aggregate execution data from all three test tasks
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/*.exec")
        }
    )
    dependsOn(unitTest, integrationTest, e2eTest)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/*.exec")
        }
    )
    dependsOn(unitTest, integrationTest, e2eTest)
    violationRules {
        rule {
            element = "CLASS"
            includes = listOf(
                "ch.alpian.payment.domain.*",
                "ch.alpian.payment.application.*",
            )
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(unitTest, integrationTest, e2eTest, tasks.jacocoTestCoverageVerification)
}

// ─── bootRun ──────────────────────────────────────────────────────────────────

tasks.bootRun {
    jvmArgs("-Dspring.profiles.active=local")
}

// ─── OpenAPI Generator ────────────────────────────────────────────────────────

val generatedOpenApiDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("${projectDir}/src/main/resources/openapi/api.yaml")
    outputDir.set(generatedOpenApiDir.get().asFile.absolutePath)
    apiPackage.set("ch.alpian.payment.api.generated")
    modelPackage.set("ch.alpian.payment.api.generated.model")
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot3" to "true",
            "useTags" to "true",
            "openApiNullable" to "false",
            "documentationProvider" to "none",
            "serializationLibrary" to "jackson",
        )
    )
}

tasks.compileJava {
    dependsOn(tasks.openApiGenerate)
}

// ─── Jib ──────────────────────────────────────────────────────────────────────

jib {
    from {
        image = "gcr.io/distroless/java21-debian12"
    }
    to {
        image = System.getenv("IMAGE_NAME") ?: "payment-service-template:latest"
    }
    container {
        jvmFlags = listOf(
            "-XX:+UseContainerSupport",
            "-XX:MaxRAMPercentage=75.0",
            "-Djava.security.egd=file:/dev/./urandom"
        )
        ports = listOf("8080")
        user = "65532:65532"
    }
}
