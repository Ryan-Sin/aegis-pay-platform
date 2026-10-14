plugins {
    kotlin("plugin.jpa")
}

dependencies {
    implementation(project(":libs:platform-web-starter"))
    implementation(project(":libs:platform-lock-starter"))
    implementation(project(":libs:platform-http-client-starter"))
    implementation(project(":libs:platform-observability-starter"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.kafka:spring-kafka")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}