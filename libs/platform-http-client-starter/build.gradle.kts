plugins {
    `java-library`
}

dependencies {
    api(project(":libs:platform-core"))
    api(project(":libs:platform-observability-starter"))
    api("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
    api("io.github.resilience4j:resilience4j-spring-boot3")
    api("io.github.resilience4j:resilience4j-kotlin")
    // Boot 4는 RestClient.Builder/WebClient.Builder auto-configuration(observation 포함)을 별도 모듈로 분리했다.
    // 없으면 팩토리가 계측되지 않은 기본 builder를 써서 traceparent·client span이 생기지 않는다(K-11).
    api("org.springframework.boot:spring-boot-restclient")
    api("org.springframework.boot:spring-boot-webclient")

    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-webflux")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // servlet(RestClient)·reactive(WebClient) 경로를 각각 web-application-type을 지정해서 검증한다.
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
}