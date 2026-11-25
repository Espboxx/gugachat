package com.gugachat.app.core.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugachat.app.core.i18n.LocalAppStrings

/**
 * Framed fixed-height scrollable list box with an integrated single-line search bar
 * for selecting groups, models, or other searchable collections.
 */
@Composable
fun <T> GuSearchableListBox(
    title: String,
    items: List<T>,
    totalCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    emptyText: String,
    noMatchText: String,
    listHeight: Dp,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    selectedSummary: String? = null,
    topExtraItem: (@Composable () -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    val strings = LocalAppStrings.current
    val theme = LocalAppTheme.current
    val c = theme.colors
    val borderColor = if (theme.style == ThemeStyle.GUGUGA) c.outline else c.border
    val shape = RoundedCornerShape(theme.cardRadius)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    color = c.ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                if (!selectedSummary.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(theme.pillRadius),
                        color = c.primarySoft,
                        border = BorderStroke(1.dp, c.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Text(
                            text = selectedSummary,
                            color = c.primaryDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            GuBadge(
                text = if (query.isNotBlank()) "${items.size}/$totalCount" else "$totalCount",
                variant = if (query.isNotBlank() && items.isEmpty() && totalCount > 0) {
                    GuBadgeVariant.AMBER
                } else {
                    GuBadgeVariant.NEUTRAL
                },
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = c.surfaceSoft,
            border = BorderStroke(theme.borderWidth, borderColor),
            shadowElevation = if (theme.style == ThemeStyle.GUGUGA) 2.dp else 1.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .background(c.surface)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = c.muted,
                        modifier = Modifier.size(18.dp),
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = searchPlaceholder,
                                color = c.muted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = c.ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            cursorBrush = SolidColor(c.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.common.clear,
                                tint = c.muted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                HorizontalDivider(color = c.border)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(listHeight),
                ) {
                    if (items.isEmpty() && topExtraItem == null) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (totalCount == 0) emptyText else noMatchText,
                                color = c.muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (topExtraItem != null) {
                                item(key = "__top_extra__") {
                                    topExtraItem()
                                }
                            }
                            items(items, key = itemKey) { item ->
                                itemContent(item)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Consistent selectable row used inside [GuSearchableListBox] for groups and models.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuSelectableListRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    highlightFree: Boolean = false,
    badges: (@Composable FlowRowScope.() -> Unit)? = null,
) {
    val c = LocalAppTheme.current.colors
    val rowBg = when {
        selected -> c.primarySoft
        highlightFree -> c.accentSoft.copy(alpha = 0.45f)
        else -> c.surface
    }
    val rowBorder = when {
        selected -> c.primary
        highlightFree -> c.accent.copy(alpha = 0.55f)
        else -> c.border
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .border(1.dp, rowBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = c.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = c.muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (badges != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = badges,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
