plugins {
    `java-library`
}

dependencies {
    api(project(":libs:platform-core"))
    api("org.springframework.boot:spring-boot-starter-actuator")
    // Boot 4: Tracer/OTel SDK/OTLP exporter auto-configuration을 함께 가져온다(K-1).
    api("org.springframework.boot:spring-boot-starter-opentelemetry")
    api("io.micrometer:context-propagation")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")

    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-webflux")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
}
