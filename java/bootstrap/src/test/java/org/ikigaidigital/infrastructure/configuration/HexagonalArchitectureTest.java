package org.ikigaidigital.infrastructure.configuration;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.ikigaidigital.application.port.in.CommandBus;
import org.ikigaidigital.application.port.in.QueryBus;
import org.ikigaidigital.infrastructure.web.TimeDepositController;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class HexagonalArchitectureTest {
    // Models, strategies, and the calculator service are all part of the framework-free domain module.
    private static final String[] DOMAIN_PACKAGES = {
            "org.ikigaidigital.domain..", "org.ikigaidigital.services..", "org.ikigaidigital.interest.."
    };
    private static final JavaClasses BACKEND = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.ikigaidigital");

    @Test
    void domainDependsOnlyOnJavaAndItself() {
        classes().that().resideInAnyPackage(DOMAIN_PACKAGES)
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "org.ikigaidigital.domain..",
                        "org.ikigaidigital.services..", "org.ikigaidigital.interest..")
                .check(BACKEND);
    }

    @Test
    void applicationDependsOnlyOnJavaDomainAndItself() {
        classes().that().resideInAPackage("org.ikigaidigital.application..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "org.ikigaidigital.domain..", "org.ikigaidigital.services..",
                        "org.ikigaidigital.application..")
                .check(BACKEND);
    }

    @Test
    void moduleDependenciesPointInwardAndOnlyBootstrapConnectsAdapters() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy(DOMAIN_PACKAGES)
                .layer("Application").definedBy("org.ikigaidigital.application..")
                .layer("Persistence").definedBy("org.ikigaidigital.infrastructure.persistence..")
                .layer("Web").definedBy("org.ikigaidigital.infrastructure.web..")
                .layer("Bootstrap").definedBy("org.ikigaidigital.infrastructure.configuration..")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Persistence", "Web", "Bootstrap")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Web", "Bootstrap")
                .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Bootstrap")
                .whereLayer("Web").mayOnlyBeAccessedByLayers("Bootstrap")
                .whereLayer("Bootstrap").mayNotBeAccessedByAnyLayer()
                .check(BACKEND);
    }

    @Test
    void webUsesBusPortsAndRequestsRatherThanConcreteDispatchersOrHandlers() {
        noClasses().that().resideInAPackage("org.ikigaidigital.infrastructure.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.ikigaidigital.application.bus..",
                        "org.ikigaidigital.application.command.handler..",
                        "org.ikigaidigital.application.query.handler..")
                .check(BACKEND);
        classes().that().haveFullyQualifiedName(TimeDepositController.class.getName())
                .should().dependOnClassesThat().haveFullyQualifiedName(CommandBus.class.getName())
                .andShould().dependOnClassesThat().haveFullyQualifiedName(QueryBus.class.getName())
                .check(BACKEND);
        noClasses().that().resideInAPackage("org.ikigaidigital.infrastructure.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.ikigaidigital.domain.port..", "org.ikigaidigital.services..")
                .check(BACKEND);
    }

    @Test
    void commandAndQueryHandlersRemainSeparate() {
        noClasses().that().resideInAPackage("org.ikigaidigital.application.command.handler..")
                .should().dependOnClassesThat().resideInAPackage("org.ikigaidigital.application.query..")
                .check(BACKEND);
        noClasses().that().resideInAPackage("org.ikigaidigital.application.query.handler..")
                .should().dependOnClassesThat().resideInAPackage("org.ikigaidigital.application.command..")
                .check(BACKEND);
    }

    @Test
    void transactionManagementIsConfinedToBootstrap() {
        noClasses().that().resideOutsideOfPackage("org.ikigaidigital.infrastructure.configuration..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework.transaction..")
                .check(BACKEND);
    }

    @Test
    void interestStrategiesRemainEncapsulatedByDomain() {
        noClasses().that().resideOutsideOfPackages(DOMAIN_PACKAGES)
                .should().dependOnClassesThat()
                .resideInAPackage("org.ikigaidigital.interest..")
                .check(BACKEND);
    }
}
