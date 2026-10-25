package io.aegis.services.auth

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HexagonalArchitectureTest {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages(BASE_PACKAGE)

    @Test
    @DisplayName("의존 방향은 adapter → application → domain이다.")
    fun layersDependInward() {
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("domain").definedBy("$BASE_PACKAGE.domain..")
            .layer("application").definedBy("$BASE_PACKAGE.application..")
            .layer("adapter").definedBy("$BASE_PACKAGE.adapter..")
            .whereLayer("adapter").mayNotBeAccessedByAnyLayer()
            .whereLayer("application").mayOnlyBeAccessedByLayers("adapter")
            .whereLayer("domain").mayOnlyBeAccessedByLayers("application", "adapter")
            .check(classes)
    }

    @Test
    @DisplayName("domain은 Spring, JPA, AWS SDK에 의존하지 않는다.")
    fun domainIsFrameworkFree() {
        noClasses().that().resideInAPackage("$BASE_PACKAGE.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "software.amazon..",
            )
            .check(classes)
    }

    @Test
    @DisplayName("application은 JPA와 AWS SDK에 의존하지 않는다(기술 의존은 adapter에만 둔다).")
    fun applicationIsInfrastructureFree() {
        noClasses().that().resideInAPackage("$BASE_PACKAGE.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "jakarta.persistence..",
                "org.springframework.data..",
                "software.amazon..",
                "org.springframework.web..",
            )
            .check(classes)
    }

    @Test
    @DisplayName("adapter끼리는 서로 의존하지 않는다(in/web은 out/kms를 직접 부르지 않는다).")
    fun adaptersAreIndependent() {
        noClasses().that().resideInAPackage("$BASE_PACKAGE.adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("$BASE_PACKAGE.adapter.out..")
            .check(classes)
    }

    private companion object {
        const val BASE_PACKAGE = "io.aegis.services.auth"
    }
}
