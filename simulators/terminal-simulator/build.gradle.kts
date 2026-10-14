dependencies {
    implementation(project(":libs:platform-web-starter"))
    implementation(project(":libs:platform-http-client-starter"))
    implementation(project(":libs:platform-observability-starter"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}