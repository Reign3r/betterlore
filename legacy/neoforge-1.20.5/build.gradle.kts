plugins {
    java
    id("net.neoforged.gradle.userdev") version "7.0.192"
}

group = "com.reign"
version = "1.1.0"
base.archivesName.set("better-lore-neoforge-1.20.5")

// NeoGradle's generated NeoForm task paths are long. Keeping these ephemeral
// intermediates in the system temp directory avoids Windows CreateProcess path
// failures; the parent Stonecutter task copies the resulting release jar back
// to its conventional per-target build directory.
layout.buildDirectory.set(
    file(System.getProperty("java.io.tmpdir")).resolve("better-lore-neoforge-1.20.5")
)

java {
    withSourcesJar()
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net/")
    maven("https://maven.neoforged.net/releases/")
}

dependencies {
    implementation("net.neoforged:neoforge:20.5.21-beta")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

val generatedSources = layout.projectDirectory.dir("../../neoforge/versions/1.20.5/build/legacy-userdev-src")

sourceSets {
    named("main") {
        java.srcDir(generatedSources.dir("main/java"))
        resources.srcDir(generatedSources.dir("main/resources"))
    }
    named("test") {
        java.srcDir(generatedSources.dir("test/java"))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.processResources {
    val jeiDependency = ""
    val properties = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to "1.20.5",
        "neoforge_version" to "20.5.21-beta",
        "neoforge_loader_version" to "1",
        "resource_pack_format" to "32",
        "resource_pack_minor" to "0",
        "java_version" to "21",
        "jei_dependency" to jeiDependency
    )
    inputs.properties(properties)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "better_lore.mixins.json", "pack.mcmeta")) {
        expand(properties)
    }
}
