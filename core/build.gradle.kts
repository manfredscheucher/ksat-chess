plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(21)
    jvm()
    js { browser(); nodejs() }

    sourceSets {
        commonMain.dependencies {
            api(project(":ksat"))
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// Slow tests (deep perft, 8-ply puzzles) only run with -PslowTests; default runs stay a few seconds.
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    systemProperty("slowTests", (project.findProperty("slowTests") != null).toString())
}
