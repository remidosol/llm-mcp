package com.remidosol.llmmcp.job.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The hexagonal-lite boundaries (api / application / domain / infrastructure), enforced at build
 * time (ADR-0003). Duplicated per
 * service on purpose: services share nothing but {@code contracts}, so each carries its own copy
 * of these rules, including "no KafkaTemplate.send outside the outbox publisher".
 */
@AnalyzeClasses(packages = "com.remidosol.llmmcp.job", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "..application..", "..infrastructure..", "..api..")
            .because("the domain depends on nothing; jakarta.persistence is tolerated (ADR-0012)");

    @ArchTest
    static final ArchRule api_does_not_touch_infrastructure = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .because("inbound adapters talk to use cases, never to outbound adapters");

    @ArchTest
    static final ArchRule application_does_not_touch_infrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .because("the application layer reaches outward only through its own ports");

    @ArchTest
    static final ArchRule application_does_not_touch_api = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..api..")
            .because("dependencies point inward: api -> application -> domain");

    @ArchTest
    static final ArchRule lombok_only_in_domain_entities = noClasses()
            .that().resideOutsideOfPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("lombok..")
            .because("Lombok is allowed on JPA entities only (ADR-0004)");

    @ArchTest
    static final ArchRule jackson2_is_banned = noClasses()
            .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..")
            .because("this repo is Jackson 3 (tools.jackson) only — see CLAUDE.md hard rules");

    @ArchTest
    static final ArchRule kafka_sends_only_from_the_outbox_poller = noClasses()
            .that().doNotHaveSimpleName("OutboxPoller").and().doNotHaveSimpleName("KafkaErrorHandlingConfig")
            .should().dependOnClassesThat().haveSimpleName("KafkaTemplate")
            .because("no KafkaTemplate.send outside the outbox publisher (CLAUDE.md hard rule); "
                    + "the DLT recoverer config is the single exception");
}
