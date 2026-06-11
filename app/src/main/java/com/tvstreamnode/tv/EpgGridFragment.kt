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
    private lateinit var scrollView: HorizontalScrollView
    private lateinit var rowsContainer: LinearLayout
    private lateinit var channelColumn: LinearLayout

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

        val root = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // Scrollable grid (right side)
        scrollView = HorizontalScrollView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
            isFocusable = true

            rowsContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
            }
            addView(rowsContainer)
        }
        root.addView(scrollView)

        // Fixed channel column (left overlay, does NOT scroll horizontally)
        channelColumn = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(colW, ViewGroup.LayoutParams.MATCH_PARENT)
            elevation = 6f
            setBackgroundColor(Color.parseColor("#2C3E50"))
        }
        channelColumn.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(colW, headerH)
            setBackgroundColor(Color.parseColor("#243342"))
        })
        root.addView(channelColumn)

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
                buildGrid(state.channels, state.events)
            }
        }

        viewModel.loadData()
        return root
    }

    private fun buildGrid(channels: List<Channel>, events: List<EpgEvent>) {
        try {
            buildGridSafe(channels, events)
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

    private fun buildGridSafe(channels: List<Channel>, events: List<EpgEvent>) {
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

        // ── Channel rows ──
        for (channel in channels) {
            val channelEvents = eventsByChannel[channel.uuid] ?: emptyList()
            val row = buildChannelRow(channel, channelEvents, colW, totalWidth, rowH, rangeStart, totalSeconds)
            rowsContainer.addView(row)
            rowsContainer.addView(View(requireContext()).apply {
                setBackgroundColor(Color.parseColor("#2A2A2A"))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            })
        }

        // Scroll to current time
        val nowIndicatorX = colW + ((now - rangeStart) * totalWidth / totalSeconds).toInt()
        scrollView.post {
            val scrollTo = maxOf(nowIndicatorX - scrollView.width / 4, colW)
            scrollView.scrollTo(scrollTo, 0)
            scrollView.requestFocus()
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
