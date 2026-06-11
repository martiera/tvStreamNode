package com.tvstreamnode.tv.data.repository

import com.tvstreamnode.tv.data.api.TvheadendApi
import com.tvstreamnode.tv.data.model.EpgEvent

class EpgRepository(
    private val api: TvheadendApi
) {

    suspend fun getEpgForChannel(channelUuid: String, start: Long = System.currentTimeMillis() / 1000): Result<List<EpgEvent>> {
        return try {
            val response = api.getEpgEvents(channelUuid = channelUuid, start = start)
            Result.success(response.entries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentEvents(): Result<List<EpgEvent>> {
        return try {
            val response = api.getCurrentEpgEvents()
            Result.success(response.entries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
