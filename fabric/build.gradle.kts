import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("net.fabricmc.fabric-loom") apply false
    id("net.fabricmc.fabric-loom-remap") apply false
    id("multiloader-common")
}

base.archivesName.set("better-lore-fabric-${project.name}")

val targetVersion = project.path.substringAfterLast(':')
val commonNode = rootProject.project(":common:$targetVersion")
val commonPreparedJava = commonNode.layout.buildDirectory.dir("stonecutter-cache/sources/main/java")
val commonPrepareTask = ":common:$targetVersion:stonecutterPrepare"
val commonSourceDirectory = rootProject.file("common/src/main/java")
val jeiVersion = project.findProperty("deps.jei_fabric")?.toString()
    ?: project.property("deps.jei").toString()
val jeiApiAvailable = jeiVersion != "UNSUPPORTED"
val jeiEntrypoint = if (jeiApiAvailable) {
    ",\n    \"jei_mod_plugin\": [\n      \"com.reign.betterlore.compat.jei.BetterLoreJeiPlugin\"\n    ]"
} else {
    ""
}
val conditionallyProcessedCommonSources = fileTree(commonSourceDirectory) {
    include("**/*.java")
}.files
    .filter { it.readText().contains("//?") }
    .map { it.relativeTo(commonSourceDirectory).invariantSeparatorsPath }
val prepareSharedJava by tasks.registering(Sync::class) {
    dependsOn(commonPrepareTask)
    from(commonSourceDirectory) {
        exclude(conditionallyProcessedCommonSources)
    }
    from(commonPreparedJava) {
        if (!jeiApiAvailable) {
            exclude("com/reign/betterlore/compat/jei/**")
        }
    }
    into(layout.buildDirectory.dir("generated/processed-common-java"))
}
val loaderSourceDirectory = rootProject.file("fabric/src/main/java")
val loaderPreparedJava = layout.buildDirectory.dir("stonecutter-cache/sources/main/java")
val conditionallyProcessedLoaderSources = fileTree(loaderSourceDirectory) {
    include("**/*.java")
}.files
    .filter { it.readText().contains("//?") }
    .map { it.relativeTo(loaderSourceDirectory).invariantSeparatorsPath }
val prepareLoaderJava by tasks.registering(Sync::class) {
    // Keep the normal source set intact for Stonecutter's own scanner, then
    // compile only this merged tree so the active version never sees both
    // sides of a conditional source block.
    dependsOn(tasks.named("stonecutterPrepare"))
    from(loaderSourceDirectory) {
        exclude(conditionallyProcessedLoaderSources)
    }
    from(loaderPreparedJava)
    into(layout.buildDirectory.dir("generated/processed-loader-java"))
}
val placeholderApiDependency = project.property("deps.placeholder_api").toString().let { value ->
    if (value.count { it == ':' } >= 2) value else "eu.pb4:placeholder-api:$value"
}
val jeiMinecraftVersion = project.findProperty("deps.jei_minecraft")?.toString()
    ?: project.property("deps.minecraft").toString()
val usesModernUnmappedLoom = project.property("deps.minecraft").toString().startsWith("26.")
apply(plugin = if (usesModernUnmappedLoom) "net.fabricmc.fabric-loom" else "net.fabricmc.fabric-loom-remap")
val loomExtension = extensions.getByType<LoomGradleExtensionAPI>()

dependencies {
    add("minecraft", "com.mojang:minecraft:${property("deps.minecraft")}")
    if (usesModernUnmappedLoom) {
        // Loom 1.17 resolves the current snapshot line without a separate
        // mappings artifact; this matches the proven 26.1.2 baseline.
        implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
        implementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
        implementation(placeholderApiDependency)
    } else {
        add("mappings", loomExtension.officialMojangMappings())
        add("modImplementation", "net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
        add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
        add("modImplementation", placeholderApiDependency)
    }
    if (jeiApiAvailable) {
        val jeiApi = "mezz.jei:jei-$jeiMinecraftVersion-fabric-api:$jeiVersion"
        if (usesModernUnmappedLoom) {
            compileOnly(jeiApi)
        } else {
            add("modCompileOnly", jeiApi)
        }
    }
    val reiApi = "me.shedaniel:RoughlyEnoughItems-api-fabric:${property("deps.rei")}" 
    if (usesModernUnmappedLoom) {
        compileOnly(reiApi)
        compileOnly("me.shedaniel.cloth:basic-math:0.6.1")
    } else {
        add("modCompileOnly", reiApi)
        add("modCompileOnly", "me.shedaniel.cloth:basic-math:0.6.1")
    }
}

sourceSets.main {
    java.srcDir(prepareSharedJava)
    resources.srcDir(rootProject.file("common/src/main/resources"))
    if (!jeiApiAvailable) {
        java.exclude("com/reign/betterlore/compat/jei/**")
    }
}

sourceSets.test {
    java.srcDir(rootProject.file("common/src/test/java"))
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(prepareSharedJava, prepareLoaderJava)
    setSource(files(prepareSharedJava, prepareLoaderJava))
}

tasks.named("compileTestJava") {
    dependsOn(prepareSharedJava, prepareLoaderJava)
}

val preparedSourceRoots = listOf(
    layout.buildDirectory.dir("generated/processed-common-java"),
    layout.buildDirectory.dir("generated/processed-loader-java")
)

tasks.named<Jar>("sourcesJar") {
    dependsOn(prepareSharedJava, prepareLoaderJava)
    // withSourcesJar() has already added sourceSets.main.allSource. Keep only
    // the generated trees and add the loader tree that remains raw in that
    // source set for Stonecutter's scanner.
    from(prepareLoaderJava)
    include { entry ->
        val file = entry.file.toPath().toAbsolutePath().normalize()
        preparedSourceRoots.any { root ->
            file.startsWith(root.get().asFile.toPath().toAbsolutePath().normalize())
        }
    }
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to project.property("deps.minecraft").toString(),
        "fabric_loader_version" to project.property("deps.fabric_loader").toString(),
        "fabric_api_version" to project.property("deps.fabric_api").toString(),
        "placeholder_api_version" to project.property("placeholder_api_version").toString(),
        "resource_pack_format" to project.property("minecraft.resource_pack_format").toString(),
        "resource_pack_minor" to project.property("minecraft.resource_pack_minor").toString(),
        "java_version" to project.property("java.version").toString(),
        "jei_entrypoint" to jeiEntrypoint
    )
    inputs.properties(properties)
    // Loom reads the raw descriptor while configuring the project, so retain a
    // valid non-JEI descriptor in src/main/resources. The release descriptor
    // is generated from the template below after the profile chooses whether
    // JEI is available.
    exclude("fabric.mod.json")
    from(rootProject.file("fabric/src/main/templates")) {
        include("fabric.mod.json.template")
        rename("fabric.mod.json.template", "fabric.mod.json")
        expand(properties)
    }
    filesMatching(listOf("better_lore.mixins.json", "pack.mcmeta")) {
        expand(properties)
    }
}
