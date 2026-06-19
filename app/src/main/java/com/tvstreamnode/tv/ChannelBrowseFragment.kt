package com.tvstreamnode.tv

import android.app.AlertDialog
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
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.lifecycle.ViewModelProvider
import com.tvstreamnode.tv.data.model.Channel
import com.tvstreamnode.tv.data.model.EpgEvent
import com.tvstreamnode.tv.util.ListManager
import com.tvstreamnode.tv.util.Preferences

class ChannelBrowseFragment : Fragment() {

    private lateinit var viewModel: ChannelBrowseViewModel
    private lateinit var headerRow: LinearLayout
    private lateinit var headerScroll: HorizontalScrollView
    private lateinit var horizontalGrid: HorizontalGridView
    private lateinit var cardPresenter: ChannelCardPresenter

    private var channels: List<Channel> = emptyList()
    private var events: Map<String, EpgEvent> = emptyMap()
    private var selectedListId: String? = null
    private var sortedChannels: List<Channel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[ChannelBrowseViewModel::class.java]
        selectedListId = arguments?.getString("list_id")
        ListManager.init(requireContext())
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // ── Header row with filter tags ──
        headerScroll = HorizontalScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * resources.displayMetrics.density).toInt())
        }
        headerRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        headerScroll.addView(headerRow)
        root.addView(headerScroll)

        // ── Card grid ──
        cardPresenter = ChannelCardPresenter()
        cardPresenter.onClickListener = { channel, event ->
            startActivity(Intent(requireContext(), PlaybackActivity::class.java).apply {
                putExtra("channel_uuid", channel.uuid)
                putExtra("channel_name", channel.name)
                putExtra("event_title", event?.title)
            })
        }
        cardPresenter.onDownListener = { channel ->
            showCardListMenuForChannel(channel)
        }

        horizontalGrid = HorizontalGridView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setNumRows(1)
            setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
            setItemSpacing((8 * resources.displayMetrics.density).toInt())
            isFocusable = true
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    headerScroll.requestFocus()
                    true
                } else if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    val pos = horizontalGrid.selectedPosition
                    val bridge = horizontalGrid.adapter
                    if (bridge != null && pos >= bridge.itemCount - 1) {
                        horizontalGrid.setSelectedPosition(0)
                        true
                    } else false
                } else if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    val pos = horizontalGrid.selectedPosition
                    if (pos <= 0) {
                        val bridge = horizontalGrid.adapter
                        if (bridge != null) horizontalGrid.setSelectedPosition(bridge.itemCount - 1)
                        true
                    } else false
                } else if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    showCardListMenu()
                    true
                } else false
            }
        }

        val centerWrapper = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            horizontalGrid.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_VERTICAL }
            addView(horizontalGrid)
        }
        root.addView(centerWrapper)

        root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
                true
            } else if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                parentFragmentManager.popBackStack()
                true
            } else false
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            if (!state.isLoading) {
                channels = state.channels
                events = state.currentEvents
                buildHeader()
                populateGrid()
            }
        }

        viewModel.loadData()
        return root
    }

    private fun buildHeader() {
        headerRow.removeAllViews()

        // "All Channels" tag
        val allTag = makeHeaderTag("All Channels (${channels.size})", selectedListId == null)
        allTag.setOnClickListener {
            selectedListId = null
            buildHeader()
            populateGrid()
            horizontalGrid.requestFocus()
        }
        headerRow.addView(allTag)

        // List tags
        for (list in ListManager.getAll()) {
            val tag = makeHeaderTag("${list.name} (${list.channelIds.size})", list.id == selectedListId)
            tag.setOnClickListener {
                selectedListId = list.id
                buildHeader()
                populateGrid()
                horizontalGrid.requestFocus()
            }
            headerRow.addView(tag)
        }
    }

    private fun makeHeaderTag(text: String, selected: Boolean): TextView {
        val density = resources.displayMetrics.density
        val tag = TextView(requireContext())
        tag.text = text
        tag.textSize = 14f
        tag.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        tag.setTextColor(if (selected) Color.WHITE else Color.parseColor("#AAAAAA"))
        tag.setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
        tag.gravity = Gravity.CENTER_VERTICAL
        val bg = GradientDrawable().apply {
            setColor(if (selected) Color.parseColor("#1A73E8") else Color.parseColor("#2A2A2A"))
            cornerRadius = (20 * density).toFloat()
        }
        tag.background = bg
        tag.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            rightMargin = (8 * density).toInt()
            topMargin = (6 * density).toInt()
            bottomMargin = (6 * density).toInt()
        }
        tag.isFocusable = true
        tag.isClickable = true
        val selBg = GradientDrawable().apply {
            setColor(Color.parseColor("#1A73E8"))
            cornerRadius = (20 * density).toFloat()
        }
        val selBgFocused = GradientDrawable().apply {
            setColor(Color.parseColor("#3D8AF7"))
            cornerRadius = (20 * density).toFloat()
            setStroke(2, Color.WHITE)
        }
        val normBg = GradientDrawable().apply {
            setColor(Color.parseColor("#2A2A2A"))
            cornerRadius = (20 * density).toFloat()
        }
        val normBgFocused = GradientDrawable().apply {
            setColor(Color.parseColor("#3D3D3D"))
            cornerRadius = (20 * density).toFloat()
            setStroke(2, Color.parseColor("#1A73E8"))
        }
        tag.setOnFocusChangeListener { v, hasFocus ->
            val tv = v as TextView
            if (hasFocus) {
                tv.background = if (selected) selBgFocused else normBgFocused
                tv.setTextColor(Color.WHITE)
                tv.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                tv.background = if (selected) selBg else normBg
                tv.setTextColor(if (selected) Color.WHITE else Color.parseColor("#AAAAAA"))
                tv.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }
        tag.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> { horizontalGrid.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val idx = headerRow.indexOfChild(tag)
                        val nextIdx = if (idx < headerRow.childCount - 1) idx + 1 else 0
                        headerRow.getChildAt(nextIdx).requestFocus(); true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        val idx = headerRow.indexOfChild(tag)
                        val nextIdx = if (idx > 0) idx - 1 else headerRow.childCount - 1
                        headerRow.getChildAt(nextIdx).requestFocus(); true
                    }
                    else -> false
                }
            } else false
        }
        return tag
    }

    private fun populateGrid() {
        if (channels.isEmpty()) return

        val filtered = if (selectedListId != null) {
            val list = ListManager.getAll().find { it.id == selectedListId }
            channels.filter { list?.channelIds?.contains(it.uuid) ?: false }
        } else channels

        val sorted = filtered.sortedBy { it.name?.lowercase() ?: "" }
        sortedChannels = sorted

        val adapter = ArrayObjectAdapter(cardPresenter)
        for (channel in sorted) {
            adapter.add(Pair(channel, events[channel.uuid]))
        }

        val bridgeAdapter = ItemBridgeAdapter(adapter)
        horizontalGrid.adapter = bridgeAdapter

        horizontalGrid.post {
            if (adapter.size() > 0) {
                horizontalGrid.setSelectedPosition(0)
                val vh = horizontalGrid.findViewHolderForAdapterPosition(0)
                if (vh != null) vh.itemView.requestFocus()
                else horizontalGrid.requestFocus()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reFocusOnLastChannel()
    }

    private fun reFocusOnLastChannel() {
        if (channels.isEmpty() || !::horizontalGrid.isInitialized) return
        val uuid = Preferences(requireContext()).lastChannelUuid
        if (uuid.isEmpty()) return
        if (selectedListId != null) {
            val list = ListManager.getAll().find { it.id == selectedListId }
            if (list != null && uuid !in list.channelIds) return
        }
        val filtered = if (selectedListId != null) {
            val list = ListManager.getAll().find { it.id == selectedListId }
            channels.filter { list?.channelIds?.contains(it.uuid) ?: false }
        } else channels
        val sorted = filtered.sortedBy { it.name?.lowercase() ?: "" }
        val index = sorted.indexOfFirst { it.uuid == uuid }
        if (index < 0) return
        horizontalGrid.post {
            horizontalGrid.setSelectedPosition(index)
            val vh = horizontalGrid.findViewHolderForAdapterPosition(index)
            if (vh != null) vh.itemView.requestFocus()
            else horizontalGrid.requestFocus()
        }
    }

    private fun showCardListMenu() {
        val pos = horizontalGrid.selectedPosition
        if (pos < 0 || pos >= sortedChannels.size) return
        showCardListMenuForChannel(sortedChannels[pos])
    }

    private fun showCardListMenuForChannel(channel: Channel) {
        val uuid = channel.uuid
        val chName = channel.name ?: ""
        val lists = ListManager.getAll()
        if (lists.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "No lists — create one in Lists menu", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val density = resources.displayMetrics.density
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (12 * density).toInt(), (24 * density).toInt(), (8 * density).toInt())
        }

        for (list in lists) {
            val isInList = uuid in list.channelIds
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt())
                isFocusable = true
                isClickable = true
                val normalBg = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
                val focusBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#2A2A2A"))
                    setStroke(2, Color.parseColor("#1A73E8"))
                }
                setOnFocusChangeListener { v, hasFocus -> v.background = if (hasFocus) focusBg else normalBg }
                setOnClickListener {
                    ListManager.toggleChannel(list.id, uuid, !isInList)
                    val msg = if (!isInList) "Added to ${list.name}" else "Removed from ${list.name}"
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    showCardListMenu()
                }
            }

            row.addView(TextView(requireContext()).apply {
                    text = if (isInList) "☑" else "☐"
                textSize = 20f
                setTextColor(if (isInList) Color.parseColor("#1A73E8") else Color.parseColor("#666666"))
                layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(requireContext()).apply {
                text = list.name
                textSize = 16f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            content.addView(row)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Lists — $chName")
            .setView(content)
            .setPositiveButton("Close", null)
            .show()
    }
}
