import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import javax.inject.Inject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// ── Web Worker bundle (:worker) ─────────────────────────────────────────────
// The solver runs in a Web Worker on the web (doc/spec.md section 7). The :worker module is its own
// Kotlin/JS executable (webpack output "solver-worker.js"); this Sync copies that bundle into a
// generated resources dir that is registered as a jsMain resource dir, so both the dev-server
// (jsBrowserDevelopmentRun) and the distribution (jsBrowserDistribution) serve it next to
// index.html. Every build takes the minified production bundle (deterministic: build, assemble and
// jsBrowserDistribution all ship the same worker); scripts/runWeb.sh passes -PworkerVariant=development
// for the faster development bundle in the dev-server.
val workerVariant = providers.gradleProperty("workerVariant").orElse("production").get()
require(workerVariant == "production" || workerVariant == "development") {
    "workerVariant must be 'production' or 'development', got '$workerVariant'"
}
val workerWebpackTask = ":worker:jsBrowser${workerVariant.replaceFirstChar { it.uppercase() }}Webpack"
val syncSolverWorker = tasks.register<Sync>("syncSolverWorker") {
    dependsOn(workerWebpackTask)
    from(rootProject.layout.projectDirectory.dir("worker/build/kotlin-webpack/js/${workerVariant}Executable")) {
        include("solver-worker.js", "solver-worker.js.map")
    }
    into(layout.buildDirectory.dir("generated/workerResources"))
}

kotlin {
    jvmToolchain(21)

    jvm()

    js {
        browser {
            commonWebpackConfig { outputFileName = "composeApp.js" }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain {
            // build-time GeneratedVersionInfo.kt (version + git commit/date) and GeneratedLicenses.kt
            // (license texts), both shown in About.
            kotlin.srcDir(layout.buildDirectory.dir("generated/versionInfo"))
            kotlin.srcDir(layout.buildDirectory.dir("generated/licenses"))
        }
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            // Main (AWT/Swing EDT) dispatcher for viewModelScope on desktop.
            implementation(libs.kotlinx.coroutines.swing)
        }
        jsMain {
            resources.srcDir(syncSolverWorker)
        }
        jsMain.dependencies {
            implementation(libs.kotlin.browser)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.desktop.uiTestJUnit4)
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.bytefred.satchess.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "sat-chess"
            packageVersion = project.version.toString()
        }
    }
}

// Screenshots of the main screen (desktop + phone width) for review, written by the jvmTest
// ScreenshotTest to build/screenshots/.
tasks.withType<Test>().configureEach {
    systemProperty("screenshotDir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
}

// ── build-time version info (GeneratedVersionInfo.kt) ───────────────────────
// Same task as ktoy / OpenYarnStash: the version number comes from gradle.properties, commit sha /
// dirty / commit-date from git. Written into commonMain so the web build can show it in About.
abstract class GenerateVersionInfo @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory @get:Optional @get:PathSensitive(PathSensitivity.NONE)
    abstract val gitDir: DirectoryProperty
    @get:Input abstract val versionName: Property<String>
    @get:Input abstract val packageName: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val version = versionName.get()
        var sha = "unknown"
        var isDirty = "unknown"
        var commitDate = "unknown"
        val wd = gitDir.asFile.orNull?.parentFile ?: project.rootDir
        if (gitDir.asFile.orNull?.exists() == true) {
            try {
                fun git(vararg args: String): String {
                    val out = ByteArrayOutputStream()
                    execOps.exec {
                        workingDir = wd
                        commandLine(listOf("git", *args))
                        standardOutput = out
                        isIgnoreExitValue = false
                    }
                    return out.toString().trim()
                }
                sha = git("rev-parse", "--short", "HEAD")
                commitDate = git("log", "-1", "--format=%cI", "--date=iso-strict")
                isDirty = if (git("status", "--porcelain", "--untracked-files=no")
                        .isEmpty()) "clean" else "dirty"
            } catch (e: Exception) {
                // git unavailable or no commit yet: keep the defaults
            }
        }
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        df.timeZone = TimeZone.getTimeZone("UTC")
        val buildDate = df.format(Date())
        val commitYear = commitDate.take(4).toIntOrNull() ?: 0
        val dir = outputDir.get().asFile.apply { mkdirs() }
        dir.resolve("GeneratedVersionInfo.kt").writeText(
            """
            package ${packageName.get()}

            /**
             * AUTO-GENERATED — DO NOT EDIT.
             * Generated at build time by the generateVersionInfo Gradle task.
             * Change the version in gradle.properties (version=X.Y.Z); the rest
             * comes from git. Shown in About.
             */
            object GeneratedVersionInfo {
                const val VERSION: String = "$version"
                const val COMMIT_SHA: String = "$sha"
                const val COMMIT_DATE: String = "$commitDate"
                const val COMMIT_YEAR: Int = $commitYear
                const val IS_DIRTY: String = "$isDirty"
                const val BUILD_DATE: String = "$buildDate"
            }
            """.trimIndent() + "\n"
        )
    }
}

val generateVersionInfo = tasks.register("generateVersionInfo", GenerateVersionInfo::class.java) {
    val gitDirFile = rootProject.layout.projectDirectory.dir(".git")
    if (gitDirFile.asFile.exists()) gitDir.set(gitDirFile)
    versionName.set(project.provider { project.version.toString() })
    packageName.set("org.bytefred.satchess")
    outputDir.set(layout.buildDirectory.dir("generated/versionInfo"))
}

// ── build-time license notices (GeneratedLicenses.kt) ───────────────────────
// The About dialog shows the notices a binary distribution must carry. The texts are read from the
// license files themselves (single source of truth, nothing hand-copied): this project's LICENSE, the
// piece graphics notice, the ksat solver ports (LICENSE + licenses/ of the sibling sat-solvers-kotlin
// checkout) and the puzzle notice.
@CacheableTask
abstract class GenerateLicenses : DefaultTask() {
    /** Notice titles, in display order; parallel to [sources]. */
    @get:Input abstract val titles: ListProperty<String>
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sources: ConfigurableFileCollection
    @get:Input abstract val packageName: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val files = sources.files.toList()
        val names = titles.get()
        require(files.size == names.size) { "titles and sources differ in length" }
        fun literal(text: String) = buildString {
            append('"')
            for (c in text) when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\r' -> Unit
                '\t' -> append("\\t")
                else -> append(c)
            }
            append('"')
        }
        val entries = names.zip(files).joinToString(",\n") { (title, file) ->
            "        LicenseNotice(${literal(title)}, ${literal(file.readText(Charsets.UTF_8).trimEnd())})"
        }
        val dir = outputDir.get().asFile.apply { mkdirs() }
        dir.resolve("GeneratedLicenses.kt").writeText(
            """
            |package ${packageName.get()}
            |
            |/** One license notice: what it covers, and the full text. */
            |class LicenseNotice(val title: String, val text: String)
            |
            |/**
            | * AUTO-GENERATED — DO NOT EDIT.
            | * Generated at build time by the generateLicenses Gradle task from the license files.
            | */
            |object GeneratedLicenses {
            |    val NOTICES: List<LicenseNotice> = listOf(
            |$entries,
            |    )
            |}
            |""".trimMargin(),
            Charsets.UTF_8,
        )
    }
}

val satSolversDir = project(":ksat").projectDir.parentFile
val licenseNotices = listOf(
    "sat-chess-kotlin (MIT)" to rootProject.file("LICENSE"),
    "Chess piece graphics: cburnett set (BSD 3-clause)" to file("NOTICE-pieces.md"),
    "SAT solvers: ksat (MIT, Kotlin ports)" to satSolversDir.resolve("LICENSE"),
    "MicroSAT (MIT)" to satSolversDir.resolve("licenses/microsat-LICENSE.txt"),
    "MiniSat (MIT)" to satSolversDir.resolve("licenses/minisat-LICENSE.txt"),
    "CaDiCaL (MIT)" to satSolversDir.resolve("licenses/cadical-LICENSE.txt"),
    "kissat (MIT)" to satSolversDir.resolve("licenses/kissat-LICENSE.txt"),
    "Example puzzles (not covered by the MIT license; puzzles/NOTICE.md)" to rootProject.file("puzzles/NOTICE.md"),
)
val generateLicenses = tasks.register<GenerateLicenses>("generateLicenses") {
    titles.set(licenseNotices.map { it.first })
    sources.from(licenseNotices.map { it.second })
    packageName.set("org.bytefred.satchess")
    outputDir.set(layout.buildDirectory.dir("generated/licenses"))
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateVersionInfo, generateLicenses)
}
