plugins {
    java
    id("com.gradleup.shadow") version("8.3.5") apply false
    id("com.github.ben-manes.versions") version("0.51.0") apply false
}

// Change to true when releasing
val release = false
val majorVersion = "1.14.2"
val minorVersion = if (release) "Release" else "DEV-" + System.getenv("BUILD_NUMBER")

group = "com.extendedclip"
version = "$majorVersion-$minorVersion"

subprojects {
    group = rootProject.group
    version = rootProject.version
}
