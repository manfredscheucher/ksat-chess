import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
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
// index.html. A production/distribution build takes the minified production bundle, everything
// else the faster development bundle.
val workerVariant = if (gradle.startParameter.taskNames.any { "Production" in it || "Distribution" in it }) {
    "production"
} else {
    "development"
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
            // build-time GeneratedVersionInfo.kt (version + git commit/date), shown in About.
            kotlin.srcDir(layout.buildDirectory.dir("generated/versionInfo"))
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

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateVersionInfo)
}
