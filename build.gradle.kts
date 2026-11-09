plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "fr.croquemitaine"
version = "0.1.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    maven("https://maven.maxhenkel.de/repository/public") { name = "henkelmax" }
}

dependencies {
    // Build épinglé (vérifié le 2026-11-09 sur repo.papermc.io)
    compileOnly("io.papermc.paper:paper-api:26.3.build.246-stable")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.27")
    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

tasks {
    withType<JavaCompile> { options.encoding = "UTF-8"; options.compilerArgs.add("-Xlint:deprecation") }
    test { useJUnitPlatform() }
    processResources {
        filesMatching("plugin.yml") { expand("version" to project.version) }
    }
    runServer {
        minecraftVersion("26.3")
        jvmArgs("-Xms2G", "-Xmx2G")
        // Serveur de TEST uniquement : Via* permet aux bots Mineflayer (protocole ≤ 26.1) de se connecter.
        // -PwithVoice active Simple Voice Chat pour tester l'intégration vocale (J9).
        downloadPlugins {
            modrinth("viaversion", "5.12.2")
            modrinth("viabackwards", "5.12.2")
            if (project.hasProperty("withVoice")) modrinth("simple-voice-chat", "bukkit-2.6.27")
        }
    }
}
