package com.readygym.app

import org.junit.Assert.*
import org.junit.Test

class ExerciseMediaTest {
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
