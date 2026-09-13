package com.remidosol.llmmcp.credit.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Same boundaries as job-service (ADR-0003); duplicated on purpose — services share nothing but contracts. */
@AnalyzeClasses(packages = "com.remidosol.llmmcp.credit", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "..application..", "..infrastructure..", "..api..");

    @ArchTest
    static final ArchRule api_does_not_touch_infrastructure = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule application_does_not_touch_infrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule application_does_not_touch_api = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule lombok_only_in_domain_entities = noClasses()
            .that().resideOutsideOfPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("lombok..");

    @ArchTest
    static final ArchRule jackson2_is_banned = noClasses()
            .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule kafka_sends_only_from_the_outbox_poller = noClasses()
            .that().doNotHaveSimpleName("OutboxPoller").and().doNotHaveSimpleName("KafkaErrorHandlingConfig")
            .should().dependOnClassesThat().haveSimpleName("KafkaTemplate")
            .because("no KafkaTemplate.send outside the outbox publisher after Phase 3 (CLAUDE.md hard rule); "
                    + "the DLT recoverer config is the single exception");
}
