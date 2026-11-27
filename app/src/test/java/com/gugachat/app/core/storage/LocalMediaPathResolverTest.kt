package com.gugachat.app.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalMediaPathResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val defaultFilesDir = "/data/user/0/com.gugachat.app/files"

    @Test
    fun toRelativePath_stripsCurrentAndLegacyFilesPrefixes() {
        assertEquals(
            "rp_media/avatar_1.png",
            LocalMediaPathResolver.toRelativePath(
                defaultFilesDir,
                "/data/user/0/com.gugachat.app/files/rp_media/avatar_1.png",
            ),
        )
        assertEquals(
            "chat_attachments/img_01.jpg",
            LocalMediaPathResolver.toRelativePath(
                "$defaultFilesDir/",
                "/data/user/0/com.gugachat.app/files/chat_attachments/img_01.jpg",
            ),
        )
        assertEquals(
            "direct_images/gen_1_0.png",
            LocalMediaPathResolver.toRelativePath(
                defaultFilesDir,
                "/data/user/10/com.gugachat.app/files/direct_images/gen_1_0.png",
            ),
        )
        assertEquals(
            "rp_media/sprite_happy.png",
            LocalMediaPathResolver.toRelativePath(
                defaultFilesDir,
                "/data/data/com.gugachat.app/files/rp_media/sprite_happy.png",
            ),
        )
    }

    @Test
    fun toRelativePath_preservesExternalUrisRelativePathsAndEmptyReturnsNull() {
        assertEquals(
            "https://example.com/avatar.png",
            LocalMediaPathResolver.toRelativePath(defaultFilesDir, "https://example.com/avatar.png"),
        )
        assertEquals(
            "http://127.0.0.1:8080/media/bg.png",
            LocalMediaPathResolver.toRelativePath(defaultFilesDir, "http://127.0.0.1:8080/media/bg.png"),
        )
        assertEquals(
            "content://media/external/images/media/42",
            LocalMediaPathResolver.toRelativePath(defaultFilesDir, "content://media/external/images/media/42"),
        )
        assertEquals(
            "data:image/png;base64,AAAA",
            LocalMediaPathResolver.toRelativePath(defaultFilesDir, "data:image/png;base64,AAAA"),
        )
        assertEquals(
            "rp_media/already_relative.png",
            LocalMediaPathResolver.toRelativePath(defaultFilesDir, "rp_media/already_relative.png"),
        )
        assertEquals(
            "/storage/emulated/0/Download/outside.png",
            LocalMediaPathResolver.toRelativePath(defaultFilesDir, "/storage/emulated/0/Download/outside.png"),
        )
        assertNull(LocalMediaPathResolver.toRelativePath(defaultFilesDir, null))
        assertNull(LocalMediaPathResolver.toRelativePath(defaultFilesDir, ""))
        assertNull(LocalMediaPathResolver.toRelativePath(defaultFilesDir, "   "))
        assertNull(LocalMediaPathResolver.toRelativePath(defaultFilesDir, defaultFilesDir))
        assertNull(LocalMediaPathResolver.toRelativePath(defaultFilesDir, "$defaultFilesDir/"))
        assertNull(LocalMediaPathResolver.toRelativePath(defaultFilesDir, "/data/data/com.gugachat.app/files/"))
    }

    @Test
    fun resolveAbsolutePathAndFile_handlesRelativeLegacyAndUriPaths() {
        val sandboxFilesDir = tempFolder.newFolder("files").absolutePath

        assertEquals(
            File(sandboxFilesDir, "rp_media/hero.png").path,
            LocalMediaPathResolver.resolveAbsolutePath(sandboxFilesDir, "rp_media/hero.png"),
        )
        assertEquals(
            File(sandboxFilesDir, "chat_attachments/doc.txt").path,
            LocalMediaPathResolver.resolveAbsolutePath(sandboxFilesDir, "./chat_attachments/doc.txt"),
        )
        // Legacy cross-uid path that does not exist on disk rebases cleanly under current filesDir
        assertEquals(
            File(sandboxFilesDir, "direct_images/work_0.png").path,
            LocalMediaPathResolver.resolveAbsolutePath(
                sandboxFilesDir,
                "/data/user/0/com.gugachat.app/files/direct_images/work_0.png",
            ),
        )
        assertEquals(
            File(sandboxFilesDir, "rp_media/legacy.png").path,
            LocalMediaPathResolver.resolveFile(
                sandboxFilesDir,
                "/data/data/com.gugachat.app/files/rp_media/legacy.png",
            )?.path,
        )

        // Existing external file keeps its real path
        val existingExternal = tempFolder.newFile("external_cover.jpg")
        assertEquals(
            existingExternal.absolutePath,
            LocalMediaPathResolver.resolveAbsolutePath(sandboxFilesDir, existingExternal.absolutePath),
        )
        assertEquals(
            existingExternal.absolutePath,
            LocalMediaPathResolver.resolveFile(sandboxFilesDir, existingExternal.absolutePath)?.absolutePath,
        )

        // URIs stay intact for resolveAbsolutePath and yield null for resolveFile
        assertEquals(
            "https://cdn.example.com/a.webp",
            LocalMediaPathResolver.resolveAbsolutePath(sandboxFilesDir, "https://cdn.example.com/a.webp"),
        )
        assertNull(LocalMediaPathResolver.resolveFile(sandboxFilesDir, "https://cdn.example.com/a.webp"))
        assertNull(LocalMediaPathResolver.resolveFile(sandboxFilesDir, "content://media/external/file/1"))
        assertNull(LocalMediaPathResolver.resolveFile(sandboxFilesDir, "data:image/png;base64,AAAA"))
        assertNull(LocalMediaPathResolver.resolveAbsolutePath(sandboxFilesDir, "   "))
        assertNull(LocalMediaPathResolver.resolveFile(sandboxFilesDir, null))
    }
}
