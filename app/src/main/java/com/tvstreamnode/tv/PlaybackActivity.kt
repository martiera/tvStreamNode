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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.ui.PlayerView
import com.tvstreamnode.tv.data.api.RetrofitClient
import com.tvstreamnode.tv.data.model.Channel
import com.tvstreamnode.tv.data.model.EpgEvent
import com.tvstreamnode.tv.data.repository.ChannelRepository
import com.tvstreamnode.tv.data.repository.EpgRepository
import com.tvstreamnode.tv.util.EpgCache
import com.tvstreamnode.tv.util.DataCache
import com.tvstreamnode.tv.util.ListManager
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
    private var dataSourceFactory: DefaultHttpDataSource.Factory? = null
    private var currentUrl: String = ""
    private var prefs: Preferences? = null
    private var autoSubtitleListener: Player.Listener? = null
    private var errorRetries = 0
    private var currentUuid: String = ""
    private var streamType: String = "auto"
    private var subtitleLanguage: String = "channel"
    private lateinit var loadingOverlay: LinearLayout
    private var audioFallbackTried = false
    private var usingAudioFallback = false

    companion object {
        // Server-side profile: video+subs pass through, MP2/AC-3 audio transcoded to
        // Vorbis in Matroska. tvheadend's webtv-aac emits AAC-LD which Fire TV's FDK
        // decoder rejects (AAC_DEC_UNSUPPORTED_FORMAT / UNSUPPORTED_ER); Vorbis is
        // decoded by the device's software Vorbis decoder.
        private const val AUDIO_FALLBACK_PROFILE = "audio-vorbis"
    }

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
        streamType = prefs!!.streamType
        subtitleLanguage = prefs!!.subtitleLanguage

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

        // ── Loading / buffering overlay ──
        loadingOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#99000000"))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        loadingOverlay.addView(ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (48 * resources.displayMetrics.density).toInt(),
                (48 * resources.displayMetrics.density).toInt()
            )
        })
        loadingOverlay.addView(TextView(this).apply {
            text = "Loading…"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * resources.displayMetrics.density).toInt() }
        })
        root.addView(loadingOverlay)

        setContentView(root)

        // ── Player setup ──
        // Live-TV zapping tuning: start playback after ~1s buffered (default 2.5s)
        // for quick channel switches; 15–30s ceiling keeps playback stable.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            loadingOverlay.visibility = View.GONE
                            maybeFallbackForAudio()
                        }
                        Player.STATE_ENDED -> handleStreamFailure()
                        else -> if (playWhenReady) loadingOverlay.visibility = View.VISIBLE
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    handleStreamFailure()
                }
                override fun onTracksChanged(tracks: Tracks) {
                    maybeFallbackForAudio()
                }
            })
        }
        playerView?.player = player
        playerView?.subtitleView?.apply {
            visibility = View.VISIBLE
            setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION)
            setApplyEmbeddedStyles(true)
        }

        // ── Instant tune-in: start playback immediately ──
        DataCache.init(this)
        // Warm caches from disk (channel list for zapping, EPG for the overlay)
        DataCache.loadChannels()?.let { cached ->
            channels = cached.sortedBy { it.name?.lowercase() ?: "" }
            currentChannelIndex = channels.indexOfFirst { it.uuid == channelUuid }
        }
        DataCache.loadEpg()?.let { EpgCache.put(it) }
        playChannel(channelUuid)

        // ── Refresh channel list + EPG from network in the background ──
        lifecycleScope.launch {
            val api = RetrofitClient.getApi(prefs!!)
            val chResult = withContext(Dispatchers.IO) {
                try { ChannelRepository(api).getChannels() }
                catch (_: Exception) { Result.failure(Exception("")) }
            }
            chResult.getOrNull()?.let { fresh ->
                DataCache.saveChannels(fresh)
                channels = fresh.sortedBy { it.name?.lowercase() ?: "" }
                currentChannelIndex = channels.indexOfFirst { it.uuid == currentUuid }
                currentChannel = channels.find { it.uuid == currentUuid }
            }

            // Refresh current EPG (mode=now) for the overlay
            if (!EpgCache.isValid()) {
                withContext(Dispatchers.IO) {
                    try {
                        val result = EpgRepository(api).getCurrentEvents()
                        result.getOrNull()?.let { events ->
                            EpgCache.put(events)
                            DataCache.saveEpg(events)
                        }
                    } catch (_: Exception) { }
                }
            }
        }
    }

    private fun playChannel(uuid: String) {
        val pfs = prefs ?: return

        val ch = channels.find { it.uuid == uuid }
        // Fall back to the intent-extra name until the channel list loads —
        // keeps the overlay correct on instant tune-in.
        val name = ch?.name ?: pfs.lastChannelName
        pfs.lastChannelUuid = uuid
        pfs.lastChannelName = name
        title = name.ifBlank { "Channel" }
        currentChannel = ch
        currentUuid = uuid

        // Show overlay IMMEDIATELY from cache (before stream switches)
        showOverlay(uuid)

        // Now switch the stream
        errorRetries = 0
        audioFallbackTried = false
        usingAudioFallback = false
        prepareSource(streamModes().first())

        // Auto-enable subtitles if available and currently disabled
        val p = player ?: return
        autoSubtitleListener?.let { p.removeListener(it) }
        val autoSub = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
                if (subtitleLanguage == "off") {
                    if (!p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
                        p.trackSelectionParameters = p.trackSelectionParameters
                            .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    }
                    return
                }
                if (textGroups.isEmpty()) return
                // Text is selected by default in media3's DefaultTrackSelector only when a
                // preferred text language is set; otherwise the DVB/CEA subtitle track stays
                // unselected and nothing renders. Force a language preference here so the
                // subtitle track actually gets picked.
                val lang = when (subtitleLanguage) {
                    "system" -> Locale.getDefault().language
                    else -> textGroups.first().getTrackFormat(0).language
                        ?: Locale.getDefault().language
                }
                val params = p.trackSelectionParameters
                val textDisabled = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                val preferredMatches = params.preferredTextLanguages.any { it.equals(lang, true) }
                if (!textDisabled && preferredMatches) return
                p.trackSelectionParameters = params
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage(lang)
                    .build()
            }
        }
        autoSubtitleListener = autoSub
        p.addListener(autoSub)
    }

    private fun streamModes(): List<String> = when (streamType) {
        "hls" -> listOf("hls")
        else -> listOf("ts", "ts_pass")
    }

    private fun prepareSource(mode: String, forcedProfile: String? = null) {
        val p = player ?: return
        val dsf = dataSourceFactory ?: return
        val uuid = currentUuid.ifBlank { return }
        val baseUrl = prefs?.serverUrl?.trimEnd('/') ?: return
        val profile = forcedProfile ?: prefs?.streamProfile?.takeIf { it.isNotBlank() }

        var url = "$baseUrl/stream/channel/$uuid"
        if (mode == "hls") url = "$url/hls"
        // An explicit stream profile (e.g. one that transcodes audio to AAC) overrides
        // the built-in ?profile=pass fallback — useful for channels whose audio codec
        // the device can't decode (MP2/AC-3 on older TVs).
        if (profile != null) url = "$url?profile=${java.net.URLEncoder.encode(profile, "UTF-8")}"
        else if (mode == "ts_pass") url = "$url?profile=pass"
        currentUrl = url

        val source = if (mode == "hls") {
            HlsMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(url))
        } else {
            ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(url))
        }
        p.setMediaSource(source, true)
        p.prepare()
        p.playWhenReady = true
    }

    /**
     * Some channels broadcast audio in a codec the device cannot decode (e.g. MP2 on
     * Fire TV / older Bravia). ExoPlayer then plays video with the audio track left
     * unselected. Detect that and transparently switch to the server-side
     * "audio-aac" profile, which passes video/subs through untouched and transcodes
     * only the audio to AAC.
     */
    private fun maybeFallbackForAudio() {
        val p = player ?: return
        if (audioFallbackTried || usingAudioFallback) return
        if (errorRetries != 0) return
        if (!prefs?.streamProfile.isNullOrBlank()) return
        val groups = p.currentTracks.groups
        val hasAudio = groups.any { it.type == C.TRACK_TYPE_AUDIO }
        val audioSelected = groups.any { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
        if (hasAudio && !audioSelected) {
            audioFallbackTried = true
            usingAudioFallback = true
            showToast("Using compatible audio stream…")
            prepareSource("ts", forcedProfile = AUDIO_FALLBACK_PROFILE)
        }
    }

    private fun handleStreamFailure() {
        // If the compatible-audio profile itself failed, revert to the raw stream
        if (usingAudioFallback) {
            usingAudioFallback = false
            prepareSource("ts")
            return
        }
        val modes = streamModes()
        if (errorRetries + 1 < modes.size) {
            errorRetries++
            prepareSource(modes[errorRetries])
            showToast("Stream interrupted — retrying…")
        } else {
            currentChannel?.uuid?.let { showErrorDialog(it) }
        }
    }

    private fun showErrorDialog(uuid: String) {
        AlertDialog.Builder(this)
            .setTitle("Stream error")
            .setMessage("Unable to play this channel.")
            .setPositiveButton("Retry") { _, _ -> playChannel(uuid) }
            .setNegativeButton("Back") { _, _ -> finish() }
            .show()
    }

    private var currentChannel: Channel? = null

    private fun showOverlay(channelUuid: String) {
        val ev = EpgCache.get(channelUuid)
        val ch = currentChannel
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

        channelBar.text = currentChannel?.name ?: prefs?.lastChannelName ?: ""

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

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showTrackMenu()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (channels.isNotEmpty()) {
                val next = if (currentChannelIndex >= channels.size - 1) 0 else currentChannelIndex + 1
                if (next != currentChannelIndex) {
                    currentChannelIndex = next
                    currentChannel = channels[next]
                    playChannel(channels[next].uuid)
                }
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (channels.isNotEmpty()) {
                val prev = if (currentChannelIndex <= 0) channels.size - 1 else currentChannelIndex - 1
                if (prev != currentChannelIndex) {
                    currentChannelIndex = prev
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

    private fun showChannelListMenu() {
        ListManager.init(this)
        val uuid = currentChannel?.uuid ?: return
        val chName = currentChannel?.name ?: ""
        val lists = ListManager.getAll()
        if (lists.isEmpty()) {
            Toast.makeText(this, "No lists — create one in Lists menu", Toast.LENGTH_SHORT).show()
            return
        }

        val density = resources.displayMetrics.density
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (12 * density).toInt(), (24 * density).toInt(), (8 * density).toInt())
        }

        for (list in lists) {
            val isInList = uuid in list.channelIds
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt())
                isFocusable = true
                isClickable = true
                setOnClickListener {
                    ListManager.toggleChannel(list.id, uuid, !isInList)
                    val msg = if (!isInList) "Added to ${list.name}" else "Removed from ${list.name}"
                    Toast.makeText(this@PlaybackActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }

            row.addView(TextView(this).apply {
                    text = if (isInList) "☑" else "☐"
                textSize = 20f
                setTextColor(if (isInList) Color.parseColor("#1A73E8") else Color.parseColor("#666666"))
                layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(this).apply {
                text = list.name
                textSize = 16f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            content.addView(row)
        }

        AlertDialog.Builder(this)
            .setTitle("Lists — $chName")
            .setView(content)
            .setPositiveButton("Close", null)
            .show()
    }

    private data class AudioTrackInfo(
        val group: Tracks.Group,
        val trackIndex: Int,
        val lang: String,
        val label: String,
        val selected: Boolean
    )

    private fun showTrackMenu() {
        val p = player ?: return
        val tracks = p.currentTracks

        val audioTracks = mutableListOf<AudioTrackInfo>()
        val subtitleTracks = mutableListOf<AudioTrackInfo>()
        var subtitleEnabled = false

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        val lang = fmt.language ?: "unknown"
                        val codec = fmt.codecs ?: fmt.sampleMimeType?.substringAfter('/') ?: ""
                        val label = fmt.label
                            ?: if (fmt.language != null) lang
                            else if (codec.isNotBlank()) "$codec (track ${i + 1})"
                            else "Track ${i + 1}"
                        audioTracks.add(AudioTrackInfo(group, i, lang, label, group.isTrackSelected(i)))
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    subtitleEnabled = !p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        val lang = fmt.language ?: "unknown"
                        val label = fmt.label
                            ?: if (fmt.language != null) lang
                            else "Subtitle ${i + 1}"
                        subtitleTracks.add(AudioTrackInfo(group, i, lang, label, group.isTrackSelected(i)))
                    }
                }
            }
        }

        if (audioTracks.isEmpty() && subtitleTracks.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Playback Settings")
                .setMessage("No alternate audio or subtitle tracks available")
                .setPositiveButton("Close", null)
                .show()
            return
        }

        val density = resources.displayMetrics.density
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (8 * density).toInt())
        }

        // Audio section header
        content.addView(TextView(this).apply {
            text = "Audio"
            textSize = 18f
            setTextColor(Color.parseColor("#999999"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * density).toInt() }
        })

        // Audio track rows
        for (track in audioTracks) {
            val selected = track.selected
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
                isFocusable = true
                isClickable = true
                val nBg = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
                val fBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#2A2A2A"))
                    setStroke(2, Color.parseColor("#1A73E8"))
                }
                setOnFocusChangeListener { v, hasFocus -> v.background = if (hasFocus) fBg else nBg }
                setOnClickListener {
                    val override = TrackSelectionOverride(track.group.mediaTrackGroup, listOf(track.trackIndex))
                    p.trackSelectionParameters = p.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .addOverride(override)
                        .build()
                    (this.getChildAt(0) as? TextView)?.let { m ->
                        m.text = "●"
                        m.setTextColor(Color.parseColor("#1A73E8"))
                    }
                    (this.getChildAt(1) as? TextView)?.setTextColor(Color.WHITE)
                    // Clear other audio rows only (skip subtitle row)
                    val parent = this.parent as? LinearLayout
                    parent?.let { pl ->
                        for (i in 0 until pl.childCount) {
                            val r = pl.getChildAt(i) as? LinearLayout ?: continue
                            if (r == this) continue
                            val marker = r.getChildAt(0) as? TextView ?: continue
                            if (marker.text in listOf("●", "○")) {
                                marker.text = "○"
                                marker.setTextColor(Color.parseColor("#666666"))
                                (r.getChildAt(1) as? TextView)?.setTextColor(Color.parseColor("#CCCCCC"))
                            }
                        }
                    }
                    showToast("Audio: ${track.label}")
                }
            }

            row.addView(TextView(this).apply {
                text = if (selected) "●" else "○"
                textSize = 20f
                setTextColor(if (selected) Color.parseColor("#1A73E8") else Color.parseColor("#666666"))
                layoutParams = LinearLayout.LayoutParams(
                    (32 * density).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            })
            row.addView(TextView(this).apply {
                text = track.label.replaceFirstChar { it.uppercase() }
                textSize = 16f
                setTextColor(if (selected) Color.WHITE else Color.parseColor("#CCCCCC"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            content.addView(row)
        }

        // Subtitles section
        if (subtitleTracks.isNotEmpty()) {
            content.addView(TextView(this).apply {
                text = "Subtitles"
                textSize = 18f
                setTextColor(Color.parseColor("#999999"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (12 * density).toInt(); bottomMargin = (8 * density).toInt() }
            })

            fun updateSubtitleMarkers(selectedTag: Any) {
                val parent = content
                for (i in 0 until parent.childCount) {
                    val r = parent.getChildAt(i) as? LinearLayout ?: continue
                    val tag = r.tag ?: continue
                    val marker = r.getChildAt(0) as? TextView ?: continue
                    if (marker.text in listOf("☑", "☐")) {
                        val active = tag == selectedTag
                        marker.text = if (active) "☑" else "☐"
                        marker.setTextColor(if (active) Color.parseColor("#1A73E8") else Color.parseColor("#666666"))
                        (r.getChildAt(1) as? TextView)?.setTextColor(if (active) Color.WHITE else Color.parseColor("#CCCCCC"))
                    }
                }
            }

            for (track in subtitleTracks) {
                val active = subtitleEnabled && track.selected
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
                    isFocusable = true
                    isClickable = true
                    tag = "subtitle:${System.identityHashCode(track.group)}:${track.trackIndex}"
                    val nBg = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
                    val fBg = GradientDrawable().apply {
                        setColor(Color.parseColor("#2A2A2A"))
                        setStroke(2, Color.parseColor("#1A73E8"))
                    }
                    setOnFocusChangeListener { v, hasFocus -> v.background = if (hasFocus) fBg else nBg }
                    setOnClickListener {
                        val override = TrackSelectionOverride(track.group.mediaTrackGroup, listOf(track.trackIndex))
                        p.trackSelectionParameters = p.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .addOverride(override)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setPreferredTextLanguage(track.lang)
                            .build()
                        updateSubtitleMarkers(this.tag)
                        showToast("Subtitles: ${track.label.replaceFirstChar { it.uppercase() }}")
                    }
                }

                row.addView(TextView(this).apply {
                    text = if (active) "☑" else "☐"
                    textSize = 20f
                    setTextColor(if (active) Color.parseColor("#1A73E8") else Color.parseColor("#666666"))
                    layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
                })
                row.addView(TextView(this).apply {
                    text = track.label.replaceFirstChar { it.uppercase() }
                    textSize = 16f
                    setTextColor(if (active) Color.WHITE else Color.parseColor("#CCCCCC"))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                content.addView(row)
            }

            val offRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
                isFocusable = true
                isClickable = true
                tag = "subtitle:off"
                val nBg = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
                val fBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#2A2A2A"))
                    setStroke(2, Color.parseColor("#1A73E8"))
                }
                setOnFocusChangeListener { v, hasFocus -> v.background = if (hasFocus) fBg else nBg }
                setOnClickListener {
                    p.trackSelectionParameters = p.trackSelectionParameters
                        .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    updateSubtitleMarkers(this.tag)
                    showToast("Subtitles Off")
                }
            }

            offRow.addView(TextView(this).apply {
                text = if (subtitleEnabled) "☐" else "☑"
                textSize = 20f
                setTextColor(if (subtitleEnabled) Color.parseColor("#666666") else Color.parseColor("#1A73E8"))
                layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            offRow.addView(TextView(this).apply {
                text = "Off"
                textSize = 16f
                setTextColor(if (subtitleEnabled) Color.parseColor("#CCCCCC") else Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            content.addView(offRow)
        }

        AlertDialog.Builder(this)
            .setTitle("Playback Settings")
            .setView(content)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showToast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
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
