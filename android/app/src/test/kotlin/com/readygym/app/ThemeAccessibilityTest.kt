package com.readygym.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inputAndUnselectedCardOutlinesRemainVisible() {
        lateinit var palette: ColorScheme
        compose.setContent { ReadyGymTheme { palette = MaterialTheme.colorScheme } }
        compose.waitForIdle()
        for (background in listOf(palette.surface, palette.background)) {
            val ratio = contrast(palette.outline, background)
            assertTrue("Control outline contrast must be at least 3:1, was $ratio", ratio >= 3f)
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }
}
