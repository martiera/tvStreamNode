package com.tvstreamnode.tv

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.lifecycle.ViewModelProvider
import com.tvstreamnode.tv.data.model.Channel
import com.tvstreamnode.tv.data.model.EpgEvent

class ChannelBrowseFragment : Fragment() {

    private lateinit var viewModel: ChannelBrowseViewModel
    private lateinit var headerView: TextView
    private lateinit var horizontalGrid: HorizontalGridView
    private lateinit var cardPresenter: ChannelCardPresenter

    private var channels: List<Channel> = emptyList()
    private var events: Map<String, EpgEvent> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[ChannelBrowseViewModel::class.java]
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#121212"))
            isFocusable = true
        }

        // ── Top row: "All Channels" header ──
        val topRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * resources.displayMetrics.density).toInt())
            setPadding(24, 0, 24, 0)
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            gravity = Gravity.CENTER_VERTICAL
        }

        headerView = TextView(requireContext()).apply {
            text = "‹  All Channels"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            isFocusable = true
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    horizontalGrid.requestFocus()
                    true
                } else false
            }
        }
        topRow.addView(headerView)
        root.addView(topRow)

        // Separator
        root.addView(View(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        })

        // ── Horizontal card grid ──
        cardPresenter = ChannelCardPresenter()
        cardPresenter.onClickListener = { channel, event ->
            startActivity(Intent(requireContext(), PlaybackActivity::class.java).apply {
                putExtra("channel_uuid", channel.uuid)
                putExtra("channel_name", channel.name)
                putExtra("event_title", event?.title)
            })
        }

        horizontalGrid = HorizontalGridView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setNumRows(1)
            setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
            setItemSpacing((8 * resources.displayMetrics.density).toInt())
            isFocusable = true
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    headerView.requestFocus()
                    true
                } else false
            }
        }
        root.addView(horizontalGrid)

        root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
                true
            } else if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                parentFragmentManager.popBackStack()
                true
            } else {
                false
            }
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            if (!state.isLoading) {
                channels = state.channels
                events = state.currentEvents
                populateGrid()
            }
        }

        viewModel.loadData()
        return root
    }

    private fun populateGrid() {
        if (channels.isEmpty()) return

        val adapter = ArrayObjectAdapter(cardPresenter)
        for (channel in channels) {
            adapter.add(Pair(channel, events[channel.uuid]))
        }

        val bridgeAdapter = ItemBridgeAdapter(adapter)
        horizontalGrid.adapter = bridgeAdapter

        // Auto-focus last viewed channel card, or first card
        horizontalGrid.post {
            val selectUuid = arguments?.getString("select_uuid") ?: ""
            val selectIndex = if (selectUuid.isNotEmpty()) {
                channels.indexOfFirst { it.uuid == selectUuid }
            } else -1

            val focusIndex = if (selectIndex >= 0) selectIndex else 0
            if (adapter.size() > 0) {
                horizontalGrid.setSelectedPosition(focusIndex)
                val item = adapter.get(focusIndex)
                if (item is Pair<*, *>) {
                    val channel = item.first as? Channel
                    val event = item.second as? EpgEvent
                    val detailText = event?.title ?: "Navigate to a program"
                    // Simple header update
                    headerView.text = "‹  ${channel?.name ?: "All Channels"} — $detailText"
                }
            }
        }
    }
}
