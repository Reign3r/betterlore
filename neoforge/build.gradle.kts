import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import net.neoforged.moddevgradle.dsl.NeoForgeExtension

plugins {
    id("net.neoforged.moddev") apply false
    id("multiloader-common")
}

base.archivesName.set("better-lore-neoforge-${project.name}")
val targetVersion = project.path.substringAfterLast(':')
val usesLegacyUserdev = targetVersion == "1.20.5"
if (!usesLegacyUserdev) {
    apply(plugin = "net.neoforged.moddev")
}
val commonNode = rootProject.project(":common:$targetVersion")
val commonPreparedJava = commonNode.layout.buildDirectory.dir("stonecutter-cache/sources/main/java")
val commonPrepareTask = ":common:$targetVersion:stonecutterPrepare"
val commonSourceDirectory = rootProject.file("common/src/main/java")
val jeiVersion = project.findProperty("deps.jei_neoforge")?.toString()
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
val loaderSourceDirectory = rootProject.file("neoforge/src/main/java")
val loaderPreparedJava = layout.buildDirectory.dir("stonecutter-cache/sources/main/java")
val conditionallyProcessedLoaderSources = fileTree(loaderSourceDirectory) {
    include("**/*.java")
}.files
    .filter { it.readText().contains("//?") }
    .map { it.relativeTo(loaderSourceDirectory).invariantSeparatorsPath }
val prepareLoaderJava by tasks.registering(Sync::class) {
    // Stonecutter must scan the raw source set, while Java compilation must
    // consume the processed branch output so the active target never sees both
    // sides of a compatibility block.
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
    type = "optional"
    versionRange = "[$jeiVersion,)"
    ordering = "NONE"
    side = "CLIENT"

    """.trimIndent()
} else {
    ""
}

if (!usesLegacyUserdev) {
    extensions.configure<NeoForgeExtension>("neoForge") {
        // The no-recompilation pipeline is sufficient for compiling and
        // packaging this mod and avoids a costly, memory-heavy Minecraft
        // decompile for every Stonecutter target. It is also the ModDevGradle
        // CI-default pipeline.
        enable {
            version = property("deps.neoforge").toString()
            setDisableRecompilation(true)
        }

        // ModDevGradle does not create launch profiles implicitly. Register the
        // Better Lore source set and an explicit client run so every modern
        // NeoForge Stonecutter target exposes the same `runClient` workflow as
        // Fabric and Forge.
        mods {
            create("better_lore") {
                sourceSet(sourceSets.main.get())
            }
        }
        runs {
            create("client") {
                client()
                gameDirectory.set(layout.projectDirectory.dir("run"))
                loadedMods.add(mods.getByName("better_lore"))
            }
        }
    }
}

dependencies {
    if (jeiApiAvailable) {
        compileOnly("mezz.jei:jei-$jeiMinecraftVersion-common-api:$jeiVersion")
    }
    testImplementation(files(sourceSets.main.get().compileClasspath))
    testRuntimeOnly(files(sourceSets.main.get().runtimeClasspath))
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
        "neoforge_version" to project.property("deps.neoforge").toString(),
        "neoforge_loader_version" to project.property("neoforge_loader_version").toString(),
        "resource_pack_format" to project.property("minecraft.resource_pack_format").toString(),
        "resource_pack_minor" to project.property("minecraft.resource_pack_minor").toString(),
        "java_version" to project.property("java.version").toString(),
        "jei_dependency" to jeiDependency
    )
    inputs.properties(properties)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "better_lore.mixins.json", "pack.mcmeta")) {
        expand(properties)
    }
}

// NeoForge 20.5 publishes the Userdev format consumed by NeoGradle 7, whose
// public plugin API predates Gradle 9. Modern loader targets remain on
// ModDevGradle/Gradle 9; this target is exported to an isolated Gradle 8.14
// Userdev build after Stonecutter has selected its source branches.
if (usesLegacyUserdev) {
    val legacyProjectDirectory = rootProject.file("legacy/neoforge-1.20.5")
    val legacySourceDirectory = layout.buildDirectory.dir("legacy-userdev-src")
    val legacyWrapperDirectory = legacyProjectDirectory.resolve("gradle/wrapper")
    val legacyBuildDirectory = File(System.getProperty("java.io.tmpdir"), "better-lore-neoforge-1.20.5")
    val toolchains = extensions.getByType<JavaToolchainService>()
    val legacyJavaLauncher = toolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }

    val prepareLegacyUserdevSources by tasks.registering(Sync::class) {
        dependsOn(prepareSharedJava, prepareLoaderJava)
        from(prepareSharedJava) { into("main/java") }
        from(prepareLoaderJava) { into("main/java") }
        from(rootProject.file("common/src/main/resources")) { into("main/resources") }
        from(rootProject.file("neoforge/src/main/resources")) { into("main/resources") }
        from(rootProject.file("common/src/test/java")) { into("test/java") }
        into(legacySourceDirectory)
    }

    val prepareLegacyUserdevWrapper by tasks.registering(Copy::class) {
        from(rootProject.file("gradle/wrapper/gradle-wrapper.jar"))
        into(legacyWrapperDirectory)
    }

    fun registerLegacyUserdevInvocation(name: String, gradleTask: String) = tasks.register<Exec>(name) {
        dependsOn(prepareLegacyUserdevSources, prepareLegacyUserdevWrapper)
        workingDir(legacyProjectDirectory)
        executable = legacyJavaLauncher.get().executablePath.asFile.absolutePath
        args(
            "-Dorg.gradle.appname=better-lore-legacy-userdev",
            "-classpath",
            legacyWrapperDirectory.resolve("gradle-wrapper.jar").absolutePath,
            "org.gradle.wrapper.GradleWrapperMain",
            gradleTask,
            "--no-daemon",
            "--max-workers=1",
            "--no-configuration-cache",
            "--console=plain"
        )
        if (gradle.startParameter.isRerunTasks) {
            args("--rerun-tasks")
        }
    }

    registerLegacyUserdevInvocation("legacyUserdevTest", "test")
    val legacyUserdevBuild = registerLegacyUserdevInvocation("legacyUserdevBuild", "build")
    tasks.register<Copy>("legacyUserdevReleaseJar") {
        dependsOn(legacyUserdevBuild)
        from(legacyBuildDirectory.resolve("libs")) {
            include("better-lore-neoforge-1.20.5-${project.version}.jar")
        }
        into(layout.buildDirectory.dir("libs"))
    }
}
