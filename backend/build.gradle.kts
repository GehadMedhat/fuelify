plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    id("io.ktor.plugin") version "3.1.1"
    application
}

group = "com.example.fuelify"
version = "0.0.1"

application {
    mainClass.set("com.example.fuelify.ApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

val ktor_version = "3.1.1"
val exposed_version = "0.44.1"
val logback_version = "1.4.14"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
implementation("io.ktor:ktor-server-default-headers:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
    implementation("io.ktor:ktor-server-config-yaml:$ktor_version")
implementation("io.ktor:ktor-server-auth:$ktor_version")
implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
    // Database
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposed_version")  // replaces exposed-java-time

    // Serialization / logging
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // Mail
    implementation("com.sun.mail:javax.mail:1.6.2")

// Metrics
implementation("io.ktor:ktor-server-metrics-micrometer:$ktor_version")
implementation("io.micrometer:micrometer-registry-prometheus:1.12.4")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
testImplementation("com.h2database:h2:2.2.224")
    // Tests
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.0")
    // Keep this for your existing files (Tables.kt, Users.kt, all routes)
implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")

// Add this for the new HealthTables.kt
implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposed_version")
}

tasks.test {
    useJUnitPlatform()
}
