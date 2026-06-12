package com.tvstreamnode.tv

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.tvstreamnode.tv.data.api.RetrofitClient
import com.tvstreamnode.tv.data.model.Channel
import com.tvstreamnode.tv.data.model.EpgEvent
import com.tvstreamnode.tv.data.repository.ChannelRepository
import com.tvstreamnode.tv.data.repository.EpgRepository
import com.tvstreamnode.tv.util.EpgCache
import com.tvstreamnode.tv.util.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlaybackActivity : androidx.fragment.app.FragmentActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var fallbackTried = false
    private var dataSourceFactory: DefaultHttpDataSource.Factory? = null
    private var currentUrl: String = ""
    private var prefs: Preferences? = null

    private var channels: List<Channel> = emptyList()
    private var currentChannelIndex: Int = -1

    // Overlay views
    private lateinit var overlay: LinearLayout
    private lateinit var channelBar: TextView
    private lateinit var overlayTitle: TextView
    private lateinit var descriptionText: TextView
    private lateinit var progressFill: View
    private lateinit var progressBg: View
    private lateinit var progressContainer: FrameLayout
    private lateinit var overlayChannelTime: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var hidePending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val channelUuid = intent.getStringExtra("channel_uuid") ?: run { finish(); return }

        prefs = Preferences(this)
        prefs!!.lastChannelUuid = channelUuid
        prefs!!.lastChannelName = intent.getStringExtra("channel_name") ?: ""

        val authHeader = Credentials.basic(prefs!!.username, prefs!!.password)
        dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Authorization" to authHeader))

        // ── Root: FrameLayout (video + overlay) ──
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        playerView = PlayerView(this).apply {
            keepScreenOn = true
            useController = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(playerView)

        // ── Now Playing overlay (bottom) ──
        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#DD000000"))
                cornerRadii = floatArrayOf(16f, 16f, 16f, 16f, 0f, 0f, 0f, 0f)
            }
            background = bg
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM }
        }

        channelBar = TextView(this).apply {
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(20, 12, 20, 12)
            setBackgroundColor(Color.parseColor("#66000000"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        overlay.addView(channelBar)

        overlayTitle = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(20, 12, 20, 0)
        }
        overlay.addView(overlayTitle)

        descriptionText = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.parseColor("#BBBBBB"))
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(20, 6, 20, 0)
        }
        overlay.addView(descriptionText)

        // Progress bar
        progressContainer = FrameLayout(this@PlaybackActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (8 * resources.displayMetrics.density).toInt()
            ).apply { topMargin = 8; bottomMargin = 10 }

            progressBg = View(this@PlaybackActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#33FFFFFF"))
            }
            addView(progressBg)

            progressFill = View(this@PlaybackActivity).apply {
                layoutParams = FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.parseColor("#1A73E8"))
            }
            addView(progressFill)
        }
        overlay.addView(progressContainer)

        overlayChannelTime = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#CCCCCC"))
            maxLines = 1
        }
        overlay.addView(overlayChannelTime)
        root.addView(overlay)

        setContentView(root)

        // ── Player setup ──
        player = ExoPlayer.Builder(this).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) finish()
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (!fallbackTried) {
                        fallbackTried = true
                        val hlsUrl = "$currentUrl/hls"
                        val hlsSource = HlsMediaSource.Factory(dataSourceFactory!!)
                            .createMediaSource(MediaItem.fromUri(hlsUrl))
                        setMediaSource(hlsSource, true)
                        prepare()
                        playWhenReady = true
                    }
                }
            })
        }
        playerView?.player = player

        // ── Load channel list + cache EPG + initial play ──
        lifecycleScope.launch {
            val api = RetrofitClient.getApi(prefs!!)
            val chResult = withContext(Dispatchers.IO) {
                try { ChannelRepository(api).getChannels() }
                catch (_: Exception) { Result.failure(Exception("")) }
            }
            channels = chResult.getOrNull() ?: emptyList()
            currentChannelIndex = channels.indexOfFirst { it.uuid == channelUuid }

            // Pre-load current EPG (mode=now) for instant overlay on channel switch
            if (!EpgCache.isValid()) {
                withContext(Dispatchers.IO) {
                    try {
                        val result = EpgRepository(api).getCurrentEvents()
                        result.getOrNull()?.let { EpgCache.put(it) }
                    } catch (_: Exception) { }
                }
            }

            playChannel(channelUuid)
        }
    }

    private fun playChannel(uuid: String) {
        val pfs = prefs ?: return

        val ch = channels.find { it.uuid == uuid }
        pfs.lastChannelUuid = uuid
        pfs.lastChannelName = ch?.name ?: ""
        title = ch?.name ?: "Channel"
        currentIndex = currentChannelIndex
        currentChannel = ch

        // Show overlay IMMEDIATELY from cache (before stream switches)
        showOverlay(uuid)

        // Now switch the stream
        val p = player ?: return
        val dsf = dataSourceFactory ?: return
        val baseUrl = pfs.serverUrl.trimEnd('/')
        currentUrl = "$baseUrl/stream/channel/$uuid"
        fallbackTried = false

        val tsSource = ProgressiveMediaSource.Factory(dsf)
            .createMediaSource(MediaItem.fromUri(currentUrl))
        p.setMediaSource(tsSource, true)
        p.prepare()
        p.playWhenReady = true
    }

    private var currentChannel: Channel? = null
    private var currentIndex: Int = -1

    private fun showOverlay(channelUuid: String) {
        val ev = EpgCache.get(channelUuid)
        val ch = currentChannel
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

        channelBar.text = ch?.name ?: ""

        if (ev != null) {
            overlayTitle.text = ev.title ?: ch?.name ?: ""
            descriptionText.text = ev.description ?: ev.subtitle ?: ""
            descriptionText.visibility = View.VISIBLE
            progressContainer.visibility = View.VISIBLE
            overlayChannelTime.visibility = View.VISIBLE

            val now = System.currentTimeMillis() / 1000
            val elapsed = (now - ev.start).toFloat()
            val total = (ev.stop - ev.start).toFloat()
            val pct = (elapsed / total * 100).coerceIn(0f, 100f)
            val remaining = total - elapsed

            progressFill.post {
                val parentW = (progressFill.parent as View).width
                val lp = progressFill.layoutParams
                if (lp is ViewGroup.MarginLayoutParams) lp.width = (parentW * pct / 100).toInt().coerceAtLeast(0)
                progressFill.layoutParams = lp
            }

            val timeStr = "${sdf.format(Date(ev.start * 1000))} – ${sdf.format(Date(ev.stop * 1000))}"
            val remainStr = if (remaining > 60) "${(remaining / 60).toInt()} min remaining" else "Ending soon"
            overlayChannelTime.text = "$timeStr  ·  $remainStr"
        } else {
            overlayTitle.text = "No TV program information"
            descriptionText.visibility = View.GONE
            progressContainer.visibility = View.GONE
            overlayChannelTime.visibility = View.GONE
        }

        // Fade in overlay
        overlay.visibility = View.VISIBLE
        overlay.startAnimation(TranslateAnimation(
            0f, 0f, 200f, 0f
        ).apply { duration = 250 })

        // Reset auto-hide timer
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 8000L)
        hidePending = true
    }

    private fun hideOverlay() {
        overlay.startAnimation(AlphaAnimation(1f, 0f).apply {
            duration = 300
            setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(a: android.view.animation.Animation?) {}
                override fun onAnimationEnd(a: android.view.animation.Animation?) {
                    overlay.visibility = View.GONE
                }
                override fun onAnimationRepeat(a: android.view.animation.Animation?) {}
            })
        })
        hidePending = false
    }

    private val hideRunnable = Runnable { hideOverlay() }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (hidePending) {
                showTrackMenu()
                return true
            }
            showOverlay(currentChannel?.uuid ?: return true)
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed(hideRunnable, 8000L)
            hidePending = true
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Any key activity extends overlay visibility
        if (hidePending && keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_DPAD_LEFT) {
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed(hideRunnable, 8000L)
        }

        if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (channels.isNotEmpty()) {
                val next = (currentChannelIndex + 1).coerceAtMost(channels.size - 1)
                if (next != currentChannelIndex) {
                    currentChannelIndex = next; currentIndex = next
                    currentChannel = channels[next]
                    playChannel(channels[next].uuid)
                }
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (channels.isNotEmpty()) {
                val prev = (currentChannelIndex - 1).coerceAtLeast(0)
                if (prev != currentChannelIndex) {
                    currentChannelIndex = prev; currentIndex = prev
                    currentChannel = channels[prev]
                    playChannel(channels[prev].uuid)
                }
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (hidePending) { hideOverlay(); return true }
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showTrackMenu() {
        val p = player ?: return
        val tracks = p.currentTracks

        var audioTracks = mutableListOf<Pair<String, String>>()
        var currentAudio = ""
        var subtitleEnabled = false
        var subtitleCount = 0

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        val lang = fmt.language ?: "unknown"
                        val label = fmt.label ?: lang
                        audioTracks.add(lang to label)
                        if (group.isSelected()) currentAudio = lang
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    subtitleEnabled = group.length > 0 && group.isSelected()
                    subtitleCount = group.length
                }
            }
        }

        if (audioTracks.isEmpty() && subtitleCount == 0) {
            AlertDialog.Builder(this)
                .setTitle("Playback Settings")
                .setMessage("No alternate audio or subtitle tracks available")
                .setPositiveButton("Close", null)
                .show()
            return
        }

        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        for ((lang, label) in audioTracks) {
            val marker = if (lang == currentAudio) "●" else "○"
            items.add("$marker Audio: $label")
            actions.add {
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon().setPreferredAudioLanguage(lang).build()
            }
        }

        if (subtitleCount > 0) {
            val marker = if (subtitleEnabled) "●" else "○"
            items.add("$marker Subtitles: ${if (subtitleEnabled) "On" else "Off"}")
            actions.add {
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitleEnabled).build()
            }
        }

        items.add(""); actions.add {}
        items.add("Close"); actions.add {}

        AlertDialog.Builder(this)
            .setTitle("Playback Settings")
            .setItems(items.toTypedArray()) { dialog, which ->
                actions.getOrNull(which)?.invoke()
                dialog.dismiss()
            }
            .show()
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        playerView = null
    }
}
