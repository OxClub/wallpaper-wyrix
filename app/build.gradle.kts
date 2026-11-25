import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(FileInputStream(localPropsFile))
}
// CI passes the key as a Gradle property; local dev reads it from local.properties
val pixabayApiKey: String = (project.findProperty("PIXABAY_API_KEY") as String?)
    ?: (localProps.getProperty("pixabay.api.key") ?: "")
// Optional. Free key from https://www.pexels.com/api/ . Leave empty to skip Pexels.
val pexelsApiKey: String = (project.findProperty("PEXELS_API_KEY") as String?)
    ?: (localProps.getProperty("pexels.api.key") ?: "")
// Unity Ads. Get these from Unity Dashboard -> Monetization -> your project.
// CI passes Gradle properties; local dev reads local.properties. Blank Game ID = ads disabled.
val unityGameId: String = (project.findProperty("UNITY_GAME_ID") as String?)
    ?: (localProps.getProperty("unity.game.id") ?: "800452515")
val unityBannerId: String = (project.findProperty("UNITY_BANNER_ID") as String?)
    ?: (localProps.getProperty("unity.banner.id") ?: "BP_Banner_Android")

android {
    namespace = "com.wyrix.wallpaper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wyrix.wallpaper"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.1.0"

        buildConfigField("String", "PIXABAY_API_KEY", "\"$pixabayApiKey\"")
        buildConfigField("String", "PEXELS_API_KEY", "\"$pexelsApiKey\"")
        buildConfigField("String", "UNITY_GAME_ID", "\"$unityGameId\"")
        buildConfigField("String", "UNITY_BANNER_ID", "\"$unityBannerId\"")
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("RELEASE_STORE_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.activity:activity-ktx:1.9.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Pinch-to-zoom full screen viewer
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Ads (Unity Ads). Check https://docs.unity.com/ads for the latest 4.x version.
    implementation("com.unity3d.ads:unity-ads:4.12.5")
}
