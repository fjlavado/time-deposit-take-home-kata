package org.ikigaidigital.infrastructure.configuration;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class HexagonalArchitectureTest {
    private static final JavaClasses BACKEND = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.ikigaidigital");

    @Test
    void domainDependsOnlyOnJavaAndItself() {
        classes().that().resideInAPackage("org.ikigaidigital.domain..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "org.ikigaidigital.domain..")
                .check(BACKEND);
    }

    @Test
    void applicationDependsOnlyOnJavaDomainAndItself() {
        classes().that().resideInAPackage("org.ikigaidigital.application..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "org.ikigaidigital.domain..", "org.ikigaidigital.application..")
                .check(BACKEND);
    }

    @Test
    void moduleDependenciesPointInwardAndOnlyBootstrapConnectsAdapters() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy("org.ikigaidigital.domain..")
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
    void webUsesApplicationPortsRatherThanInteractors() {
        noClasses().that().resideInAPackage("org.ikigaidigital.infrastructure.web..")
                .should().dependOnClassesThat().resideInAPackage("org.ikigaidigital.application.service..")
                .check(BACKEND);
    }

    @Test
    void newBackendDoesNotDependOnLegacyClasses() {
        noClasses().should().dependOnClassesThat().resideInAPackage("org.ikigaidigital")
                .check(BACKEND);
    }
}
