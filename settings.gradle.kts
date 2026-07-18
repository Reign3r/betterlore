val isCi = System.getenv("CI") == "true"
gradle.startParameter.isBuildCacheEnabled = true
// Respect --parallel/--no-parallel and the root Gradle properties locally.
// Forcing parallel execution here made large release-matrix builds ignore the
// caller's memory-safety setting and could exhaust the Gradle heap.
if (isCi) {
    gradle.startParameter.isParallelProjectExecutionEnabled = false
    gradle.startParameter.isConfigureOnDemand = false
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
    plugins {
        id("net.fabricmc.fabric-loom") version "1.17.8"
        id("net.fabricmc.fabric-loom-remap") version "1.17.8"
        id("net.neoforged.moddev") version "2.0.141"
        id("net.minecraftforge.gradle") version "7.0.31"
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.7"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

fun versions(property: String): List<String> = providers.gradleProperty(property)
    .orNull
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?: emptyList()

val distributions = linkedMapOf(
    "common" to versions("stonecutter_enabled_common_versions"),
    "fabric" to versions("stonecutter_enabled_fabric_versions"),
    "neoforge" to versions("stonecutter_enabled_neoforge_versions"),
    "forge" to versions("stonecutter_enabled_forge_versions")
)
val commonVersions = distributions.getValue("common").toSet()
val missingCommonVersions = distributions
    .filterKeys { it != "common" }
    .values
    .flatten()
    .filterNot(commonVersions::contains)
    .toSortedSet()
require(missingCommonVersions.isEmpty()) {
    "stonecutter_enabled_common_versions must include every enabled loader version; " +
        "missing: ${missingCommonVersions.joinToString()}"
}
val allVersions = distributions.values.flatten().distinct()

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    create(rootProject) {
        versions(*allVersions.toTypedArray())
        distributions.forEach { (name, supportedVersions) ->
            if (supportedVersions.isNotEmpty()) {
                branch(name) {
                    versions(*supportedVersions.toTypedArray())
                }
            }
        }
    }
}

rootProject.name = "better-lore"
