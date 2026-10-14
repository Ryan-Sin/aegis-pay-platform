dependencies {
    implementation(project(":libs:platform-observability-starter"))

    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.nimbusds:nimbus-jose-jwt:9.47")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}