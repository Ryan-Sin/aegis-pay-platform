plugins {
    `java-library`
}

dependencies {
    api(project(":libs:platform-core"))
    api("org.springframework.boot:spring-boot-starter-aop")
    api("org.redisson:redisson-spring-boot-starter:3.45.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}