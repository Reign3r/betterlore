import java.util.Properties

plugins {
    java
    `maven-publish`
}

val targetVersion = project.path.substringAfterLast(':')
val profileFile = rootProject.file("versions/$targetVersion/gradle.properties")
require(profileFile.isFile) { "Missing dependency profile for $path: $profileFile" }
val profileProperties = Properties().apply {
    profileFile.inputStream().use(::load)
}
profileProperties.forEach { (key, value) ->
    extensions.extraProperties.set(key.toString(), value)
}
val targetJava = profileProperties.getProperty("java.version", "21").toInt()

group = providers.gradleProperty("mod.group").get()
version = providers.gradleProperty("mod.version").get()

java {
    withSourcesJar()
    toolchain.languageVersion.set(JavaLanguageVersion.of(targetJava))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJava)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net/")
    maven("https://maven.fabricmc.net/")
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.minecraftforge.net/")
    maven("https://maven.blamejared.com/")
    maven("https://maven.shedaniel.me/")
    maven("https://maven.nucleoid.xyz/")
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:${providers.gradleProperty("deps.junit").get()}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:${providers.gradleProperty("deps.junit").get()}")
}
