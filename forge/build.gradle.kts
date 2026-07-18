import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import net.minecraftforge.gradle.MinecraftExtensionForProject

plugins {
    id("net.minecraftforge.gradle")
    id("multiloader-common")
}

base.archivesName.set("better-lore-forge-${project.name}")
val targetVersion = project.path.substringAfterLast(':')
val commonNode = rootProject.project(":common:$targetVersion")
val commonPreparedJava = commonNode.layout.buildDirectory.dir("stonecutter-cache/sources/main/java")
val commonPrepareTask = ":common:$targetVersion:stonecutterPrepare"
val commonSourceDirectory = rootProject.file("common/src/main/java")
val jeiVersion = project.findProperty("deps.jei_forge")?.toString()
    ?: project.property("deps.jei").toString()
val jeiApiAvailable = jeiVersion != "UNSUPPORTED"
val jeiMinecraftVersion = project.findProperty("deps.jei_minecraft")?.toString()
    ?: project.property("deps.minecraft").toString()
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
val loaderSourceDirectory = rootProject.file("forge/src/main/java")
val loaderPreparedJava = layout.buildDirectory.dir("stonecutter-cache/sources/main/java")
val conditionallyProcessedLoaderSources = fileTree(loaderSourceDirectory) {
    include("**/*.java")
}.files
    .filter { it.readText().contains("//?") }
    .map { it.relativeTo(loaderSourceDirectory).invariantSeparatorsPath }
val prepareLoaderJava by tasks.registering(Sync::class) {
    // Stonecutter scans the raw source tree, while compilation consumes the
    // selected branch output whenever a loader adapter has API conditionals.
    dependsOn(tasks.named("stonecutterPrepare"))
    from(loaderSourceDirectory) {
        exclude(conditionallyProcessedLoaderSources)
    }
    from(loaderPreparedJava)
    into(layout.buildDirectory.dir("generated/processed-loader-java"))
}
val jeiDependency = if (jeiApiAvailable) {
    """
    [[dependencies.better_lore]]
    modId = "jei"
    mandatory = false
    versionRange = "[$jeiVersion,)"
    ordering = "NONE"
    side = "CLIENT"

    """.trimIndent()
} else {
    ""
}

// ForgeGradle 7 resolves its deobfuscated Forge/Minecraft artifact from this
// generated repository rather than the public Forge Maven.
minecraft.mavenizer(repositories)

dependencies {
    implementation(minecraft.dependency("net.minecraftforge:forge:${property("deps.forge")}"))
    if (jeiApiAvailable) {
        compileOnly("mezz.jei:jei-$jeiMinecraftVersion-common-api:$jeiVersion")
    }
}

// ForgeGradle 7 uses Slime Launcher run options rather than the ForgeGradle 6
// run DSL. A `client` option on the main source set creates `runClient`.
// Explicitly configuring it keeps this target testable alongside Fabric and
// NeoForge without relying on IDE-generated launch profiles.
extensions.getByType<MinecraftExtensionForProject>().runs.create("client") {
    workingDir.set(layout.projectDirectory.dir("run"))
    // Forge's early display window can time out on an already active Windows
    // graphics session.  The game window itself is still created normally,
    // so bypass the optional bootstrap splash for reliable development runs.
    systemProperty("fml.earlyprogresswindow", "false")
    // Forge's module resolver needs this source set to be attached to the
    // Better Lore mod identity. Leaving it implicit gives the same classes a
    // second `main` module identity during the development launch.
    mods {
        create("better_lore") {
            source(sourceSets.main.get())
        }
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
    from(prepareLoaderJava)
    include { entry ->
        val file = entry.file.toPath().toAbsolutePath().normalize()
        preparedSourceRoots.any { root ->
            file.startsWith(root.get().asFile.toPath().toAbsolutePath().normalize())
        }
    }
}

tasks.named<Jar>("jar") {
    manifest.attributes["MixinConfigs"] = "better_lore.mixins.json"
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to project.property("deps.minecraft").toString(),
        "forge_loader_version" to project.property("forge_loader_version").toString(),
        "resource_pack_format" to project.property("minecraft.resource_pack_format").toString(),
        "resource_pack_minor" to project.property("minecraft.resource_pack_minor").toString(),
        "java_version" to project.property("java.version").toString(),
        "jei_dependency" to jeiDependency
    )
    inputs.properties(properties)
    filesMatching(listOf("META-INF/mods.toml", "better_lore.mixins.json", "pack.mcmeta")) {
        expand(properties)
    }
}
