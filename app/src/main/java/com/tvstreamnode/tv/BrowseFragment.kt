package com.tvstreamnode.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.ViewModelProvider
import com.tvstreamnode.tv.data.model.Channel
import com.tvstreamnode.tv.data.model.EpgEvent
import com.tvstreamnode.tv.ui.ChannelCardPresenter
import com.tvstreamnode.tv.ui.EpgCardPresenter

class BrowseFragment : BrowseSupportFragment(), OnItemViewClickedListener {

    private lateinit var viewModel: BrowseViewModel
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[BrowseViewModel::class.java]

        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = resources.getColor(R.color.primary)

        adapter = rowsAdapter

        setOnItemViewClickedListener(this)

        view?.isFocusable = true
        view?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
                true
            } else {
                false
            }
        }

        viewModel.state.observe(this) { state ->
            if (!state.isLoading) {
                populateRows(state.channels, state.currentEvents)
            }
        }

        viewModel.loadData()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadData()
    }

    private fun populateRows(channels: List<Channel>, events: List<EpgEvent>) {
        rowsAdapter.clear()

        val nowPlayingRow = createNowPlayingRow(channels, events)
        if (nowPlayingRow != null) {
            rowsAdapter.add(nowPlayingRow)
        }

        // Per-channel EPG rows
        for (channel in channels) {
            val channelEvents = events.filter { it.channelUuid == channel.uuid }
            if (channelEvents.isNotEmpty()) {
                val adapter = ArrayObjectAdapter(EpgCardPresenter()).apply {
                    addAll(0, channelEvents)
                }
                val header = HeaderItem(channel.uuid, channel.name ?: channel.uuid)
                rowsAdapter.add(ListRow(header, adapter))
            }
        }

        // Channel list row (fallback if no EPG)
        if (events.isEmpty()) {
            val adapter = ArrayObjectAdapter(ChannelCardPresenter()).apply {
                addAll(0, channels)
            }
            val header = HeaderItem("channels", "Channels")
            rowsAdapter.add(ListRow(header, adapter))
        }
    }

    private fun createNowPlayingRow(channels: List<Channel>, events: List<EpgEvent>): ListRow? {
        if (events.isEmpty()) return null

        val eventMap = events.associateBy { it.channelUuid }
        val adapter = ArrayObjectAdapter(EpgCardPresenter())

        for (channel in channels) {
            val event = eventMap[channel.uuid]
            if (event != null) {
                adapter.add(event)
            }
        }

        if (adapter.size() == 0) return null

        return ListRow(HeaderItem("now", getString(R.string.now_playing)), adapter)
    }

    override fun onItemClicked(
        itemViewHolder: Presenter.ViewHolder?,
        item: Any?,
        rowViewHolder: RowPresenter.ViewHolder?,
        row: Row?
    ) {
        when (item) {
            is EpgEvent -> {
                val intent = Intent(requireContext(), PlaybackActivity::class.java).apply {
                    putExtra("channel_uuid", item.channelUuid)
                    putExtra("channel_name", item.channelName)
                    putExtra("event_title", item.title)
                }
                startActivity(intent)
            }
            is Channel -> {
                val intent = Intent(requireContext(), PlaybackActivity::class.java).apply {
                    putExtra("channel_uuid", item.uuid)
                    putExtra("channel_name", item.name)
                }
                startActivity(intent)
            }
        }
    }
}
