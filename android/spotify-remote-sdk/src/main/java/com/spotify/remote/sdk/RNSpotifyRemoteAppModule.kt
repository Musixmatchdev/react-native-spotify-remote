package com.spotify.remote.sdk

import android.util.Log
import androidx.arch.core.util.Function
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.protocol.client.CallResult
import com.spotify.protocol.client.ErrorCallback
import com.spotify.protocol.types.ListItem
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerContext
import com.spotify.protocol.types.PlayerState
import java.util.Stack
import java.util.HashMap

@ReactModule(name = "RNSpotifyRemoteAppRemote")
class RNSpotifyRemoteAppModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {
    private val reactContext: ReactApplicationContext
    private var authModule: RNSpotifyRemoteAuthModule? = null
    private var mSpotifyAppRemote: SpotifyAppRemote? = null
    private val mSpotifyRemoteConnectionListener: Connector.ConnectionListener
    private val mConnectPromises: Stack<Promise> = Stack<Promise>()
    private var mPlayerContextSubscription: Subscription<PlayerContext>? = null
    private var mPlayerStateSubscription: Subscription<PlayerState>? = null
    private val subscriptionHasListeners: HashMap<String, Boolean> = HashMap<String, Boolean>()

    init {
        this.reactContext = reactContext
        mSpotifyRemoteConnectionListener = object : ConnectionListener() {
            fun onConnected(spotifyAppRemote: SpotifyAppRemote?) {
                mSpotifyAppRemote = spotifyAppRemote
                handleEventSubscriptions()
                while (!mConnectPromises.empty()) {
                    val promise: Promise = mConnectPromises.pop()
                    promise.resolve(true)
                }
                sendEvent(EventNameRemoteConnected, null)
            }

            fun onFailure(throwable: Throwable?) {
                while (!mConnectPromises.empty()) {
                    val promise: Promise = mConnectPromises.pop()
                    if (throwable is NotLoggedInException) {
                        promise.reject(Error("Spotify connection failed: user is not logged in."))
                    } else if (throwable is UserNotAuthorizedException) {
                        promise.reject(Error("Spotify connection failed: user is not authorized."))
                    } else if (throwable is CouldNotFindSpotifyApp) {
                        promise.reject(Error("Spotify connection failed: could not find the Spotify app, it may need to be installed."))
                    } else {
                        promise.reject(throwable)
                    }
                }
                sendEvent(EventNameRemoteDisconnected, null)
            }
        }
    }

    private fun sendEvent(
        eventName: String,
        params: Object?
    ) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    @ReactMethod
    fun addListener(eventName: String?) {
        // Set up any upstream listeners or background tasks as necessary
    }

    @ReactMethod
    fun removeListeners(count: Integer?) {
        // Remove upstream listeners, stop unnecessary background tasks
    }

    @ReactMethod
    fun eventStartObserving(eventName: String?) {
        // Will be called when the event first listener is added.
        subscriptionHasListeners.put(eventName, true)
        handleEventSubscriptions()
    }

    @ReactMethod
    fun eventStopObserving(eventName: String?) {
        // Will be called when the event last listener is removed.
        subscriptionHasListeners.put(eventName, false)
        handleEventSubscriptions()
    }

    private fun handleEventSubscriptions() {
        if (mSpotifyAppRemote == null) return
        val hasContextListeners: Boolean = subscriptionHasListeners.get(
            EventNamePlayerContextChanged
        )
        val hasPlayerStateListeners: Boolean = subscriptionHasListeners.get(
            EventNamePlayerStateChanged
        )
        if (hasContextListeners != null && hasContextListeners) {
            if (mPlayerContextSubscription != null && !mPlayerContextSubscription.isCanceled()) {
                return  // already subscribed
            }
            mPlayerContextSubscription = mSpotifyAppRemote.getPlayerApi()
                .subscribeToPlayerContext()
                .setEventCallback { playerContext ->
                    val map: ReadableMap = Convert.toMap(playerContext)
                    sendEvent(
                        EventNamePlayerContextChanged,
                        map
                    )
                }
        } else {
            if (mPlayerContextSubscription != null && !mPlayerContextSubscription.isCanceled()) {
                mPlayerContextSubscription.cancel()
                mPlayerContextSubscription = null
            }
        }
        if (hasPlayerStateListeners != null && hasPlayerStateListeners) {
            if (mPlayerStateSubscription != null && !mPlayerStateSubscription.isCanceled()) {
                return  // already subscribed
            }
            mPlayerStateSubscription = mSpotifyAppRemote.getPlayerApi()
                .subscribeToPlayerState()
                .setEventCallback { playerContext ->
                    val map: ReadableMap = Convert.toMap(playerContext)
                    sendEvent(
                        EventNamePlayerStateChanged,
                        map
                    )
                }
        } else {
            if (mPlayerStateSubscription != null && !mPlayerStateSubscription.isCanceled()) {
                mPlayerStateSubscription.cancel()
                mPlayerStateSubscription = null
            }
        }
    }

    private fun <T> executeAppRemoteCall(
        apiCall: Function<SpotifyAppRemote, CallResult<T>>,
        resultCallback: CallResult.ResultCallback<T>,
        errorCallback: ErrorCallback
    ) {
        if (mSpotifyAppRemote == null) {
            errorCallback.onError(Error("Spotify App Remote not connected"))
        } else {
            apiCall.apply(mSpotifyAppRemote)
                .setResultCallback(resultCallback)
                .setErrorCallback(errorCallback)
        }
    }

    private fun getPlayerStateInternal(
        resultCallback: CallResult.ResultCallback<ReadableMap>,
        errorCallback: ErrorCallback
    ) {
        if (mSpotifyAppRemote == null) {
            errorCallback.onError(Error("Spotify App Remote not connected"))
        } else {
            mSpotifyAppRemote.getPlayerApi().getPlayerState()
                .setResultCallback { playerState ->
                    val map: WritableMap = Convert.toMap(playerState)
                    val eventMap: WritableMap = Convert.toMap(playerState)
                    sendEvent(
                        EventNamePlayerStateChanged,
                        eventMap
                    )
                    resultCallback.onResult(map)
                }
                .setErrorCallback(errorCallback)
        }
    }

    @ReactMethod
    fun connectWithoutAuth(
        token: String?,
        clientId: String?,
        redirectUri: String?,
        promise: Promise?
    ) {
        val paramsBuilder: ConnectionParams.Builder = Builder(clientId)
            .setRedirectUri(redirectUri)
        // With this method, users must be preauthorized to use the scope as we cannot display it
        if (mConnectPromises.empty()) {
            mConnectPromises.push(promise)
            val connectionParams: ConnectionParams = paramsBuilder.build()
            SpotifyAppRemote.connect(
                this.getReactApplicationContext(), connectionParams,
                mSpotifyRemoteConnectionListener
            )
        } else {
            mConnectPromises.push(promise)
        }
    }

    @ReactMethod
    fun connect(token: String?, promise: Promise) {
        // todo: looks like the android remote handles it's own auth (since it doesn't have a token)
        // todo: argument.  Can probably improve the experience for those who don't need a token
        // todo: and just want to connect the remote
        authModule = reactContext.getNativeModule(RNSpotifyRemoteAuthModule::class.java)
        val notAuthError = Error("Auth module has not been authorized.")
        if (authModule == null) {
            promise.reject(notAuthError)
            return
        }
        val paramsBuilder: ConnectionParams.Builder = authModule.getConnectionParamsBuilder()
        if (paramsBuilder == null) {
            promise.reject(notAuthError)
            return
        }

        // If we're already connecting then just push the promise onto stack to handle
        // when connected
        if (mConnectPromises.empty()) {
            mConnectPromises.push(promise)
            val connectionParams: ConnectionParams = paramsBuilder.build()
            SpotifyAppRemote.connect(
                this.getReactApplicationContext(), connectionParams,
                mSpotifyRemoteConnectionListener
            )
        } else {
            mConnectPromises.push(promise)
        }
    }

    @ReactMethod
    fun disconnect(promise: Promise) {
        if (mSpotifyAppRemote != null) {
            SpotifyAppRemote.disconnect(mSpotifyAppRemote)
            sendEvent(EventNameRemoteDisconnected, null)
        }
        promise.resolve(null)
    }

    @ReactMethod
    fun isConnectedAsync(promise: Promise) {
        if (mSpotifyAppRemote != null) {
            val isConnected: Boolean = mSpotifyAppRemote.isConnected()
            promise.resolve(isConnected)
        } else {
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun playUri(uri: String?, promise: Promise) {
        executeAppRemoteCall<Any>(
            Function<SpotifyAppRemote, CallResult<T>> { api -> api.getPlayerApi().play(uri) },
            CallResult.ResultCallback<T> { empty -> promise.resolve(null) },
            ErrorCallback { err -> promise.reject(err) }
        )
    }

    @ReactMethod
    fun playItem(map: ReadableMap?, promise: Promise) {
        executeAppRemoteCall<Any>(
            Function<SpotifyAppRemote, CallResult<T>> { api ->
                api.getContentApi().playContentItem(Convert.toItem(map))
            },
            CallResult.ResultCallback<T> { empty -> promise.resolve(null) },
            ErrorCallback { err -> promise.reject(err) }
        )
    }

    @ReactMethod
    fun playItemWithIndex(map: ReadableMap, index: Int, promise: Promise) {
        executeAppRemoteCall<Any>(
            Function<SpotifyAppRemote, CallResult<T>> { api ->
                api.getPlayerApi().skipToIndex(map.getString("uri"), index)
            },
            CallResult.ResultCallback<T> { empty -> promise.resolve(null) },
            ErrorCallback { err -> promise.reject(err) }
        )
    }

    @ReactMethod
    fun queueUri(uri: String, promise: Promise) {
        if (!uri.startsWith("spotify:track:")) {
            promise.reject(Error("Can only queue Spotify track uri's (i.e. spotify:track:<id>)"))
        }
        executeAppRemoteCall<Any>(
            Function<SpotifyAppRemote, CallResult<T>> { api -> api.getPlayerApi().queue(uri) },
            CallResult.ResultCallback<T> { empty -> promise.resolve(null) },
            ErrorCallback { err -> promise.reject(err) }
        )
    }

    @ReactMethod
    fun seek(ms: Float, promise: Promise) {
        val positionMs = ms.toLong()
        executeAppRemoteCall<Any>(
            Function<SpotifyAppRemote, CallResult<T>> { api ->
                api.getPlayerApi().seekTo(positionMs)
            },
            CallResult.ResultCallback<T> { empty -> promise.resolve(null) },
            ErrorCallback { err -> promise.reject(err) }
        )
    }

    @ReactMethod
    fun resume(promise: Promise) {
        executeAppRemoteCall<Any>(
            Function<SpotifyAppRemote, CallResult<T>> { api -> api.getPlayerApi().resume() },
            CallResult.ResultCallback<T> { empty -> promise.resolve(null) },
            ErrorCallback { err -> promise.reject(err) }
        )
    }

    @ReactMethod
    fun pause(promise: Promise) {
        executeAppRemoteCall<Any>(
            Function<SpotifyAppRemote, CallResult<T>> { api -> api.getPlayerApi().pause() },
            CallResult.ResultCallback<T> { empty -> promise.resolve(null) },
            ErrorCallback { err -> promise.reject(err) }
        )
    }

    @ReactMethod
    fun skipToNext(promise: Promise) {
        executeAppRemoteCall<Any>(
            Function<SpotifyAppRemote, CallResult<T>> { api -> api.getPlayerApi().skipNext() },
            CallResult.ResultCallback<T> { empty -> promise.resolve(null) },
            ErrorCallback { err -> promise.reject(err) }
        )
    }

    @ReactMethod
    fun skipToPrevious(promise: Promise) {
        executeAppRemoteCall<Any>(
            Function<SpotifyAppRemote, CallResult<T>> { api -> api.getPlayerApi().skipPrevious() },
            CallResult.ResultCallback<T> { empty -> promise.resolve(null) },
            ErrorCallback { err -> promise.reject(err) }
        )
    }

    @ReactMethod
    fun setShuffling(isShuffling: Boolean, promise: Promise) {
        executeAppRemoteCall<Any>(
            Function<SpotifyAppRemote, CallResult<T>> { api ->
                api.getPlayerApi().setShuffle(isShuffling)
            },
            CallResult.ResultCallback<T> { empty -> promise.resolve(null) },
            ErrorCallback { err -> promise.reject(err) }
        )
    }

    @ReactMethod
    fun setRepeatMode(repeatMode: Int, promise: Promise) {
        executeAppRemoteCall<Any>(
            Function<SpotifyAppRemote, CallResult<T>> { api ->
                api.getPlayerApi().setRepeat(repeatMode)
            },
            CallResult.ResultCallback<T> { empty -> promise.resolve(null) },
            ErrorCallback { err -> promise.reject(err) }
        )
    }

    @ReactMethod
    fun getPlayerState(promise: Promise) {
        getPlayerStateInternal(
            CallResult.ResultCallback<ReadableMap> { playerState -> promise.resolve(playerState) },
            ErrorCallback { error -> promise.reject(error) }
        )
    }

    @ReactMethod
    fun getRecommendedContentItems(options: ReadableMap, promise: Promise) {
        executeAppRemoteCall<Any>(
            Function<SpotifyAppRemote, CallResult<T>> { api ->
                api.getContentApi().getRecommendedContentItems(options.getString("type"))
            },
            CallResult.ResultCallback<T> { listItems -> promise.resolve(Convert.toArray(listItems)) },
            ErrorCallback { error -> promise.reject(error) }
        )
    }

    @ReactMethod
    fun getChildrenOfItem(itemMap: ReadableMap?, options: ReadableMap, promise: Promise) {
        executeAppRemoteCall<Any>(
            Function<SpotifyAppRemote, CallResult<T>> { api ->
                val perPage: Int = options.getInt("perPage")
                val offset: Int = options.getInt("offset")
                val listItem: ListItem = Convert.toItem(itemMap)
                api.getContentApi().getChildrenOfItem(listItem, perPage, offset)
            },
            CallResult.ResultCallback<T> { listItems -> promise.resolve(Convert.toArray(listItems)) },
            ErrorCallback { error -> promise.reject(error) }
        )
    }

    @ReactMethod
    fun getCrossfadeState(promise: Promise) {
        executeAppRemoteCall<Any>(
            Function<SpotifyAppRemote, CallResult<T>> { api ->
                api.getPlayerApi().getCrossfadeState()
            },
            CallResult.ResultCallback<T> { crossfadeState ->
                promise.resolve(
                    Convert.toMap(
                        crossfadeState
                    )
                )
            },
            ErrorCallback { error -> promise.reject(error) }
        )
    }

    @ReactMethod
    fun getRootContentItems(type: String?, promise: Promise) {
        Log.w(
            LOG_TAG,
            "getRootContentItems is not Implemented in Spotify Android SDK, returning []"
        )
        promise.resolve(Arguments.createArray())
    }

    @ReactMethod
    fun getContentItemForUri(uri: String?, promise: Promise) {
        Log.w(
            LOG_TAG,
            "getContentItemForUri is not Implemented in Spotify Android SDK, returning null"
        )
        promise.resolve(null)
    }

    @get:Override
    val name: String
        get() = "RNSpotifyRemoteAppRemote"

    companion object {
        private const val LOG_TAG = "RNSpotifyAppRemote"
        const val EventNamePlayerStateChanged = "playerStateChanged"
        const val EventNamePlayerContextChanged = "playerContextChanged"
        const val EventNameRemoteDisconnected = "remoteDisconnected"
        const val EventNameRemoteConnected = "remoteConnected"
    }
}
