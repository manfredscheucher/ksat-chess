plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

// The Web Worker bundle: receives {puzzle, options} as JSON, runs PuzzleSolver.solve and posts each
// SolveEvent back as JSON. composeApp copies the webpack output (solver-worker.js) next to its
// index.html (see syncSolverWorker in composeApp/build.gradle.kts).
kotlin {
    js {
        browser {
            commonWebpackConfig { outputFileName = "solver-worker.js" }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":core"))
        }
    }
}
