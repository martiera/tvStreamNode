package com.tvstreamnode.tv.util

import com.tvstreamnode.tv.data.model.EpgEvent

object EpgCache {

    private var currentEvents: Map<String, EpgEvent> = emptyMap()
    private var cacheTime: Long = 0L

    fun get(uuid: String): EpgEvent? = currentEvents[uuid]

    fun put(events: List<EpgEvent>) {
        currentEvents = events.associateBy { it.channelUuid }
        cacheTime = System.currentTimeMillis()
    }

    fun isValid(): Boolean = System.currentTimeMillis() - cacheTime < 300_000L

    fun clear() {
        currentEvents = emptyMap()
        cacheTime = 0L
    }
}
