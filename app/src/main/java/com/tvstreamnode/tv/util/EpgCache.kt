package com.tvstreamnode.tv.util

import com.tvstreamnode.tv.data.model.EpgEvent

object EpgCache {

    private var channelEvents: Map<String, List<EpgEvent>> = emptyMap()
    private var cacheTime: Long = 0L

    fun get(uuid: String): List<EpgEvent> = channelEvents[uuid] ?: emptyList()

    fun put(events: List<EpgEvent>) {
        channelEvents = events.groupBy { it.channelUuid }
        cacheTime = System.currentTimeMillis()
    }

    fun isValid(): Boolean = System.currentTimeMillis() - cacheTime < 300_000L

    fun clear() {
        channelEvents = emptyMap()
        cacheTime = 0L
    }
}
