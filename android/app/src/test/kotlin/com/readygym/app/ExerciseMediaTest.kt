package com.readygym.app

import org.junit.Assert.*
import org.junit.Test

class ExerciseMediaTest {
    @Test fun allSixCurrentMovementsUseTheirMatchedAuthorizedRepositoryGifs() {
        val expected = mapOf(
            "rg-v1-bodyweight-squat" to "3119-75Bgtjy",
            "rg-v1-knee-push-up" to "3211-ZOuKWir",
            "rg-v1-glute-bridge" to "3013-u0cNiij",
            "rg-v1-dead-bug" to "0276-iny3m5y",
            "rg-v1-dumbbell-row" to "0293-BJ0Hz5L",
            "rg-v1-barbell-row" to "0027-eZyBC3j"
        )
        expected.forEach { (stableId, sourceFile) ->
            val media = requireNotNull(ExerciseMediaCatalog.find(stableId))
            assertEquals("$stableId must have its own demonstration", MediaStatus.AVAILABLE, media.status)
            assertEquals("exercise-demos/$sourceFile.gif", media.localAsset)
            assertEquals("exercise-demos/$sourceFile.jpg", media.localThumbnail)
            assertTrue(media.source!!.author.contains("Gym visual"))
            assertTrue(media.source.changes.contains("原始"))
        }
        val deadBug = requireNotNull(ExerciseMediaCatalog.find("rg-v1-dead-bug"))
        assertTrue(deadBug.displayTitle.contains("固定手臂"))
        assertTrue(deadBug.cues.any { it.contains("单腿") })
        assertFalse(deadBug.cues.any { it.contains("对侧手脚") })
    }
    @Test fun everyCurrentStableIdHasHonestMetadataWithoutBorrowingAnotherMovement() {
        val ids = setOf("rg-v1-bodyweight-squat", "rg-v1-knee-push-up", "rg-v1-glute-bridge",
            "rg-v1-dead-bug", "rg-v1-dumbbell-row", "rg-v1-barbell-row")
        assertEquals(ids, ExerciseMediaCatalog.all.map { it.stableId }.toSet())
        assertEquals(ids.size, ExerciseMediaCatalog.all.size)
        ExerciseMediaCatalog.all.forEach {
            assertTrue(it.displayTitle.isNotBlank())
            assertTrue(it.muscles.isNotEmpty())
            assertTrue(it.cues.isNotEmpty())
            assertTrue(it.cautions.isNotEmpty())
            assertTrue(it.reviewNotice.contains("未经专业审核"))
            when (it.status) {
                MediaStatus.AVAILABLE -> {
                    assertTrue(it.localAsset!!.startsWith("exercise-demos/"))
                    assertTrue(it.source!!.licenseUrl.startsWith("https://"))
                    assertTrue(it.source.author.isNotBlank())
                }
                MediaStatus.MISSING -> {
                    assertNull(it.localAsset)
                    assertNotNull(it.missingReason)
                }
            }
        }
        assertEquals(MediaStatus.AVAILABLE, ExerciseMediaCatalog.find("rg-v1-bodyweight-squat")!!.status)
        assertNull(ExerciseMediaCatalog.find("unknown-exercise"))
        assertEquals("双侧俯身哑铃划船", ExerciseMediaCatalog.find("rg-v1-dumbbell-row")!!.displayTitle)
    }
}
