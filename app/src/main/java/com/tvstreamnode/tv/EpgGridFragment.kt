package com.tvstreamnode.tv

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.tvstreamnode.tv.data.model.Channel
import com.tvstreamnode.tv.data.model.EpgEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EpgGridFragment : Fragment() {

    private lateinit var viewModel: EpgGridViewModel
    private lateinit var gridSection: FrameLayout
    private lateinit var verticalScroll: ScrollView
    private lateinit var gridScroll: HorizontalScrollView
    private lateinit var scrollHeader: HorizontalScrollView
    private lateinit var rowsContainer: LinearLayout
    private lateinit var channelColumn: LinearLayout
    private lateinit var timeLabelsContainer: LinearLayout
    private lateinit var root: FrameLayout
    private lateinit var todayLabel: TextView
    private lateinit var clockText: TextView
    private lateinit var nowIndicator: View

    private lateinit var detailPanel: LinearLayout
    private lateinit var detailTitle: TextView
    private lateinit var detailEpisode: TextView
    private lateinit var detailChannelTime: TextView
    private lateinit var detailDesc: TextView
    private lateinit var detailSummary: TextView

    private var eventsByChannel: Map<String, List<EpgEvent>> = emptyMap()
    private var allChannels: List<Channel> = emptyList()
    private var scrollHeight: Int = 0
    private var syncing = false

    private val handler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            clockText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 60000L)
        }
    }

    companion object {
        private const val HALF_HOUR_WIDTH_DP = 300
        private const val CHANNEL_COL_WIDTH_DP = 180
        private const val ROW_HEIGHT_DP = 60
        private const val HEADER_HEIGHT_DP = 36
        private const val SEPARATOR_DP = 1
        private const val VISIBLE_ROWS = 6
        private const val RANGE_BEFORE_SEC = 1800L
        private const val RANGE_AFTER_SEC = 7200L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[EpgGridViewModel::class.java]
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val density = resources.displayMetrics.density
        val colW = (CHANNEL_COL_WIDTH_DP * density).toInt()
        val headerH = (HEADER_HEIGHT_DP * density).toInt()
        val rowH = (ROW_HEIGHT_DP * density).toInt()
        val sep = (SEPARATOR_DP * density).toInt()
        val gridH = headerH + sep + VISIBLE_ROWS * (rowH + sep)

        root = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val mainLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // ── Grid section ──
        gridSection = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, gridH)
        }

        // ── Top row (FIXED: never scrolls vertically, scrolls horizontally synced with grid) ──
        val topRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, headerH)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }

        todayLabel = TextView(requireContext()).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#243342"))
            layoutParams = LinearLayout.LayoutParams(colW, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        topRow.addView(todayLabel)

        scrollHeader = HorizontalScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            isFillViewport = false

            timeLabelsContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            addView(timeLabelsContainer)
        }
        topRow.addView(scrollHeader)
        gridSection.addView(topRow)

        // Separator under top row
        gridSection.addView(View(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sep).apply {
                topMargin = headerH
            }
        })

        // ── Vertical scroll area for channels ──
        val channelScrollHeight = gridH - headerH - sep
        scrollHeight = channelScrollHeight

        verticalScroll = ScrollView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, channelScrollHeight).apply {
                topMargin = headerH + sep
            }
            isFillViewport = true
            isFocusable = true

            val hLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(colW, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            // Channel column — inside vertical ScrollView, OUTSIDE HorizontalScrollView
            // Scrolls vertically with channels, FIXED horizontally
            channelColumn = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(colW, ViewGroup.LayoutParams.WRAP_CONTENT)
                setBackgroundColor(Color.parseColor("#2C3E50"))
            }
            hLayout.addView(channelColumn)

            // Grid scroll — syncs with scrollHeader above
            gridScroll = HorizontalScrollView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                isFillViewport = true
                isFocusable = true

                rowsContainer = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                addView(rowsContainer)
            }
            hLayout.addView(gridScroll)

            addView(hLayout)
        }
        gridSection.addView(verticalScroll)

        // Sync horizontal scroll between time header and grid
        scrollHeader.setOnScrollChangeListener { _, x, _, _, _ ->
            if (!syncing) { syncing = true; gridScroll.scrollTo(x, 0); syncing = false }
        }
        gridScroll.setOnScrollChangeListener { _, x, _, _, _ ->
            if (!syncing) { syncing = true; scrollHeader.scrollTo(x, 0); syncing = false }
        }

        // Yellow now indicator overlay
        nowIndicator = View(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#FFEB3B"))
            layoutParams = FrameLayout.LayoutParams(2, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        gridSection.addView(nowIndicator)
        mainLayout.addView(gridSection)

        // Separator between grid and detail
        mainLayout.addView(View(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#444444"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2)
        })

        // ── Detail panel ──
        detailPanel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(20, 14, 20, 14)
        }

        detailTitle = TextView(requireContext()).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 6 }
        }

        detailEpisode = TextView(requireContext()).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#CCCCCC"))
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 6 }
        }

        detailChannelTime = TextView(requireContext()).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#BBBBBB"))
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 }
        }

        detailDesc = TextView(requireContext()).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 4 }
        }

        detailSummary = TextView(requireContext()).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#999999"))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        detailPanel.addView(detailTitle)
        detailPanel.addView(detailEpisode)
        detailPanel.addView(detailChannelTime)
        detailPanel.addView(detailDesc)
        detailPanel.addView(detailSummary)
        mainLayout.addView(detailPanel)

        root.addView(mainLayout)

        // ── Clock overlay ──
        clockText = TextView(requireContext()).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(16, 10, 16, 10)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
            }
        }
        root.addView(clockText)

        root.isFocusable = true
        root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                parentFragmentManager.popBackStack()
                true
            } else {
                false
            }
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            if (!state.isLoading) {
                eventsByChannel = state.events.groupBy { it.channelUuid }
                allChannels = state.channels
                buildGrid(state.channels)
            }
        }

        viewModel.loadData()
        return root
    }

    override fun onResume() {
        super.onResume()
        clockText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        handler.postDelayed(clockRunnable, 60000L)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockRunnable)
    }

    // ──────────────── Grid building ────────────────

    private fun buildGrid(channels: List<Channel>) {
        try {
            buildGridSafe(channels)
        } catch (e: Exception) {
            if (::rowsContainer.isInitialized) {
                rowsContainer.removeAllViews()
                rowsContainer.addView(TextView(requireContext()).apply {
                    text = "Error: ${e.message}"
                    setTextColor(Color.parseColor("#FF5252"))
                    textSize = 16f
                    setPadding(32, 32, 32, 32)
                })
            }
        }
    }

    private fun buildGridSafe(channels: List<Channel>) {
        rowsContainer.removeAllViews()
        channelColumn.removeAllViews()
        timeLabelsContainer.removeAllViews()

        if (channels.isEmpty()) {
            rowsContainer.addView(TextView(requireContext()).apply {
                text = "No channels"
                setTextColor(Color.GRAY)
                textSize = 20f
                setPadding(32, 32, 32, 32)
            })
            return
        }

        val density = resources.displayMetrics.density
        val halfHourW = (HALF_HOUR_WIDTH_DP * density).toInt()
        val colW = (CHANNEL_COL_WIDTH_DP * density).toInt()
        val rowH = (ROW_HEIGHT_DP * density).toInt()

        val now = System.currentTimeMillis() / 1000
        val rangeStart = now - RANGE_BEFORE_SEC
        val rangeEnd = now + RANGE_AFTER_SEC
        val totalSeconds = (rangeEnd - rangeStart).toFloat()
        val halfHourSlots = ((totalSeconds + 1799) / 1800).toInt()
        val totalWidth = halfHourSlots * halfHourW

        // "Today" label
        todayLabel.text = formatDate(now)

        // Time labels in scroll header (synced with grid)
        for (i in 0 until halfHourSlots) {
            val tickTime = rangeStart + i * 1800L
            val label = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(tickTime * 1000))
            timeLabelsContainer.addView(TextView(requireContext()).apply {
                text = label
                textSize = 11f
                setTextColor(Color.parseColor("#999999"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(halfHourW, ViewGroup.LayoutParams.MATCH_PARENT)
            })
        }

        val nowX = colW + ((now - rangeStart) * totalWidth / totalSeconds).toInt()

        val focusBg = GradientDrawable().apply {
            setColor(Color.parseColor("#33FFFFFF"))
            cornerRadius = 0f
        }
        val blockFocusBg = GradientDrawable().apply {
            setColor(Color.parseColor("#388E3C"))
            cornerRadius = 4f
        }
        val blockNormalBg = GradientDrawable().apply {
            setColor(Color.parseColor("#1B5E20"))
            cornerRadius = 4f
        }

        // ── Channel rows ──
        for ((index, channel) in channels.withIndex()) {
            val channelEvents = eventsByChannel[channel.uuid] ?: emptyList()
            val row = buildChannelRow(channel, colW, totalWidth, rowH)

            row.onFocusChangeListener = null
            row.setOnFocusChangeListener { _, hasFocus ->
                val colLabel = channelColumn.getChildAt(index)
                if (hasFocus) {
                    row.background = focusBg
                    colLabel?.setBackgroundColor(Color.parseColor("#3D5A80"))
                    if (row.tag !is EpgEvent) showDetail(channel, null)
                } else {
                    row.background = null
                    colLabel?.setBackgroundColor(Color.TRANSPARENT)
                }
            }

            for (event in channelEvents) {
                val startOff = ((event.start - rangeStart) * totalWidth / totalSeconds).toInt()
                val width = maxOf(((event.stop - event.start) * totalWidth / totalSeconds).toInt(), 2)
                if (startOff + width < 0 || startOff > totalWidth) continue

                val block = FrameLayout(requireContext()).apply {
                    background = blockNormalBg
                    layoutParams = FrameLayout.LayoutParams(
                        minOf(width, totalWidth - maxOf(startOff, 0)),
                        (rowH * 0.82f).toInt()
                    ).apply {
                        leftMargin = startOff + colW + 2
                        topMargin = (rowH * 0.09f).toInt()
                    }
                    isFocusable = true
                    isClickable = true
                    tag = event

                    addView(TextView(requireContext()).apply {
                        text = event.title ?: "—"
                        textSize = 10f
                        setTextColor(Color.WHITE)
                        maxLines = 3
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(6, 0, 6, 0)
                    })
                }

                block.onFocusChangeListener = null
                block.setOnFocusChangeListener { v, hasFocus ->
                    val view = v as FrameLayout
                    if (hasFocus) {
                        view.background = blockFocusBg
                        (view.parent as? View)?.background = focusBg
                        channelColumn.getChildAt(index)?.setBackgroundColor(Color.parseColor("#3D5A80"))
                        showDetail(channel, event)
                        gridScroll.post {
                            val rect = android.graphics.Rect()
                            view.getGlobalVisibleRect(rect)
                            if (rect.right > gridScroll.width || rect.left < colW) {
                                gridScroll.smoothScrollTo(maxOf(startOff + colW - gridScroll.width / 3, 0), 0)
                            }
                        }
                    } else {
                        view.background = blockNormalBg
                    }
                }

                block.setOnClickListener { startPlayback(channel.uuid, channel.name, event.title) }
                row.addView(block)
            }

            row.setOnClickListener { startPlayback(channel.uuid, channel.name, null) }

            rowsContainer.addView(row)
            rowsContainer.addView(View(requireContext()).apply {
                setBackgroundColor(Color.parseColor("#2A2A2A"))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            })
        }

        // Auto-focus first row
        rowsContainer.post {
            if (rowsContainer.childCount > 0) rowsContainer.getChildAt(0).requestFocus()
        }

        // Position now indicator
        val lp = nowIndicator.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = nowX
        nowIndicator.layoutParams = lp
        nowIndicator.bringToFront()

        // Scroll to current time (syncs both via listener)
        gridScroll.post {
            val scrollTo = maxOf(nowX - gridScroll.width / 4, colW)
            gridScroll.scrollTo(scrollTo, 0)
        }
    }

    private fun buildChannelRow(channel: Channel, colW: Int, totalWidth: Int, rowH: Int): FrameLayout {
        val row = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(colW + totalWidth, rowH)
            isFocusable = true
            isClickable = true
        }

        row.addView(View(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(colW, rowH)
        })

        val displayName = buildString {
            channel.number?.let { append("$it  ") }
            channel.name?.let { append(it) }
        }
        channelColumn.addView(TextView(requireContext()).apply {
            text = displayName
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 0, 8, 0)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(colW, rowH)
        })

        return row
    }

    // ──────────────── Detail panel ────────────────

    private fun showDetail(channel: Channel?, event: EpgEvent?) {
        if (event != null && channel != null) {
            detailTitle.text = event.title ?: "—"
            val episode = buildEpisodeString(event)
            detailEpisode.text = episode
            detailEpisode.visibility = if (episode.isNotEmpty()) View.VISIBLE else View.GONE
            val src = buildString { channel.number?.let { append("Ch.$it ") }; append(channel.name ?: "") }
            detailChannelTime.text = "$src · ${formatDate(event.start)} ${formatTimeRange(event.start, event.stop)}"
            detailDesc.text = event.description ?: ""
            detailSummary.text = event.summary ?: ""
            detailPanel.visibility = View.VISIBLE
        } else if (channel != null) {
            detailTitle.text = buildString { channel.number?.let { append("Ch.$it ") }; channel.name?.let { append(it) } }
            detailEpisode.visibility = View.GONE
            detailChannelTime.text = "Navigate to a program"
            detailDesc.text = ""
            detailSummary.text = ""
            detailPanel.visibility = View.VISIBLE
        }
    }

    // ──────────────── Helpers ────────────────

    private fun buildEpisodeString(event: EpgEvent): String {
        if (!event.episodeOnscreen.isNullOrBlank()) return event.episodeOnscreen
        val parts = mutableListOf<String>()
        event.seasonNumber?.let { parts.add("Season $it") }
        event.episodeNumber?.let { parts.add("Episode $it") }
        return parts.joinToString(" | ")
    }

    private fun formatDate(timestamp: Long): String {
        val eventCal = Calendar.getInstance().apply { timeInMillis = timestamp * 1000 }
        val todayCal = Calendar.getInstance()
        eventCal.set(Calendar.HOUR_OF_DAY, 0); eventCal.set(Calendar.MINUTE, 0); eventCal.set(Calendar.SECOND, 0); eventCal.set(Calendar.MILLISECOND, 0)
        todayCal.set(Calendar.HOUR_OF_DAY, 0); todayCal.set(Calendar.MINUTE, 0); todayCal.set(Calendar.SECOND, 0); todayCal.set(Calendar.MILLISECOND, 0)
        val diffDays = ((eventCal.timeInMillis - todayCal.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
        return when (diffDays) {
            0 -> "Today"; -1 -> "Yesterday"; 1 -> "Tomorrow"
            else -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp * 1000))
        }
    }

    private fun formatTimeRange(start: Long, stop: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return "${sdf.format(Date(start * 1000))}–${sdf.format(Date(stop * 1000))}"
    }

    private fun startPlayback(uuid: String?, name: String?, eventTitle: String?) {
        if (uuid == null) return
        startActivity(Intent(requireContext(), PlaybackActivity::class.java).apply {
            putExtra("channel_uuid", uuid)
            putExtra("channel_name", name)
            putExtra("event_title", eventTitle)
        })
    }
}
