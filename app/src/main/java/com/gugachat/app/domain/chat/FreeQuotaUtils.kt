package com.gugachat.app.domain.chat

import com.gugachat.app.data.api.GroupModelFreeQuotaStatusDto
import com.gugachat.app.data.api.OnlineChatGroupDto
import com.gugachat.app.data.api.OnlineChatModelDto
import com.gugachat.app.data.api.OnlineImageKeyDto
import com.gugachat.app.data.api.OnlineImageModelDto
import kotlin.math.roundToLong

object FreeQuotaUtils {
    fun hasAvailableFreeQuota(status: GroupModelFreeQuotaStatusDto?): Boolean {
        if (status == null) return false
        return status.remaining > 0.0 || status.daily_limit <= 0.0
    }

    fun sortChatGroupsByFreeQuota(groups: List<OnlineChatGroupDto>): List<OnlineChatGroupDto> =
        groups.sortedWith(compareByDescending<OnlineChatGroupDto> { it.has_free_quotas }.thenBy { it.name.lowercase() })

    fun sortChatModelsByFreeQuota(models: List<OnlineChatModelDto>): List<OnlineChatModelDto> =
        models.sortedWith(
            compareByDescending<OnlineChatModelDto> { it.free_quota != null }
                .thenByDescending { hasAvailableFreeQuota(it.free_quota) }
                .thenBy { it.id.lowercase() },
        )

    fun sortImageKeysByFreeQuota(keys: List<OnlineImageKeyDto>): List<OnlineImageKeyDto> =
        keys.sortedWith(compareByDescending<OnlineImageKeyDto> { it.has_free_quotas }.thenBy { it.name.lowercase() })

    fun sortImageModelsByFreeQuota(models: List<OnlineImageModelDto>): List<OnlineImageModelDto> =
        models.sortedWith(
            compareByDescending<OnlineImageModelDto> { it.free_quota != null }
                .thenByDescending { hasAvailableFreeQuota(it.free_quota) }
                .thenBy { it.id.lowercase() },
        )

    fun formatFreeQuotaBadge(status: GroupModelFreeQuotaStatusDto?, isZh: Boolean = true): String? {
        if (status == null) return null
        val remaining = formatAmount(status.remaining)
        val limit = formatAmount(status.daily_limit)
        return when (status.quota_type.lowercase()) {
            "usd" -> if (isZh) "限免余 \$$remaining/\$$limit" else "Free \$$remaining/\$$limit"
            else -> if (isZh) "限免余 $remaining/$limit 次" else "Free $remaining/$limit"
        }
    }

    private fun formatAmount(value: Double): String {
        if (!value.isFinite()) return "0"
        val rounded = (value * 100.0).roundToLong() / 100.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
}
