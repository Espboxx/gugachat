package com.gugachat.app.ui.studio

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.theme.GuPill
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.data.api.AppJson
import com.gugachat.app.data.local.CharacterCardEntity
import com.gugachat.app.domain.chat.RpPresetTemplate
import kotlin.math.abs

internal enum class CharacterFilterKind { ALL, TAVERN, ORIGINAL, OFFICIAL }
internal enum class SettingFilterKind { ALL, WORLDBOOK, PRESET, REGEX }

internal sealed interface LaunchTarget {
    data class Card(val card: CharacterCardEntity) : LaunchTarget
    data class OfficialPreset(val preset: RpPresetTemplate) : LaunchTarget
}

internal enum class LaunchSheetSubpage { MAIN, PICK_PERSONA, PICK_WORLDBOOKS, PICK_PRESET }

internal data class CardGradientSpec(
    val start: Color,
    val end: Color,
    val accent: Color,
    val icon: ImageVector,
)

internal fun resolveCardGradient(seed: String): CardGradientSpec {
    val palettes = listOf(
        CardGradientSpec(Color(0xFF2E3B6E), Color(0xFF141830), Color(0xFF9FB8FF), Icons.Default.AutoStories),
        CardGradientSpec(Color(0xFF5D2A42), Color(0xFF21101C), Color(0xFFF6B7CB), Icons.Default.TheaterComedy),
        CardGradientSpec(Color(0xFF1F4E5F), Color(0xFF0E212B), Color(0xFF7BE0EA), Icons.Default.Public),
        CardGradientSpec(Color(0xFF543C22), Color(0xFF22170D), Color(0xFFF6C67D), Icons.Default.EmojiEvents),
        CardGradientSpec(Color(0xFF2C4A3B), Color(0xFF122019), Color(0xFF8BE2B6), Icons.Default.Landscape),
        CardGradientSpec(Color(0xFF423266), Color(0xFF1A122E), Color(0xFFCBB6FF), Icons.Default.AutoAwesome),
    )
    val idx = abs(seed.hashCode()) % palettes.size
    return palettes[idx]
}

internal fun resolvePresetGradient(presetId: String): CardGradientSpec = when (presetId.lowercase()) {
    "palace_intrigue" -> CardGradientSpec(Color(0xFF6E1D34), Color(0xFF2B0E1A), Color(0xFFF6D38A), Icons.Default.EmojiEvents)
    "modern_tavern" -> CardGradientSpec(Color(0xFF2B2D63), Color(0xFF141736), Color(0xFFF6B95E), Icons.Default.AutoStories)
    "fantasy_rpg" -> CardGradientSpec(Color(0xFF5A3718), Color(0xFF251A10), Color(0xFFFCD34D), Icons.Default.Security)
    "cyberpunk_noir" -> CardGradientSpec(Color(0xFF0E4A60), Color(0xFF091B2C), Color(0xFF67E8F9), Icons.Default.Code)
    "wuxia" -> CardGradientSpec(Color(0xFF2F3D52), Color(0xFF141B28), Color(0xFFE2E8F0), Icons.Default.Landscape)
    "steampunk_coc" -> CardGradientSpec(Color(0xFF453224), Color(0xFF1C1612), Color(0xFFD9B27C), Icons.Default.Search)
    "space_mercenary" -> CardGradientSpec(Color(0xFF1B3A63), Color(0xFF0B1830), Color(0xFF7DD3FC), Icons.Default.Public)
    "modern_romance" -> CardGradientSpec(Color(0xFF832E55), Color(0xFF3A162D), Color(0xFFFBCFE8), Icons.Default.Favorite)
    else -> resolveCardGradient(presetId)
}

internal fun CharacterCardEntity.isOriginalCard(): Boolean {
    val s = sourceSpec.lowercase()
    return s == "gugachat_original" || s == "legacy_rp" || s.isBlank()
}

internal fun CharacterCardEntity.specBadgeLabel(isZh: Boolean): String {
    val s = sourceSpec.lowercase()
    return when {
        s.contains("v3") || s.contains("charx") -> "V3"
        s.contains("v2") || s.contains("tavern") || s.contains("chara") -> "V2"
        s == "legacy_rp" -> if (isZh) "归档" else "Legacy"
        else -> if (isZh) "原创" else "Original"
    }
}

internal fun CharacterCardEntity.allGreetings(): List<String> {
    val alts = decodeJsonList<String>(alternateGreetingsJson)
    return (listOf(firstMes) + alts).map { it.trim() }.filter { it.isNotEmpty() }
}

internal fun RpPresetTemplate.allGreetings(): List<String> =
    (listOf(config.greeting) + config.alternateGreetings).map { it.trim() }.filter { it.isNotEmpty() }

internal inline fun <reified T> decodeJsonList(raw: String?): List<T> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { AppJson.decodeFromString<List<T>>(raw) }.getOrDefault(emptyList())
}

@Composable
internal fun CharacterFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    GuPill(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = Modifier.height(32.dp),
    )
}

@Composable
internal fun LaunchSubpageHeader(
    title: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalAppTheme.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = c.ink)
        }
        Text(
            text = title,
            color = c.ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClose) {
            Text("✕", color = c.muted, fontSize = 15.sp)
        }
    }
}
