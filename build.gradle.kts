plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}
// App version from the latest `v<major>.<minor>.<patch>` git tag, overridable with -PappVersion=<version>
val appVersion: String by extra(
    providers.gradleProperty("appVersion").orElse(
        providers.exec {
            commandLine("git", "describe", "--tags", "--match", "v[0-9]*", "--abbrev=0")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim().removePrefix("v").ifEmpty { "1.0.0" } }
    ).get()
)
