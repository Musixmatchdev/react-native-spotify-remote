package com.spotify.remote.sdk

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.spotify.protocol.types.Album
import com.spotify.protocol.types.Artist
import com.spotify.protocol.types.CrossfadeState
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.ListItem
import com.spotify.protocol.types.ListItems
import com.spotify.protocol.types.PlayerOptions
import com.spotify.protocol.types.PlayerRestrictions
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.PlayerContext
import com.spotify.protocol.types.Track
import com.spotify.sdk.android.auth.AuthorizationResponse
import java.util.Calendar

object Convert {
    fun toMap(response: AuthorizationResponse?): ReadableMap? {
        return if (response != null) {
            val map: WritableMap = Arguments.createMap()
            val expirationDate: Calendar = Calendar.getInstance()
            expirationDate.add(Calendar.SECOND, response.getExpiresIn())
            when (response.getType()) {
                TOKEN -> map.putString("accessToken", response.getAccessToken())
                CODE -> map.putString("accessToken", response.getCode())
            }
            map.putString("expirationDate", expirationDate.toString())
            map.putBoolean("expired", Calendar.getInstance().after(expirationDate))
            map
        } else {
            null
        }
    }

    fun toArray(listItems: ListItems): ReadableArray {
        val array: WritableArray = Arguments.createArray()
        for (item in listItems.items) {
            array.pushMap(toMap(item))
        }
        return array
    }

    fun toMap(item: ListItem): ReadableMap {
        val map: WritableMap = Arguments.createMap()
        map.putString("title", item.title)
        map.putString("subtitle", item.subtitle)
        map.putString("id", item.id)
        map.putString("uri", item.uri)
        map.putBoolean("playable", item.playable)

        // Not supported by android SDK, so put empty to maintain signature
        map.putArray("children", Arguments.createArray())
        map.putBoolean("container", item.hasChildren)
        map.putBoolean("availableOffline", false)
        return map
    }

    fun toItem(map: ReadableMap): ListItem {
        return ListItem(
            map.getString("id"),
            map.getString("uri"),
            ImageUri(""),
            map.getString("title"),
            map.getString("subtitle"),
            map.getBoolean("playable"),
            map.getBoolean("container")
        )
    }

    fun toMap(state: CrossfadeState): ReadableMap {
        val map: WritableMap = Arguments.createMap()
        map.putBoolean("enabled", state.isEnabled)
        map.putInt("duration", state.duration)
        return map
    }

    fun toMap(album: Album): ReadableMap {
        val map: WritableMap = Arguments.createMap()
        map.putString("name", album.name)
        map.putString("uri", album.uri)
        return map
    }

    fun toMap(artist: Artist): ReadableMap {
        val map: WritableMap = Arguments.createMap()
        map.putString("name", artist.name)
        map.putString("uri", artist.uri)
        return map
    }

    fun toMap(track: Track?): ReadableMap? {
        if (track == null) {
            return null
        }
        val map: WritableMap = Arguments.createMap()
        map.putDouble("duration", track.duration as Double)
        map.putBoolean("isPodcast", track.isPodcast)
        map.putBoolean("isEpisode", track.isEpisode)
        map.putString("uri", track.uri)
        map.putString("name", track.name)
        map.putMap("artist", toMap(track.artist))
        map.putMap("album", toMap(track.album))
        return map
    }

    fun toMap(options: PlayerOptions): ReadableMap {
        val map: WritableMap = Arguments.createMap()
        map.putDouble("repeatMode", options.repeatMode)
        map.putBoolean("isShuffling", options.isShuffling)
        return map
    }

    fun toMap(restrictions: PlayerRestrictions): ReadableMap {
        val map: WritableMap = Arguments.createMap()
        map.putBoolean("canRepeatContext", restrictions.canRepeatContext)
        map.putBoolean("canRepeatTrack", restrictions.canRepeatTrack)
        map.putBoolean("canSeek", restrictions.canSeek)
        map.putBoolean("canSkipNext", restrictions.canSkipNext)
        map.putBoolean("canSkipPrevious", restrictions.canSkipPrev)
        map.putBoolean("canToggleShuffle", restrictions.canToggleShuffle)
        return map
    }

    fun toMap(playerState: PlayerState): WritableMap {
        val map: WritableMap = Arguments.createMap()
        map.putBoolean("isPaused", playerState.isPaused)
        map.putDouble("playbackPosition", playerState.playbackPosition as Double)
        map.putDouble("playbackSpeed", playerState.playbackSpeed)
        map.putMap("playbackOptions", toMap(playerState.playbackOptions))
        map.putMap("playbackRestrictions", toMap(playerState.playbackRestrictions))
        map.putMap("track", toMap(playerState.track))
        return map
    }

    fun toMap(playerContext: PlayerContext): ReadableMap {
        val map: WritableMap = Arguments.createMap()
        map.putString("title", playerContext.title)
        map.putString("uri", playerContext.uri)
        return map
    }
}
