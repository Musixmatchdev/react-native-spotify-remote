plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 31

    defaultConfig {
        minSdk = 21
        versionCode = 1
        versionName = "1.0"
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildTypes {
        getByName("release")  {
            isMinifyEnabled = false
            isDebuggable = false
        }

        getByName("debug") {
            isDebuggable = true
        }
    }

    @Suppress("UnstableApiUsage")
    lintOptions {
        disable("GradleCompatible")
    }

    @Suppress("UnstableApiUsage")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
    }
}


dependencies {
    implementation (files("libs/spotify-auth-release-1.2.3.aar"))
    implementation (files("libs/spotify-app-remote-release-0.8.0.aar"))

    implementation ("com.google.code.gson:gson:2.8.5") // needed by spotify-app-remote
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.1")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.4.1")

    //noinspection GradleDynamicVersion
    implementation ("com.facebook.react:react-native:+") // From node_modules
}