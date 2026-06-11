package com.tvstreamnode.tv.data.api

import com.tvstreamnode.tv.data.model.ChannelGridResponse
import com.tvstreamnode.tv.data.model.EpgGridResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TvheadendApi {

    @GET("api/channel/grid")
    suspend fun getChannels(
        @Query("limit") limit: Int = 500
    ): ChannelGridResponse

    @GET("api/epg/events/grid")
    suspend fun getEpgEvents(
        @Query("channel") channelUuid: String,
        @Query("start") start: Long,
        @Query("limit") limit: Int = 50,
        @Query("sort") sort: String = "start"
    ): EpgGridResponse

    @GET("api/epg/events/grid")
    suspend fun getCurrentEpgEvents(
        @Query("limit") limit: Int = 500,
        @Query("mode") mode: String = "now",
        @Query("sort") sort: String = "start"
    ): EpgGridResponse

    @GET("api/epg/events/grid")
    suspend fun getEpgInRange(
        @Query("filter", encoded = true) filter: String,
        @Query("limit") limit: Int = 2000,
        @Query("sort") sort: String = "start"
    ): EpgGridResponse

    @GET("api/epg/events/grid")
    suspend fun getEpgInRangeChannel(
        @Query("filter", encoded = true) filter: String,
        @Query("channel") channelUuid: String,
        @Query("limit") limit: Int = 10,
        @Query("sort") sort: String = "start"
    ): EpgGridResponse

    @GET("api/channel/grid")
    suspend fun testConnection(): ChannelGridResponse
}
