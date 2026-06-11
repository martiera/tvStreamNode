package com.tvstreamnode.tv

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.tvstreamnode.tv.util.Preferences

class PlaybackActivity : androidx.fragment.app.FragmentActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val channelUuid = intent.getStringExtra("channel_uuid") ?: run {
            finish(); return
        }
        val channelName = intent.getStringExtra("channel_name")
        val eventTitle = intent.getStringExtra("event_title")

        title = channelName ?: getString(R.string.play_channel)

        playerView = PlayerView(this).apply {
            setContentDescription(eventTitle ?: channelName ?: "")
            keepScreenOn = true
            useController = true
        }
        setContentView(playerView)

        val prefs = Preferences(this)
        val baseUrl = prefs.serverUrl.trimEnd('/')
        val hlsUrl = "$baseUrl/stream/channel/$channelUuid/hls"

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(hlsUrl))

        player = ExoPlayer.Builder(this).build().apply {
            setMediaSource(hlsSource)
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        finish()
                    }
                }
            })
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
