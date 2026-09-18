// Pure Kotlin/JVM: sun position, refraction, rise/set events and the altitude root-finder.
// No Android dependencies (CLAUDE.md), so `./gradlew :core-solar:test` runs in seconds.
plugins {
    kotlin("jvm")
}

// Target JVM 17 bytecode (Android-compatible) with whatever JDK >= 17 runs the build;
// no pinned toolchain, so no JDK download is needed.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
