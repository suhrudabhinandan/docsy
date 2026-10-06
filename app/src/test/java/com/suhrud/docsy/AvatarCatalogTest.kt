package com.suhrud.docsy

import com.suhrud.docsy.ui.components.AvatarCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarCatalogTest {

    @Test
    fun testCatalogSizeIs17() {
        assertEquals(17, AvatarCatalog.size)
        assertEquals(17, AvatarCatalog.items.size)
    }

    @Test
    fun testAllAvatarIdsAreUnique() {
        val ids = AvatarCatalog.items.map { it.id }
        assertEquals(17, ids.distinct().size)
    }

    @Test
    fun testAllAvatarIdsResolve() {
        for (item in AvatarCatalog.items) {
            val resolved = AvatarCatalog.getById(item.id)
            assertEquals(item.id, resolved.id)
            assertTrue(resolved.drawableRes != 0)
            assertTrue(resolved.contentDescriptionRes != 0)
        }
    }

    @Test
    fun testUnknownIdFallback() {
        val fallback = AvatarCatalog.getById("unknown_non_existent_avatar_id")
        assertEquals("avatar_01", fallback.id)

        val nullFallback = AvatarCatalog.getById(null)
        assertEquals("avatar_01", nullFallback.id)
    }

    @Test
    fun testOldIndexMigration() {
        assertEquals("avatar_01", AvatarCatalog.migrateIndexToId(0))
        assertEquals("avatar_02", AvatarCatalog.migrateIndexToId(1))
        assertEquals("avatar_17", AvatarCatalog.migrateIndexToId(16))
    }
}
