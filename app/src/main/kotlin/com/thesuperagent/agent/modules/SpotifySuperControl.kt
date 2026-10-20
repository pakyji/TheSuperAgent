package com.thesuperagent.agent.modules

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import com.thesuperagent.agent.SuperAgentApp
import com.thesuperagent.agent.core.LogStore
import com.thesuperagent.agent.services.NotificationReaderService
import kotlinx.coroutines.flow.MutableStateFlow

data class TrackInfo(
    val title: String,
    val artist: String,
    val album: String,
    val isPlaying: Boolean,
    val art: Bitmap?,          // nullable + downsampled — OOM fix
)

/**
 * Spotify Super Control — official MediaSession API only.
 */
object SpotifySuperControl {

    val currentTrack = MutableStateFlow<TrackInfo?>(null)
    private var controller: MediaController? = null

    private fun sessionManager(): MediaSessionManager? = try {
        SuperAgentApp.instance.getSystemService(MediaSessionManager::class.java)
    } catch (t: Throwable) { null }

    /** Graceful permission fail: returns false instead of crashing without listener access. */
    fun refresh(): Boolean {
        val msm = sessionManager() ?: return false
        val cn = ComponentName(
            SuperAgentApp.instance,
            com.thesuperagent.agent.services.NotificationReaderService::class.java
        )
        val sessions = try {
            msm.getActiveSessions(cn)
        } catch (t: Throwable) {
            return false
        }
        controller = sessions.firstOrNull { it.packageName == "com.spotify.music" }
            ?: sessions.firstOrNull()
        controller?.let { c ->
            val meta = c.metadata
            val playing = c.playbackState?.state ==
                android.media.session.PlaybackState.STATE_PLAYING
            val art = runCatching {
                // downsample to max 256px to avoid OOM on big album art
                val raw = meta?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                raw?.let { b ->
                    val scale = (256f / maxOf(b.width, b.height)).coerceAtMost(1f)
                    if (scale < 1f)
                        Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true)
                    else b
                }
            }.getOrNull()
            currentTrack.value = TrackInfo(
                title = meta?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                artist = meta?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                album = meta?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                isPlaying = playing,
                art = art,
            )
        } ?: run { currentTrack.value = null }
        return controller != null
    }

    private fun key(code: Int) {
        val c = controller ?: return
        runCatching {
            c.dispatchMediaButtonEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, code))
            c.dispatchMediaButtonEvent(
                KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    fun play() { controller?.transportControls?.play() ?: key(KeyEvent.KEYCODE_MEDIA_PLAY) }
    fun pause() { controller?.transportControls?.pause() ?: key(KeyEvent.KEYCODE_MEDIA_PAUSE) }
    fun next() { controller?.transportControls?.skipToNext() ?: key(KeyEvent.KEYCODE_MEDIA_NEXT) }
    fun prev() { controller?.transportControls?.skipToPrevious() ?: key(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }

    fun seek(pos: Long) { controller?.transportControls?.seekTo(pos) }

    fun playPlaylist(query: String): Boolean {
        val ctx = SuperAgentApp.instance
        return runCatching {
            val uri = Uri.parse("spotify:search:$query")
            val i = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            true
        }.getOrElse {
            runCatching {
                val i = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://open.spotify.com/search/$query"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                true
            }.getOrElse { false }
        }
    }
}
