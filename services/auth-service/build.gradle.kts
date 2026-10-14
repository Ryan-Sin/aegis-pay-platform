plugins {
    kotlin("plugin.jpa")
}

dependencies {
    implementation(project(":libs:platform-web-starter"))
    implementation(project(":libs:platform-observability-starter"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.nimbusds:nimbus-jose-jwt:9.47")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}