import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "com.etcmc.etcworlds"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.lucko.me/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    mavenLocal()
}

dependencies {
    paperweight.foliaDevBundle("1.21.11-R0.1-SNAPSHOT")

    compileOnly("net.luckperms:api:5.4")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.etcmc.etccore:ETCCore:1.0.0")
}

paperweight.reobfArtifactConfiguration.set(ReobfArtifactConfiguration.MOJANG_PRODUCTION)

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "project" to mapOf(
            "version" to version,
            "name" to project.name,
            "description" to "Multi-world manager Folia-native"
        )
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching(listOf("plugin.yml", "*.yml")) { expand(props) }
}

tasks.jar {
    archiveFileName.set("ETCWorlds-${version}-dev.jar")
}

tasks.assemble {
    dependsOn("reobfJar")
}
