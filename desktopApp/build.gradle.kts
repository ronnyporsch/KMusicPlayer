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

compose.desktop {
    application {
        mainClass = "de.ronnyporsch.open_rgb.kmusicplayer.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "de.ronnyporsch.open_rgb.kmusicplayer"
            packageVersion = "1.0.0"

            linux { iconFile.set(project.file("icons/icon.png")) }
            windows { iconFile.set(project.file("icons/icon.ico")) }
        }
    }
}