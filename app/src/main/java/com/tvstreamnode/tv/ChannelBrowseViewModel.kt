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

data class ChannelBrowseState(
    val channels: List<Channel> = emptyList(),
    val currentEvents: Map<String, EpgEvent> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class ChannelBrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Preferences(application)
    private val api = RetrofitClient.getApi(prefs)
    private val channelRepo = ChannelRepository(api)
    private val epgRepo = EpgRepository(api)

    private val _state = MutableLiveData(ChannelBrowseState())
    val state: LiveData<ChannelBrowseState> = _state

    private var cacheChannels: List<Channel>? = null
    private var cacheEvents: Map<String, EpgEvent>? = null
    private var cacheTime: Long = 0L

    fun loadData(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cacheChannels != null && now - cacheTime < 300_000L) {
            _state.value = ChannelBrowseState(
                channels = cacheChannels!!,
                currentEvents = cacheEvents!!
            )
            return
        }

        _state.value = _state.value?.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val channelsResult = channelRepo.getChannels()
            val epgResult = epgRepo.getCurrentEvents()

            val channels = channelsResult.getOrNull() ?: emptyList()
            val events = epgResult.getOrNull() ?: emptyList()
            val currentByChannel = events.associateBy { it.channelUuid }

            val error = when {
                channelsResult.isFailure -> channelsResult.exceptionOrNull()?.message
                epgResult.isFailure -> epgResult.exceptionOrNull()?.message
                else -> null
            }

            cacheChannels = channels
            cacheEvents = currentByChannel
            cacheTime = now

            _state.value = ChannelBrowseState(
                channels = channels,
                currentEvents = currentByChannel,
                isLoading = false,
                error = error
            )
        }
    }
}
