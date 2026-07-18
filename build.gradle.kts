import org.gradle.api.tasks.Copy

plugins {
    base
    id("net.fabricmc.fabric-loom") version "1.17.8" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.17.8" apply false
    id("net.neoforged.moddev") version "2.0.141" apply false
    id("net.minecraftforge.gradle") version "7.0.31" apply false
}

val releaseDirectory = layout.buildDirectory.dir("release")
val pythonExecutable = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
    "python"
} else {
    "python3"
}

val verifyMatrix by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates the declared loader/version matrix and architecture invariants."
    commandLine(pythonExecutable, rootProject.file("scripts/verify_matrix.py").absolutePath)
}

val buildAll by tasks.registering {
    group = "build"
    description = "Builds every enabled Stonecutter common and loader target."
    dependsOn(verifyMatrix)
}

val testAll by tasks.registering {
    group = "verification"
    description = "Runs the shared test suite for every enabled Minecraft version."
    dependsOn(verifyMatrix)
}

val collectReleaseJars by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Collects remapped/reobfuscated loader jars into build/release."
    dependsOn(buildAll)
    commandLine(pythonExecutable, rootProject.file("scripts/collect_release_jars.py").absolutePath)
}

val verifyReleaseJars by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates release jars, descriptors, mixins, and ServiceLoader wiring."
    dependsOn(collectReleaseJars)
    commandLine(pythonExecutable, rootProject.file("scripts/verify_release_jars.py").absolutePath)
}

gradle.projectsEvaluated {
    // This central script is applied to Stonecutter's per-version aggregate
    // project (for example :26.1.2), not the physical Gradle root. The loader
    // nodes are siblings under :fabric/:neoforge/:forge, so subprojects would
    // be empty here and the aggregate tasks would incorrectly be no-ops.
    val targetVersion = project.name
    val legacyNeoForge = if (targetVersion == "1.20.5") {
        rootProject.findProject(":neoforge:$targetVersion")
    } else {
        null
    }
    val loaderProjects = listOf("fabric", "neoforge", "forge").mapNotNull { loader ->
        rootProject.findProject(":$loader:$targetVersion")
    }.filterNot { it == legacyNeoForge }

    tasks.named("buildAll").configure {
        dependsOn(loaderProjects.mapNotNull { it.tasks.findByName("build") })
        legacyNeoForge?.let { dependsOn(it.tasks.named("legacyUserdevReleaseJar")) }
    }

    tasks.named("testAll").configure {
        dependsOn(loaderProjects.mapNotNull { it.tasks.findByName("test") })
        legacyNeoForge?.let { dependsOn(it.tasks.named("legacyUserdevTest")) }
    }
}
