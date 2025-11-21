// 순수 Kotlin 모듈 - 도메인 예외 및 Enum만 포함
dependencies {
    // SLF4J for logging in exceptions
    api("org.slf4j:slf4j-api:2.0.9")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
