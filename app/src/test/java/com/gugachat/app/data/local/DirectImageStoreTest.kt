package com.gugachat.app.data.local

import com.gugachat.app.data.api.OnlineImageGenerationDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectImageStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun list_filtersGenerateEditedProcessingExpiringAndSearchCorrectly() = runBlocking {
        val store = DirectImageStore(tempFolder.newFolder("direct_images"))
        val genLegacyBlank = OnlineImageGenerationDto(
            id = "gen-blank",
            api_key_id = -1L,
            provider = "openai",
            model = "gpt-image-1",
            prompt = "A sunlit lantern alley",
            action = null,
            created_at = 100L,
        )
        val genExplicit = OnlineImageGenerationDto(
            id = "gen-explicit",
            api_key_id = -1L,
            provider = "openai",
            model = "gpt-image-1",
            prompt = "Moonlit courtyard palace",
            action = "generate",
            created_at = 200L,
        )
        val genEdited = OnlineImageGenerationDto(
            id = "gen-inpaint",
            api_key_id = -1L,
            provider = "openai",
            model = "flux-pro",
            prompt = "Add crimson silk scarf",
            action = "inpaint",
            created_at = 300L,
        )

        store.saveGeneration(genLegacyBlank, listOf(byteArrayOf(1, 2, 3) to "image/png"))
        store.saveGeneration(genExplicit, listOf(byteArrayOf(4, 5, 6) to "image/png"))
        store.saveGeneration(genEdited, listOf(byteArrayOf(7, 8, 9) to "image/png"))

        val all = store.list(filter = "all")
        assertEquals(3, all.items.size)
        assertEquals(3, all.total)

        val generated = store.list(filter = "generate")
        assertEquals(setOf("gen-blank", "gen-explicit"), generated.items.map { it.id }.toSet())

        val edited = store.list(filter = "edited")
        assertEquals(listOf("gen-inpaint"), edited.items.map { it.id })

        val processing = store.list(filter = "processing")
        assertTrue(processing.items.isEmpty())

        val expiring = store.list(filter = "expiring")
        assertTrue(expiring.items.isEmpty())

        val searchGen = store.list(search = "palace", filter = "generate")
        assertEquals(listOf("gen-explicit"), searchGen.items.map { it.id })
    }
}
