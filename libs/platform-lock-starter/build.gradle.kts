plugins {
    `java-library`
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-aop")
    api("org.redisson:redisson-spring-boot-starter:3.45.1")
    implementation("org.springframework:spring-tx")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}