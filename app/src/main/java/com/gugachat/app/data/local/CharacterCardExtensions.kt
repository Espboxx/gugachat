package com.gugachat.app.data.local

import com.gugachat.app.core.util.JsonUtils
import com.gugachat.app.domain.chat.RpCharacter
import com.gugachat.app.domain.chat.RpRegexScript
import com.gugachat.app.domain.chat.RpVisualTheme

fun CharacterCardEntity.decodedCast(): List<RpCharacter> =
    JsonUtils.decodeOrDefault(castJson, emptyList())

fun CharacterCardEntity.decodedAltGreetings(): List<String> =
    JsonUtils.decodeOrDefault(alternateGreetingsJson, emptyList())

fun CharacterCardEntity.decodedRegexScripts(): List<RpRegexScript> =
    JsonUtils.decodeOrDefault(regexScriptsJson, emptyList())

fun CharacterCardEntity.decodedVisualTheme(): RpVisualTheme =
    JsonUtils.decodeOrDefault(visualThemeJson, RpVisualTheme())

fun CharacterCardEntity.decodedExpressionSprites(): Map<String, String> =
    JsonUtils.decodeOrDefault(expressionSpritesJson, emptyMap())

fun CharacterCardEntity.decodedTags(): List<String> =
    JsonUtils.decodeOrDefault(tagsJson, emptyList())
