plugins {
    `java-library`
}

dependencies {
    api(project(":libs:platform-core"))
    api("org.springframework.boot:spring-boot-starter-aspectj")
    api("org.redisson:redisson-spring-boot-starter:4.8.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}