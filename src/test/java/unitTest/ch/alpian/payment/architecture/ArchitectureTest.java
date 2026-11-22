package ch.alpian.payment.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Transactional;

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
@AnalyzeClasses(packages = "ch.alpian.payment")
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_must_not_use_spring_framework_packages = noClasses()
            .that()
            .resideInAPackage("ch.alpian.payment..domain..")
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
            .resideInAPackage("ch.alpian.payment..api..")
            .should()
            .accessClassesThat()
            .resideInAPackage("ch.alpian.payment..infrastructure..")
            .because("API layer controllers must delegate to application services, not reach"
                    + " directly into infrastructure repositories or adapters");

    @ArchTest
    static final ArchRule application_must_not_access_infrastructure = noClasses()
            .that()
            .resideInAPackage("ch.alpian.payment..application..")
            .should()
            .accessClassesThat()
            .resideInAPackage("ch.alpian.payment..infrastructure..")
            .because("application layer flows and tasks must depend on domain ports, not"
                    + " infrastructure repositories or adapters");

    @ArchTest
    static final ArchRule domain_must_not_access_other_layers = noClasses()
            .that()
            .resideInAPackage("ch.alpian.payment..domain..")
            .should()
            .accessClassesThat()
            .resideInAnyPackage(
                    "ch.alpian.payment..api..",
                    "ch.alpian.payment..application..",
                    "ch.alpian.payment..infrastructure..")
            .because("domain layer is the innermost ring; it must have zero dependencies on"
                    + " outer layers to remain independently testable and reusable");

    @ArchTest
    static final ArchRule task_classes_must_not_be_transactional = noClasses()
            .that()
            .resideInAPackage("ch.alpian.payment..application.tasks..")
            .should()
            .beAnnotatedWith(Transactional.class)
            .because("flows are the only transaction boundary; tasks must join the flow's transaction");

    @ArchTest
    static final ArchRule task_methods_must_not_be_transactional = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("ch.alpian.payment..application.tasks..")
            .should()
            .beAnnotatedWith(Transactional.class)
            .because("flows are the only transaction boundary; tasks must join the flow's transaction");
}
