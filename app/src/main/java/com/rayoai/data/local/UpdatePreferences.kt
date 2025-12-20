package com.rayoai.data.local

import android.content.Context
import com.rayoai.domain.model.UpdateChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdatePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val updateChannelFlow = MutableStateFlow(readChannel())

    val updateChannel: StateFlow<UpdateChannel> = updateChannelFlow.asStateFlow()

    fun setUpdateChannel(channel: UpdateChannel) {
        prefs.edit().putString(KEY_UPDATE_CHANNEL, channel.name).apply()
        updateChannelFlow.value = channel
    }

    fun getUpdateChannel(): UpdateChannel = updateChannelFlow.value

    fun savePendingUpdate(downloadId: Long, version: String) {
        prefs.edit()
            .putLong(KEY_PENDING_DOWNLOAD_ID, downloadId)
            .putString(KEY_PENDING_VERSION, version)
            .apply()
    }

    fun getPendingUpdate(): PendingUpdate? {
        val downloadId = prefs.getLong(KEY_PENDING_DOWNLOAD_ID, -1L)
        val version = prefs.getString(KEY_PENDING_VERSION, null)
        if (downloadId <= 0L || version.isNullOrBlank()) {
            return null
        }
        return PendingUpdate(downloadId, version)
    }

    fun clearPendingUpdate() {
        prefs.edit()
            .remove(KEY_PENDING_DOWNLOAD_ID)
            .remove(KEY_PENDING_VERSION)
            .apply()
    }

    private fun readChannel(): UpdateChannel {
        val stored = prefs.getString(KEY_UPDATE_CHANNEL, null)
        return runCatching { UpdateChannel.valueOf(stored ?: UpdateChannel.STABLE.name) }
            .getOrDefault(UpdateChannel.STABLE)
    }

    data class PendingUpdate(
        val downloadId: Long,
        val version: String
    )

    private companion object {
        const val PREFS_NAME = "update_prefs"
        const val KEY_UPDATE_CHANNEL = "update_channel"
        const val KEY_PENDING_DOWNLOAD_ID = "pending_download_id"
        const val KEY_PENDING_VERSION = "pending_version"
    }
}
