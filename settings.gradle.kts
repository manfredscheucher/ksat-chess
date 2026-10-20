rootProject.name = "sat-chess-kotlin"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":core")        // chess model, move generator, puzzle format, SAT encoder + solver driver (jvm, js)
include(":cli")         // JVM command line (solve / enumerate / write DIMACS)
include(":worker")      // Kotlin/JS Web Worker bundle that runs the solver off the UI thread
include(":composeApp")  // Compose UI (jvm desktop + js browser)

// SAT solvers from the SIBLING checkout ~/github/sat-solvers-kotlin (same wiring as luckySweep).
// :ksat is the facade (org.bytefred.ksat.facade.Ksat) over microsat/minisat/cadical/kissat.
include(":ksat-common")
project(":ksat-common").projectDir = file("../sat-solvers-kotlin/ksat-common")
include(":microsat")
project(":microsat").projectDir = file("../sat-solvers-kotlin/solver/microsat")
include(":minisat")
project(":minisat").projectDir = file("../sat-solvers-kotlin/solver/minisat")
include(":cadical")
project(":cadical").projectDir = file("../sat-solvers-kotlin/solver/cadical")
include(":kissat")
project(":kissat").projectDir = file("../sat-solvers-kotlin/solver/kissat")
include(":ksat")
project(":ksat").projectDir = file("../sat-solvers-kotlin/ksat")
