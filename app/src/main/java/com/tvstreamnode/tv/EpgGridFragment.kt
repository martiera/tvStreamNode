package com.tvstreamnode.tv

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import java.util.Date
import java.util.Locale

class EpgGridFragment : Fragment() {

    private lateinit var viewModel: EpgGridViewModel
    private lateinit var verticalScroll: ScrollView
    private lateinit var horizontalScroll: HorizontalScrollView
    private lateinit var rowsContainer: LinearLayout
    private lateinit var channelColumn: LinearLayout
    private lateinit var root: FrameLayout

    companion object {
        private const val HALF_HOUR_WIDTH_DP = 300
        private const val CHANNEL_COL_WIDTH_DP = 180
        private const val ROW_HEIGHT_DP = 60
        private const val HEADER_HEIGHT_DP = 36
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

        root = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // Vertical scroll (channels)
        verticalScroll = ScrollView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
            isFocusable = true

            val hLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            // Fixed channel column
            channelColumn = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(colW, ViewGroup.LayoutParams.WRAP_CONTENT)
                setBackgroundColor(Color.parseColor("#2C3E50"))
            }
            channelColumn.addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(colW, headerH)
                setBackgroundColor(Color.parseColor("#243342"))
            })
            hLayout.addView(channelColumn)

            // Horizontal scroll (time grid)
            horizontalScroll = HorizontalScrollView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                isFillViewport = true
                isFocusable = true

                rowsContainer = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                }
                addView(rowsContainer)
            }
            hLayout.addView(horizontalScroll)
            addView(hLayout)
        }
        root.addView(verticalScroll)

        // Current time indicator (overlay in root FrameLayout)
        val nowIndicator = View(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#FF5252"))
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(2, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(nowIndicator)

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
                buildGrid(state.channels, state.events, nowIndicator)
            }
        }

        viewModel.loadData()
        return root
    }

    private fun buildGrid(channels: List<Channel>, events: List<EpgEvent>, nowIndicator: View) {
        try {
            buildGridSafe(channels, events, nowIndicator)
        } catch (e: Exception) {
            rowsContainer.removeAllViews()
            rowsContainer.addView(TextView(requireContext()).apply {
                text = "Error: ${e.message}"
                setTextColor(Color.parseColor("#FF5252"))
                textSize = 16f
                setPadding(32, 32, 32, 32)
            })
        }
    }

    private fun buildGridSafe(channels: List<Channel>, events: List<EpgEvent>, nowIndicator: View) {
        rowsContainer.removeAllViews()
        channelColumn.removeViews(1, channelColumn.childCount - 1)

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
        val headerH = (HEADER_HEIGHT_DP * density).toInt()

        val now = System.currentTimeMillis() / 1000
        val rangeStart = now - RANGE_BEFORE_SEC
        val rangeEnd = now + RANGE_AFTER_SEC
        val totalSeconds = (rangeEnd - rangeStart).toFloat()
        val halfHourSlots = ((totalSeconds + 1799) / 1800).toInt()
        val totalWidth = halfHourSlots * halfHourW

        val eventsByChannel = events.groupBy { it.channelUuid }

        // ── Time header ──
        val headerRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(colW + totalWidth, headerH)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }
        headerRow.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(colW, ViewGroup.LayoutParams.MATCH_PARENT)
        })
        for (i in 0 until halfHourSlots) {
            val tickTime = rangeStart + i * 1800L
            val label = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(tickTime * 1000))
            headerRow.addView(TextView(requireContext()).apply {
                text = label
                textSize = 11f
                setTextColor(Color.parseColor("#999999"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(halfHourW, ViewGroup.LayoutParams.MATCH_PARENT)
            })
        }
        rowsContainer.addView(headerRow)
        rowsContainer.addView(View(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        })

        val nowIndicatorX = colW + ((now - rangeStart) * totalWidth / totalSeconds).toInt()

        val focusBg = GradientDrawable().apply {
            setColor(Color.parseColor("#33FFFFFF"))
            cornerRadius = 0f
        }

        // ── Channel rows ──
        for ((index, channel) in channels.withIndex()) {
            val channelEvents = eventsByChannel[channel.uuid] ?: emptyList()
            val row = buildChannelRow(channel, channelEvents, colW, totalWidth, rowH, rangeStart, totalSeconds)

            row.onFocusChangeListener = null
            row.setOnFocusChangeListener { _, hasFocus ->
                val colLabel = channelColumn.getChildAt(index + 1)
                if (hasFocus) {
                    row.background = focusBg
                    colLabel?.setBackgroundColor(Color.parseColor("#3D5A80"))
                } else {
                    row.background = null
                    colLabel?.setBackgroundColor(Color.TRANSPARENT)
                }
            }

            rowsContainer.addView(row)
            rowsContainer.addView(View(requireContext()).apply {
                setBackgroundColor(Color.parseColor("#2A2A2A"))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            })
        }

        // Auto-focus first row
        rowsContainer.post {
            if (rowsContainer.childCount > 1) {
                rowsContainer.getChildAt(1).requestFocus()
            }
        }

        // Position now indicator
        val lp = nowIndicator.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = nowIndicatorX
        nowIndicator.layoutParams = lp
        nowIndicator.bringToFront()

        // Scroll to current time
        horizontalScroll.post {
            val scrollTo = maxOf(nowIndicatorX - horizontalScroll.width / 4, colW)
            horizontalScroll.scrollTo(scrollTo, 0)
        }
    }

    private fun buildChannelRow(
        channel: Channel,
        events: List<EpgEvent>,
        colW: Int,
        totalWidth: Int,
        rowH: Int,
        rangeStart: Long,
        totalSeconds: Float
    ): FrameLayout {
        val row = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(colW + totalWidth, rowH)
            isFocusable = true
            isClickable = true
        }

        // Hidden spacer under left column
        row.addView(View(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(colW, rowH)
        })

        // Fixed column label
        val displayName = buildString {
            channel.number?.let { append("$it ") }
            channel.name?.let { append(it) }
        }
        val fixedLabel = TextView(requireContext()).apply {
            text = displayName
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 0, 8, 0)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(colW, rowH)
        }
        channelColumn.addView(fixedLabel)

        // ── Program blocks ──
        for (event in events) {
            val startOff = ((event.start - rangeStart) * totalWidth / totalSeconds).toInt()
            val width = maxOf(((event.stop - event.start) * totalWidth / totalSeconds).toInt(), 2)

            if (startOff + width < 0 || startOff > totalWidth) continue

            val block = FrameLayout(requireContext()).apply {
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#1B5E20"))
                    cornerRadius = 4f
                }
                background = bg
                layoutParams = FrameLayout.LayoutParams(
                    minOf(width, totalWidth - maxOf(startOff, 0)),
                    (rowH * 0.82f).toInt()
                ).apply {
                    leftMargin = startOff + colW + 2
                    topMargin = (rowH * 0.09f).toInt()
                }

                addView(TextView(requireContext()).apply {
                    text = event.title ?: "—"
                    textSize = 10f
                    setTextColor(Color.WHITE)
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(6, 0, 6, 0)
                })
            }
            row.addView(block)

            block.setOnClickListener {
                startPlayback(channel.uuid, channel.name, event.title)
            }
        }

        row.setOnClickListener {
            startPlayback(channel.uuid, channel.name, null)
        }

        return row
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
