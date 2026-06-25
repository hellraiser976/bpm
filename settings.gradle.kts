// settings.gradle.kts - AudioBpmEditor
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // JitPack - for waveformSeekBar, Amplituda, LAME
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "AudioBpmEditor"
include(":app")
