package com.tvstreamnode.tv.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tvstreamnode.tv.data.model.Channel
import com.tvstreamnode.tv.data.model.EpgEvent

/**
 * Disk cache for channel list + current EPG events (SharedPreferences + Gson).
 * Enables instant cold-start playback and channel zapping before the first
 * network fetch completes. Entries carry a timestamp; EPG honors the same
 * 5-minute validity window as the in-memory EpgCache.
 */
object DataCache {

    private const val PREFS_NAME = "tvstreamnode_datacache"
    private const val KEY_CHANNELS = "channels_json"
    private const val KEY_CHANNELS_TIME = "channels_time"
    private const val KEY_EPG = "epg_json"
    private const val KEY_EPG_TIME = "epg_time"
    private const val EPG_TTL_MS = 300_000L
    private const val CHANNELS_TTL_MS = 24 * 60 * 60 * 1000L // 24h: names rarely change

    private val gson = Gson()
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    // ── Channels ──

    fun saveChannels(channels: List<Channel>) {
        val p = prefs ?: return
        p.edit()
            .putString(KEY_CHANNELS, gson.toJson(channels))
            .putLong(KEY_CHANNELS_TIME, System.currentTimeMillis())
            .apply()
    }

    fun loadChannels(): List<Channel>? {
        val p = prefs ?: return null
        val json = p.getString(KEY_CHANNELS, null) ?: return null
        val time = p.getLong(KEY_CHANNELS_TIME, 0L)
        if (System.currentTimeMillis() - time > CHANNELS_TTL_MS) return null
        return try {
            gson.fromJson(json, object : TypeToken<List<Channel>>() {}.type)
        } catch (_: Exception) {
            null
        }
    }

    // ── EPG (current events, same shape as EpgCache) ──

    fun saveEpg(events: List<EpgEvent>) {
        val p = prefs ?: return
        p.edit()
            .putString(KEY_EPG, gson.toJson(events))
            .putLong(KEY_EPG_TIME, System.currentTimeMillis())
            .apply()
    }

    /** Returns cached events if still fresh (5-min TTL), else null. */
    fun loadEpg(): List<EpgEvent>? {
        val p = prefs ?: return null
        val json = p.getString(KEY_EPG, null) ?: return null
        val time = p.getLong(KEY_EPG_TIME, 0L)
        if (System.currentTimeMillis() - time > EPG_TTL_MS) return null
        return try {
            gson.fromJson(json, object : TypeToken<List<EpgEvent>>() {}.type)
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
