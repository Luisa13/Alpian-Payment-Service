package ch.example.template.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the package-by-feature layered architecture:
 *
 * <pre>
 *   api  ──▶  application  ──▶  domain
 *                   │
 *            infrastructure ──▶ domain
 * </pre>
 *
 * <p>Rules:
 *
 * <ol>
 *   <li>Domain must be framework-independent (Spring Boot / Web / Context / Kafka forbidden).
 *       Spring Data annotations ({@code @Id}, {@code @Version}, {@code @Table}) are explicitly
 *       permitted because they carry zero runtime coupling.
 *   <li>API layer must never reach into infrastructure; it communicates exclusively through the
 *       application layer.
 *   <li>Domain must not depend on any other layer of this application.
 * </ol>
 */
@AnalyzeClasses(packages = "ch.example.template")
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_must_not_use_spring_framework_packages = noClasses()
            .that()
            .resideInAPackage("ch.example.template..domain..")
            .should()
            .accessClassesThat()
            .resideInAnyPackage(
                    "org.springframework.boot..",
                    "org.springframework.web..",
                    "org.springframework.context..",
                    "org.springframework.kafka..",
                    "org.springframework.scheduling..",
                    "org.springframework.stereotype..")
            .because("domain logic must be portable and testable without a running Spring"
                    + " context; Spring Data mapping annotations (org.springframework.data.*)"
                    + " are permitted as a pragmatic trade-off");

    @ArchTest
    static final ArchRule api_must_not_access_infrastructure = noClasses()
            .that()
            .resideInAPackage("ch.example.template..api..")
            .should()
            .accessClassesThat()
            .resideInAPackage("ch.example.template..infrastructure..")
            .because("API layer controllers must delegate to application services, not reach"
                    + " directly into infrastructure repositories or adapters");

    @ArchTest
    static final ArchRule domain_must_not_access_other_layers = noClasses()
            .that()
            .resideInAPackage("ch.example.template..domain..")
            .should()
            .accessClassesThat()
            .resideInAnyPackage(
                    "ch.example.template..api..",
                    "ch.example.template..application..",
                    "ch.example.template..infrastructure..")
            .because("domain layer is the innermost ring; it must have zero dependencies on"
                    + " outer layers to remain independently testable and reusable");
}
