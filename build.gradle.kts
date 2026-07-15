plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "vn.haohansmp"
version = "1.1.0"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.53.2.0")
    implementation("com.google.code.gson:gson:2.13.2")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    test {
        useJUnitPlatform()
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("org.sqlite", "vn.haohansmp.utilities.libs.sqlite")
        relocate("com.google.gson", "vn.haohansmp.utilities.libs.gson")
    }

    jar {
        archiveClassifier.set("plain")
    }

    build {
        dependsOn(shadowJar)
    }
}
