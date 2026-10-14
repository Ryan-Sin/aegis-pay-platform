plugins {
    `java-library`
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
    api("io.github.resilience4j:resilience4j-spring-boot3")
    api("io.github.resilience4j:resilience4j-bulkhead")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}