import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

val appVersion: String by rootProject.extra

compose.desktop {
    application {
        mainClass = "de.ronnyporsch.open_rgb.kmusicplayer.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "de.ronnyporsch.open_rgb.kmusicplayer"
            // Installers only accept <major>.<minor>.<patch>
            packageVersion = appVersion.substringBefore('-')
            // From suggestRuntimeModules; the bundled JRE only contains these plus the Compose defaults
            modules("java.instrument", "java.management", "java.prefs", "jdk.security.auth", "jdk.unsupported")

            linux { iconFile.set(project.file("icons/icon.png")) }
            windows { iconFile.set(project.file("icons/icon.ico")) }
        }
    }
}