plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.audiobpmeditor"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.audiobpmeditor"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true; buildConfig = true }
    lint { abortOnError = false; checkReleaseBuilds = false }
}
configurations.all {
    exclude(group = "com.android.support")
    resolutionStrategy {
        force("androidx.core:core:1.9.0")
        force("androidx.core:core-ktx:1.9.0")
        force("androidx.appcompat:appcompat:1.6.1")
        force("com.google.android.material:material:1.9.0")
        force("androidx.activity:activity:1.7.2")
        force("androidx.activity:activity-ktx:1.7.2")
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.7.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    // Media3 1.1.1 – https://mvnrepository.com/artifact/androidx.media3/media3-exoplayer/1.1.1
    implementation("androidx.media3:media3-exoplayer:1.1.1")
    implementation("androidx.media3:media3-common:1.1.1")
    implementation("androidx.media3:media3-ui:1.1.1")
    // WaveformSeekBar 5.0.2 – https://github.com/massoudss/waveformSeekBar
    implementation("com.github.massoudss:waveformSeekBar:5.0.2") {
        exclude(group = "com.android.support")
        exclude(group = "com.github.lincollincol")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
