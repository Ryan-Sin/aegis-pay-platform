plugins {
    kotlin("plugin.jpa")
}

dependencies {
    implementation(project(":libs:platform-mvc-starter"))
    implementation(project(":libs:platform-observability-starter"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.nimbusds:nimbus-jose-jwt:9.47")
    implementation(platform("software.amazon.awssdk:bom:2.56.0"))
    implementation("software.amazon.awssdk:kms")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation("org.testcontainers:testcontainers-localstack")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
