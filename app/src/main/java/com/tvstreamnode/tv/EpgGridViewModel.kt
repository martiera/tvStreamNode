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

data class EpgGridState(
    val channels: List<Channel> = emptyList(),
    val events: List<EpgEvent> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class EpgGridViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Preferences(application)
    private val api = RetrofitClient.getApi(prefs)
    private val channelRepo = ChannelRepository(api)
    private val epgRepo = EpgRepository(api)

    private val _state = MutableLiveData(EpgGridState())
    val state: LiveData<EpgGridState> = _state

    private var cachedChannels: List<Channel>? = null
    private var cachedEvents: List<EpgEvent>? = null
    private var cacheTime: Long = 0L

    // Load 6-hour window and cache in memory
    fun loadData(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedChannels != null && now - cacheTime < 300_000L) {
            _state.value = EpgGridState(
                channels = cachedChannels!!,
                events = cachedEvents!!
            )
            return
        }

        _state.value = _state.value?.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val nowSec = System.currentTimeMillis() / 1000
            val rangeStart = nowSec - 10800L // 3 hours before
            val rangeEnd = nowSec + 10800L   // 3 hours after

            val channelsResult = channelRepo.getChannels()
            val epgResult = epgRepo.getEpgInRange(start = rangeStart, end = rangeEnd)

            val channels = channelsResult.getOrNull() ?: emptyList()
            val events = epgResult.getOrNull() ?: emptyList()

            val error = when {
                channelsResult.isFailure -> channelsResult.exceptionOrNull()?.message
                epgResult.isFailure -> epgResult.exceptionOrNull()?.message
                else -> null
            }

            cachedChannels = channels
            cachedEvents = events
            cacheTime = now

            _state.value = EpgGridState(
                channels = channels,
                events = events,
                isLoading = false,
                error = error
            )
        }
    }
}
