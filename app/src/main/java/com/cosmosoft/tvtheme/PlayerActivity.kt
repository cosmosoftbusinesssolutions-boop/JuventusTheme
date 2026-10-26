package com.cosmosoft.tvtheme

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cosmosoft.tvtheme.plex.PlexItem
import com.cosmosoft.tvtheme.plex.PlexServer
import com.cosmosoft.tvtheme.plex.PlexSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Progress reports must outlive the activity: the final "stopped" one is sent as it closes. */
private val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Plays a Plex movie or episode. It tries the original file first and falls back to an HLS stream
 * converted by the server, and reports progress so Plex can resume and update Continue watching.
 */
class PlayerActivity : ComponentActivity() {
    private lateinit var session: PlexSession
    private lateinit var server: PlexServer
    private lateinit var ratingKey: String
    private var player: ExoPlayer? = null
    private var knownDurationMs = 0L
    private var usingTranscode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = PlexSession(this)
        server = session.server ?: return finish()
        ratingKey = intent.getStringExtra(EXTRA_RATING_KEY) ?: return finish()
        knownDurationMs = intent.getLongExtra(EXTRA_DURATION, 0)

        val player = ExoPlayer.Builder(this).build().also { player = it }
        player.addListener(listener)
        setContentView(
            PlayerView(this).apply {
                this.player = player
                keepScreenOn = true
                requestFocus()
            },
        )

        val start = intent.getLongExtra(EXTRA_START, 0)
        val partKey = intent.getStringExtra(EXTRA_PART_KEY)
        if (partKey != null) {
            play(MediaItem.fromUri(session.api.directPlayUrl(server, partKey)), start)
        } else {
            playTranscoded(start)
        }

        lifecycleScope.launch {
            while (true) {
                delay(10_000)
                if (player.isPlaying) report("playing")
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val position = player?.currentPosition ?: 0
            if (!usingTranscode) {
                // The TV can't decode the original file; ask the server to convert it.
                playTranscoded(position)
            } else {
                Toast.makeText(this@PlayerActivity, R.string.player_error, Toast.LENGTH_LONG).show()
                finish()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            report(if (isPlaying) "playing" else "paused")
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                report("stopped", position = durationMs())
                finish()
            }
        }
    }

    private fun playTranscoded(startMs: Long) {
        usingTranscode = true
        val item = MediaItem.Builder()
            .setUri(session.api.transcodeUrl(server, ratingKey))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        play(item, startMs)
    }

    private fun play(item: MediaItem, startMs: Long) {
        val player = player ?: return
        player.setMediaItem(item, startMs)
        player.prepare()
        player.playWhenReady = true
    }

    private fun durationMs(): Long = player?.duration?.takeIf { it > 0 } ?: knownDurationMs

    private fun report(state: String, position: Long = player?.currentPosition ?: 0) {
        val duration = durationMs()
        reportScope.launch {
            runCatching { session.api.reportTimeline(server, ratingKey, state, position, duration) }
        }
    }

    override fun onStop() {
        player?.let {
            report("stopped")
            it.pause()
        }
        super.onStop()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_RATING_KEY = "rating_key"
        private const val EXTRA_PART_KEY = "part_key"
        private const val EXTRA_START = "start_ms"
        private const val EXTRA_DURATION = "duration_ms"

        fun intent(context: Context, item: PlexItem, startMs: Long): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_RATING_KEY, item.ratingKey)
                .putExtra(EXTRA_PART_KEY, item.partKey)
                .putExtra(EXTRA_START, startMs)
                .putExtra(EXTRA_DURATION, item.durationMs ?: 0L)
    }
}
