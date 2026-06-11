package com.tvstreamnode.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.tvstreamnode.tv.data.api.RetrofitClient
import com.tvstreamnode.tv.data.model.Channel
import com.tvstreamnode.tv.data.model.EpgEvent
import com.tvstreamnode.tv.data.repository.ChannelRepository
import com.tvstreamnode.tv.data.repository.EpgRepository
import com.tvstreamnode.tv.util.Preferences
import kotlinx.coroutines.launch

data class BrowseState(
    val channels: List<Channel> = emptyList(),
    val currentEvents: List<EpgEvent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class BrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Preferences(application)
    private val api = RetrofitClient.getApi(prefs)
    private val channelRepo = ChannelRepository(api)
    private val epgRepo = EpgRepository(api)

    private val _state = MutableLiveData(BrowseState())
    val state: LiveData<BrowseState> = _state

    fun loadData() {
        if (!prefs.isConfigured) {
            _state.value = _state.value?.copy(error = "Server not configured")
            return
        }

        _state.value = _state.value?.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val channelsResult = channelRepo.getChannels()
            val epgResult = epgRepo.getCurrentEvents()

            val channels = channelsResult.getOrNull() ?: emptyList()
            val events = epgResult.getOrNull() ?: emptyList()

            val error = when {
                channelsResult.isFailure -> channelsResult.exceptionOrNull()?.message
                epgResult.isFailure -> epgResult.exceptionOrNull()?.message
                else -> null
            }

            _state.value = BrowseState(
                channels = channels,
                currentEvents = events,
                isLoading = false,
                error = error
            )
        }
    }

    fun getStreamUrl(channelUuid: String): String {
        val base = prefs.serverUrl.trimEnd('/')
        return "$base/stream/channel/$channelUuid"
    }

    fun getHlsStreamUrl(channelUuid: String): String {
        val base = prefs.serverUrl.trimEnd('/')
        return "$base/stream/channel/$channelUuid/hls"
    }
}
