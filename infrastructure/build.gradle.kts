plugins {
    id("org.springframework.boot")
    kotlin("plugin.jpa")
}

dependencies {
    // Common 모듈 의존성
    api(project(":common"))

    // Spring Data JPA
    api("org.springframework.boot:spring-boot-starter-data-jpa")

    // H2 Database
    api("com.h2database:h2")

    // Apache POI (Excel 파싱)
    api("org.apache.poi:poi:5.2.3")
    api("org.apache.poi:poi-ooxml:5.2.3")

    // Validation
    api("org.springframework.boot:spring-boot-starter-validation")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// No bootJar task - this is a library module
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}
