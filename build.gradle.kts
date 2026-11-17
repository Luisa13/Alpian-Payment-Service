plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
    alias(libs.plugins.jib)
    alias(libs.plugins.openapi.generator)
    java
    jacoco
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// ─── Dependencies ─────────────────────────────────────────────────────────────

dependencies {
    // BOM — manages all Spring Boot ecosystem versions
    implementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.spring.boot.bom))

    // Web + Validation
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Data — Spring Data JDBC (repositories + aggregate mapping) + JdbcClient (complex SQL)
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")

    // Flyway — migrations; postgresql module required for Flyway 10+
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Structured logging (logstash JSON encoder, non-local profiles)
    implementation(libs.logstash.logback.encoder)

    // ─── Test ─────────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")

    // Testcontainers — @ServiceConnection auto-wires containers into Spring context
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")

    // Architecture enforcement
    testImplementation(libs.archunit.junit5)
}

// ─── Spotless ─────────────────────────────────────────────────────────────────

spotless {
    java {
        // Only format hand-written sources; never touch generated code
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

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            // Enforce coverage only on the domain and application layers —
            // pure business logic that must be thoroughly tested.
            element = "CLASS"
            includes =
                listOf(
                    "ch.example.template.*.domain.*",
                    "ch.example.template.*.application.*",
                    "ch.example.template.shared.domain.*",
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
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// ─── OpenAPI Generator ────────────────────────────────────────────────────────

val generatedOpenApiDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("${projectDir}/src/main/resources/openapi/api.yaml")
    outputDir.set(generatedOpenApiDir.get().asFile.absolutePath)
    apiPackage.set("ch.example.template.transfer.api.generated")
    modelPackage.set("ch.example.template.transfer.api.generated.model")
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

sourceSets {
    main {
        java {
            srcDir(generatedOpenApiDir.map { it.dir("src/main/java") })
        }
    }
}

tasks.compileJava {
    dependsOn(tasks.openApiGenerate)
}

// Generated sources must be excluded from Spotless and ArchUnit (handled by target("src/**"))

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
