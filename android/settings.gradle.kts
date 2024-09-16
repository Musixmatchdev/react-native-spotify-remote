pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven {
            // All of React Native (JS, Obj-C sources, Android binaries) is installed from npm
            url = uri("$rootDir/../node_modules/react-native/android")
        }
    }
}

rootProject.name = "Spotify Remote SDK"
include(":spotify-remote-sdk")