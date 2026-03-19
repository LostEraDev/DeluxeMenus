plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
}

tasks {
    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    jar {
        archiveFileName.set("DeluxeMenus-Velocity-${rootProject.version}.jar")
    }

    compileJava {
        options.encoding = "UTF-8"
    }
}
