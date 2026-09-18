// Pure Kotlin/JVM: data models, JSON loading and the HengeEngine. No Android dependencies.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core-solar"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
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

// Small command-line front end for checking real events without a phone:
//   ./gradlew :core-engine:run --args="upcoming 2026-09-18 14 0.7"
//   ./gradlew :core-engine:run --args="date 2026-09-20"
//   ./gradlew :core-engine:run --args="next sl_04c46e16 2026-09-18 5"
application { mainClass.set("de.munichhenge.engine.CliKt") }
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir.parentFile
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dfile.encoding=UTF-8")
}
