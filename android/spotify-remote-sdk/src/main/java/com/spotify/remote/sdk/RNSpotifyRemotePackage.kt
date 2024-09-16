package com.spotify.remote.sdk

import java.util.Arrays
import java.util.Collections
import java.util.List
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
import com.facebook.react.bridge.JavaScriptModule

class RNSpotifyRemotePackage : ReactPackage {
    @Override
    fun createNativeModules(reactContext: ReactApplicationContext?): List<NativeModule> {
        return Arrays.< NativeModule > asList < NativeModule ? > RNSpotifyRemoteAuthModule(
            reactContext
        ), RNSpotifyRemoteAppModule(reactContext))
    }

    // Deprecated from RN 0.47
    fun createJSModules(): List<Class<out JavaScriptModule?>> {
        return Collections.emptyList()
    }

    @Override
    fun createViewManagers(reactContext: ReactApplicationContext?): List<ViewManager> {
        return Collections.emptyList()
    }
}