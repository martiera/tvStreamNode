package com.tvstreamnode.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.tvstreamnode.tv.util.Preferences

class PlaybackActivity : androidx.fragment.app.FragmentActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var fallbackTried = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val channelUuid = intent.getStringExtra("channel_uuid") ?: run {
            finish(); return
        }
        val channelName = intent.getStringExtra("channel_name")

        title = channelName ?: getString(R.string.play_channel)

        playerView = PlayerView(this).apply {
            keepScreenOn = true
            useController = true
        }
        setContentView(playerView)

        val prefs = Preferences(this)
        val baseUrl = prefs.serverUrl.trimEnd('/')
        val tsUrl = "$baseUrl/stream/channel/$channelUuid"
        val hlsUrl = "$baseUrl/stream/channel/$channelUuid/hls"

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        player = ExoPlayer.Builder(this).build().apply {
            playWhenReady = true

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        finish()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!fallbackTried) {
                        fallbackTried = true
                        // Try HLS as fallback (some servers have it configured)
                        val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(hlsUrl))
                        setMediaSource(hlsSource, true)
                        prepare()
                        playWhenReady = true
                    }
                }
            })

            // Default: raw MPEG-TS (video/mp2t) — all Tvheadend servers support this
            val tsSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(tsUrl))
            setMediaSource(tsSource)
            prepare()
        }

        playerView?.player = player
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        playerView = null
    }
}
