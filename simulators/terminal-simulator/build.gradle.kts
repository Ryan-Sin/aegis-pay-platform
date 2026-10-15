dependencies {
    implementation(project(":libs:platform-mvc-starter"))
    implementation(project(":libs:platform-http-client-starter"))
    implementation(project(":libs:platform-observability-starter"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}