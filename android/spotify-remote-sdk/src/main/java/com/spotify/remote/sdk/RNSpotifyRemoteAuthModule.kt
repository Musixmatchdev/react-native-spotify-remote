package com.spotify.remote.sdk

import android.app.Activity
import android.content.Intent
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.annotations.ReactModule
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import java.util.ArrayList

@ReactModule(name = "RNSpotifyRemoteAuth")
class RNSpotifyRemoteAuthModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), ActivityEventListener {
    private val reactContext: ReactApplicationContext
    private var authPromise: Promise? = null
    private var mAuthResponse: AuthorizationResponse? = null
    private var mConfig: ReadableMap? = null
    private var mConnectionParamsBuilder: ConnectionParams.Builder? = null
    val connectionParamsBuilder: ConnectionParams.Builder?
        get() = mConnectionParamsBuilder

    init {
        reactContext.addActivityEventListener(this)
        this.reactContext = reactContext
    }

    @ReactMethod
    fun authorize(config: ReadableMap?, promise: Promise?) {
        mConfig = config
        val clientId: String = mConfig.getString("clientID")
        val redirectUri: String = mConfig.getString("redirectURL")
        val showDialog: Boolean = mConfig.getBoolean("showDialog")
        val scopes = convertScopes(mConfig)
        val responseType: AuthorizationResponse.Type =
            if (mConfig.hasKey("authType")) AuthorizationResponse.Type.valueOf(mConfig.getString("authType")) else AuthorizationResponse.Type.TOKEN
        mConnectionParamsBuilder = Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(showDialog)
        authPromise = promise
        val builder: AuthorizationRequest.Builder = Builder(
            clientId,
            responseType,
            redirectUri
        )
        builder.setScopes(scopes)
        val request: AuthorizationRequest = builder.build()
        AuthorizationClient.openLoginActivity(getCurrentActivity(), REQUEST_CODE, request)
    }

    @Override
    fun onNewIntent(intent: Intent?) {
    }

    @Override
    fun onActivityResult(activity: Activity?, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE) {
            val response: AuthorizationResponse = AuthorizationClient.getResponse(resultCode, data)
            when (response.getType()) {
                TOKEN, CODE -> if (authPromise != null) {
                    mAuthResponse = response
                    authPromise.resolve(Convert.toMap(response))
                }

                ERROR -> if (authPromise != null) {
                    val code: String = response.getCode()
                    val error: String = response.getError()
                    authPromise.reject(code, error)
                    mConnectionParamsBuilder = null
                }

                else -> if (authPromise != null) {
                    val code = "500"
                    val error = "Cancelled"
                    authPromise.reject(code, error)
                    mConnectionParamsBuilder = null
                }
            }
        }
    }

    @ReactMethod
    fun getSession(promise: Promise) {
        promise.resolve(Convert.toMap(mAuthResponse))
    }

    @ReactMethod
    fun endSession(promise: Promise) {
        mAuthResponse = null
        mConnectionParamsBuilder = null
        mConfig = null
        AuthorizationClient.clearCookies(this.getReactApplicationContext())
        val remoteModule: RNSpotifyRemoteAppModule = reactContext.getNativeModule(
            RNSpotifyRemoteAppModule::class.java
        )
        if (remoteModule != null) {
            remoteModule.disconnect(promise)
        } else {
            promise.resolve(null)
        }
    }

    fun convertScopes(config: ReadableMap?): Array<String> {
        val arrayOfScopes: ReadableArray = config.getArray("scopes")
        val scopesArrayList: ArrayList<String> = ArrayList<String>()
        for (i in 0 until arrayOfScopes.size()) {
            val scope: String = arrayOfScopes.getString(i)
            scopesArrayList.add(scope)
        }
        return scopesArrayList.toArray(arrayOfNulls<String>(scopesArrayList.size()))
    }

    @get:Override
    val name: String
        get() = "RNSpotifyRemoteAuth"

    companion object {
        private const val REQUEST_CODE = 1337
    }
}
