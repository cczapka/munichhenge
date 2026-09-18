// All Gradle plugins live on the root classpath so the Kotlin Android plugin and the Android
// Gradle plugin share a classloader. `-Pmunichhenge.android=false` leaves AGP out, which lets
// the pure JVM modules (:core-solar, :core-engine) build on machines without access to
// Google's Maven repository (configure-on-demand keeps :app unconfigured there).
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.2.0")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.2.0")
        if (project.findProperty("munichhenge.android") != "false") {
            classpath("com.android.tools.build:gradle:8.11.1")
        }
    }
}
