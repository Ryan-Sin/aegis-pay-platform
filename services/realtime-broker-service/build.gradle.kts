dependencies {
    implementation(project(":libs:platform-mvc-starter"))
    implementation(project(":libs:platform-observability-starter"))

    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}