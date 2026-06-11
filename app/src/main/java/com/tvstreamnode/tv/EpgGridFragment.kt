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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvstreamnode.tv.data.model.Channel
import com.tvstreamnode.tv.data.model.EpgEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EpgGridFragment : Fragment() {

    private lateinit var viewModel: EpgGridViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChannelAdapter

    private lateinit var detailTitle: TextView
    private lateinit var detailEpisode: TextView
    private lateinit var detailChannelTime: TextView
    private lateinit var detailDesc: TextView
    private lateinit var detailSummary: TextView

    private var channels: List<Channel> = emptyList()
    private var eventsByChannel: Map<String, List<EpgEvent>> = emptyMap()

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val CHANNEL_COL_WIDTH_DP = 180
        private const val ROW_HEIGHT_DP = 64
        private const val VISIBLE_ROWS = 6
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[EpgGridViewModel::class.java]
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val density = resources.displayMetrics.density
        val rowH = (ROW_HEIGHT_DP * density).toInt()
        val gridH = (36 * density + VISIBLE_ROWS * (ROW_HEIGHT_DP + 2) * density).toInt()

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // ── Header: "Today" + clock ──
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (36 * density).toInt())
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }
        val todayLabel = TextView(requireContext()).apply {
            text = "Today"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#243342"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams((CHANNEL_COL_WIDTH_DP * density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
        }
        header.addView(todayLabel)
        val clockView = TextView(requireContext()).apply {
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            textSize = 14f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        header.addView(clockView)
        handler.postDelayed(object : Runnable {
            override fun run() {
                clockView.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                handler.postDelayed(this, 60000L)
            }
        }, 60000L)
        root.addView(header)

        // separator
        root.addView(View(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        })

        // ── Channel list ──
        adapter = ChannelAdapter { channel -> startPlayback(channel.uuid, channel.name, null) }
        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, gridH)
            layoutManager = LinearLayoutManager(context)
            adapter = this@EpgGridFragment.adapter
            isFocusable = false
        }
        root.addView(recyclerView)

        // separator
        root.addView(View(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#444444"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2)
        })

        // ── Detail panel ──
        val detailPanel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(20, 14, 20, 14)
        }

        detailTitle = TextView(requireContext()).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
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
            maxLines = 3
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 4 }
        }
        detailSummary = TextView(requireContext()).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#999999"))
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        detailPanel.addView(detailTitle)
        detailPanel.addView(detailEpisode)
        detailPanel.addView(detailChannelTime)
        detailPanel.addView(detailDesc)
        detailPanel.addView(detailSummary)
        root.addView(detailPanel)

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
                channels = state.channels
                eventsByChannel = state.events.groupBy { it.channelUuid }
                adapter.setData(channels, eventsByChannel)
            }
        }

        viewModel.loadData()
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
    }

    private fun startPlayback(uuid: String?, name: String?, eventTitle: String?) {
        if (uuid == null) return
        startActivity(Intent(requireContext(), PlaybackActivity::class.java).apply {
            putExtra("channel_uuid", uuid)
            putExtra("channel_name", name)
            putExtra("event_title", eventTitle)
        })
    }

    // ──────────────── Adapter ────────────────

    private inner class ChannelAdapter(
        private val onPlay: (Channel) -> Unit
    ) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {

        private var channels: List<Channel> = emptyList()
        private var eventsByChannel: Map<String, List<EpgEvent>> = emptyMap()

        fun setData(channels: List<Channel>, events: Map<String, List<EpgEvent>>) {
            this.channels = channels
            this.eventsByChannel = events
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val density = resources.displayMetrics.density
            val rowH = (ROW_HEIGHT_DP * density).toInt()
            val colW = (CHANNEL_COL_WIDTH_DP * density).toInt()

            val itemView = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH)
                isFocusable = true
                isClickable = true
                setPadding(4, 2, 4, 2)
            }

            // Channel name column
            val channelLabel = LinearLayout(parent.context).apply {
                id = 1
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 4, 8, 4)
                layoutParams = LinearLayout.LayoutParams(colW, ViewGroup.LayoutParams.MATCH_PARENT)
                val nameBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#2C3E50"))
                    cornerRadius = 8f
                }
                background = nameBg

                val displayName = TextView(parent.context).apply {
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                addView(displayName)
            }
            itemView.addView(channelLabel)

            // Program card area
            val programBg = GradientDrawable().apply {
                setColor(Color.parseColor("#1B1B1B"))
                cornerRadius = 8f
            }
            val programCard = TextView(parent.context).apply {
                id = 2
                textSize = 13f
                setTextColor(Color.WHITE)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(14, 8, 14, 8)
                gravity = Gravity.CENTER_VERTICAL
                background = programBg
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = 6 }
            }
            itemView.addView(programCard)

            // Focus highlight
            val focusBg = GradientDrawable().apply {
                setColor(Color.parseColor("#1A73E8"))
                cornerRadius = 10f
            }
            val channelFocusBg = GradientDrawable().apply {
                setColor(Color.parseColor("#3D5A80"))
                cornerRadius = 8f
            }
            val programFocusBg = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2A2A"))
                cornerRadius = 8f
            }

            itemView.onFocusChangeListener = null
            itemView.setOnFocusChangeListener { v, hasFocus ->
                val row = v as LinearLayout
                val cl = row.getChildAt(0) as LinearLayout
                val pt = row.getChildAt(1) as TextView

                if (hasFocus) {
                    v.background = focusBg
                    cl.background = channelFocusBg
                    pt.background = programFocusBg
                    pt.setTextColor(Color.WHITE)
                    pt.setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    v.background = null
                    cl.background = GradientDrawable().apply {
                        setColor(Color.parseColor("#2C3E50"))
                        cornerRadius = 8f
                    }
                    pt.background = programBg
                    pt.setTextColor(Color.WHITE)
                    pt.setTypeface(null, android.graphics.Typeface.NORMAL)
                }

                val pos = (v.layoutParams as? RecyclerView.LayoutParams)?.absoluteAdapterPosition
                if (pos != null && pos in channels.indices) {
                    val ch = channels[pos]
                    val events = eventsByChannel[ch.uuid]
                    val current = events?.firstOrNull()
                    showDetail(ch, current)
                }
            }

            return ViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val channel = channels[position]
            val events = eventsByChannel[channel.uuid]
            val currentEvent = events?.firstOrNull()

            val row = holder.itemView as LinearLayout
            val channelLayout = row.getChildAt(0) as LinearLayout
            val nameText = channelLayout.getChildAt(0) as TextView
            val programText = row.getChildAt(1) as TextView

            nameText.text = buildString {
                channel.number?.let { append("$it  ") }
                channel.name?.let { append(it) }
            }

            if (currentEvent != null) {
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(Date(currentEvent.start * 1000))
                programText.text = "[$timeStr] ${currentEvent.title ?: "—"}"
            } else {
                programText.text = "No program data"
            }

            row.setOnClickListener { onPlay(channel) }
        }

        override fun getItemCount(): Int = channels.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }

    // ──────────────── Detail panel ────────────────

    private fun showDetail(channel: Channel?, event: EpgEvent?) {
        if (event != null && channel != null) {
            detailTitle.text = event.title ?: "—"
            val ep = buildEpisodeString(event)
            detailEpisode.text = ep
            detailEpisode.visibility = if (ep.isNotEmpty()) View.VISIBLE else View.GONE
            detailChannelTime.text = "${channel.name} · ${formatDate(event.start)} ${formatTimeRange(event.start, event.stop)}"
            detailDesc.text = event.description ?: ""
            detailSummary.text = event.summary ?: ""
        } else if (channel != null) {
            detailTitle.text = channel.name ?: ""
            detailEpisode.visibility = View.GONE
            detailChannelTime.text = "No program data"
            detailDesc.text = ""
            detailSummary.text = ""
        }
    }

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
}
