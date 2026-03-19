plugins {
    java
    id("maven-publish")
    id("com.gradleup.shadow") version("8.3.5")
}

repositories {
    mavenCentral()
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.glaremasters.me/repository/public/")
    maven("https://nexus.phoenixdevt.fr/repository/maven-public/")
    maven("https://repo.momirealms.net/releases/")
    maven("https://repo.nexomc.com/releases/")
    maven("https://repo.oraxen.com/releases")
    maven("https://jitpack.io")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly(libs.spigot)

    compileOnly(fileTree("libs") { include("*.jar") })

    //compileOnly(libs.vault)
    compileOnly(libs.authlib)

    compileOnly(libs.headdb)
    compileOnly(libs.craftengine.core)
    compileOnly(libs.craftengine.bukkit)
    compileOnly(libs.itemsadder)
    compileOnly(libs.nexo)
    compileOnly(libs.oraxen)
    compileOnly(libs.mythiclib)
    compileOnly(libs.mmoitems)
    //compileOnly(libs.score)
    compileOnly(libs.sig)
    compileOnly(libs.packetevents)

    compileOnly(libs.papi)

    implementation(libs.nashorn)
    implementation(libs.adventure.platform)
    implementation(libs.adventure.minimessage)
    implementation(libs.bstats)

    compileOnly("org.jetbrains:annotations:23.0.0")
}

tasks {
    shadowJar {
        relocate("org.objectweb.asm", "com.extendedclip.deluxemenus.libs.asm")
        relocate("org.openjdk.nashorn", "com.extendedclip.deluxemenus.libs.nashorn")
        relocate("net.kyori", "com.extendedclip.deluxemenus.libs.adventure")
        relocate("org.bstats", "com.extendedclip.deluxemenus.libs.bstats")
        archiveFileName.set("DeluxeMenus-${rootProject.version}.jar")
    }
    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        disableAutoTargetJvm()
    }

    processResources {
        filesMatching("plugin.yml") {
            expand("version" to rootProject.version)
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks.named("jar"))

            groupId = project.group.toString()
            artifactId = rootProject.name.lowercase()
            version = project.version.toString()
        }
    }

    repositories {
        maven {
            name = "LostEra-Repo"
            url = uri("https://repo.alsyinfra.dev/repository/lostera/")
            credentials {
                username = project.findProperty("nexusUsername") as String?
                password = project.findProperty("nexusPassword") as String?
            }
        }
    }
}
