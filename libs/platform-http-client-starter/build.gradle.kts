plugins {
    `java-library`
}

dependencies {
    api(project(":libs:platform-core"))
    api(project(":libs:platform-observability-starter"))
    api("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
    api("io.github.resilience4j:resilience4j-spring-boot3")
    api("io.github.resilience4j:resilience4j-kotlin")

    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-webflux")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}