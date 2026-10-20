// All plugins declared here with `apply false` so their versions are fixed ONCE on the shared build
// classpath; modules apply them without a version.
plugins {
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kotlinJvm) apply false
}
