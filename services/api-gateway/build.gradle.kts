dependencies {
    implementation(project(":libs:platform-core"))
    implementation(project(":libs:platform-webflux-starter"))
    implementation(project(":libs:platform-observability-starter"))
    implementation(project(":libs:platform-http-client-starter"))

    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.nimbusds:nimbus-jose-jwt:9.47")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
}