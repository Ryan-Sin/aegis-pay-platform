package io.aegis.services.gateway

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
    @DisplayName("domain은 Spring에 의존하지 않는다.")
    fun domainIsFrameworkFree() {
        noClasses().that().resideInAPackage("$BASE_PACKAGE.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
            .check(classes)
    }

    @Test
    @DisplayName("application은 Redis, Gateway, WebFlux, HTTP 클라이언트, 캐시 구현에 의존하지 않는다.")
    fun applicationIsInfrastructureFree() {
        noClasses().that().resideInAPackage("$BASE_PACKAGE.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework.data..",
                "org.springframework.cloud..",
                "org.springframework.web..",
                "io.aegis.platform.httpclient..",
                "com.github.benmanes.caffeine..",
            )
            .check(classes)
    }

    @Test
    @DisplayName("adapter.in은 adapter.out을 직접 부르지 않고 포트를 거친다.")
    fun inboundAdaptersUsePorts() {
        noClasses().that().resideInAPackage("$BASE_PACKAGE.adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("$BASE_PACKAGE.adapter.out..")
            .check(classes)
    }

    private companion object {
        const val BASE_PACKAGE = "io.aegis.services.gateway"
    }
}
