plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

// JVM command line: solve a puzzle file, SAT perft, run a puzzle directory, write DIMACS.
// Run it via scripts/runCli.sh (installDist, then the installed binary).
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass.set("org.bytefred.satchess.cli.MainKt")
    applicationName = "sat-chess"
    // Large 8-ply instances need some heap; stays well below the 16 GB machine limit.
    applicationDefaultJvmArgs = listOf("-Xmx3g")
}
