package com.tvstreamnode.tv.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tvstreamnode.tv.data.model.ChannelList

object ListManager {

    private const val PREFS_KEY = "channel_lists"
    private var cached: List<ChannelList>? = null
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("tvstreamnode", Context.MODE_PRIVATE)
    }

    fun getAll(): List<ChannelList> {
        if (cached != null) return cached!!
        val json = prefs?.getString(PREFS_KEY, null)
        cached = if (json != null) {
            try { Gson().fromJson(json, object : TypeToken<List<ChannelList>>() {}.type) }
            catch (_: Exception) { emptyList() }
        } else emptyList()
        return cached!!
    }

    fun create(name: String): ChannelList {
        val list = ChannelList(name = name)
        val all = getAll().toMutableList()
        all.add(list)
        save(all)
        return list
    }

    fun delete(id: String) {
        val all = getAll().toMutableList()
        all.removeAll { it.id == id }
        save(all)
    }

    fun addChannel(listId: String, channelUuid: String) {
        val all = getAll().toMutableList()
        val idx = all.indexOfFirst { it.id == listId }
        if (idx >= 0 && channelUuid !in all[idx].channelIds) {
            all[idx] = all[idx].copy(
                channelIds = all[idx].channelIds + channelUuid
            )
            save(all)
        }
    }

    fun removeChannel(listId: String, channelUuid: String) {
        val all = getAll().toMutableList()
        val idx = all.indexOfFirst { it.id == listId }
        if (idx >= 0) {
            all[idx] = all[idx].copy(
                channelIds = all[idx].channelIds.filter { it != channelUuid }
            )
            save(all)
        }
    }

    fun getListNamesForChannel(channelUuid: String): List<String> {
        return getAll().filter { channelUuid in it.channelIds }.map { it.name }
    }

    fun getListIdsForChannel(channelUuid: String): List<String> {
        return getAll().filter { channelUuid in it.channelIds }.map { it.id }
    }

    fun toggleChannel(listId: String, channelUuid: String, add: Boolean) {
        if (add) addChannel(listId, channelUuid) else removeChannel(listId, channelUuid)
    }

    private fun save(lists: List<ChannelList>) {
        val json = Gson().toJson(lists)
        prefs?.edit()?.putString(PREFS_KEY, json)?.apply()
        cached = lists
    }
}
