package com.readygym.app

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser

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

    @Test fun warmLightThemeKeepsTextAndSelectionReadable() {
        lateinit var palette: ColorScheme
        compose.setContent { ReadyGymTheme { palette = MaterialTheme.colorScheme } }
        compose.waitForIdle()
        assertTrue("The app must use a sunny light background, not charcoal", palette.background.luminance() >= .85f)
        assertTrue("Cards must be white", palette.surface.luminance() >= .98f)
        assertTrue("The background must be warm, not cold gray", palette.background.red > palette.background.blue)
        assertTrue("The primary action must be orange", palette.primary.red > palette.primary.green && palette.primary.green > palette.primary.blue)
        assertTrue("The supporting accent must be green", palette.secondary.green > palette.secondary.red && palette.secondary.green > palette.secondary.blue)
        assertTrue("Selected cards must have a pale warm fill", palette.surfaceVariant.luminance() >= .75f && palette.surfaceVariant.red > palette.surfaceVariant.blue)
        val textPairs = linkedMapOf(
            "body/background" to (palette.onBackground to palette.background),
            "body/card" to (palette.onSurface to palette.surface),
            "body/selected card" to (palette.onSurface to palette.surfaceVariant),
            "secondary/background" to (palette.onSurfaceVariant to palette.background),
            "secondary/card" to (palette.onSurfaceVariant to palette.surface),
            "secondary/selected card" to (palette.onSurfaceVariant to palette.surfaceVariant),
            "primary button" to (palette.onPrimary to palette.primary),
            "selected check mark" to (palette.onSecondary to palette.secondary),
            "confirmed disabled button" to (palette.onSecondaryContainer to palette.secondaryContainer),
            "orange label/background" to (palette.primary to palette.background),
            "orange label/card" to (palette.primary to palette.surface),
            "orange label/selected card" to (palette.primary to palette.surfaceVariant),
            "green label/background" to (palette.secondary to palette.background),
            "green label/card" to (palette.secondary to palette.surface),
            "green label/selected card" to (palette.secondary to palette.surfaceVariant),
            "error/background" to (palette.error to palette.background)
        )
        textPairs.forEach { (role, pair) ->
            val ratio = contrast(pair.first, pair.second)
            assertTrue("$role text contrast must be at least 4.5:1, was $ratio", ratio >= 4.5f)
            println("CONTRAST text $role=$ratio")
        }
        for (background in listOf(palette.background, palette.surface, palette.surfaceVariant)) {
            for (outline in listOf(palette.outline, palette.primary, palette.secondary)) {
                val ratio = contrast(outline, background)
                assertTrue("Control/selected outline contrast must be at least 3:1, was $ratio", ratio >= 3f)
                println("CONTRAST control=$ratio")
            }
        }
    }

    @Test
    @Config(qualifiers = "night")
    fun systemNightModeDoesNotReintroduceCharcoalTheme() {
        lateinit var palette: ColorScheme
        compose.setContent { ReadyGymTheme { palette = MaterialTheme.colorScheme } }
        compose.waitForIdle()
        assertTrue("The accepted sunny theme must stay light even in system night mode", palette.background.luminance() >= .85f)
        assertTrue("Night mode must keep text readable", contrast(palette.onSurface, palette.surface) >= 4.5f)
    }

    @Test fun launcherAndEquipmentMatchSunnyLightSurfaces() {
        val background = vectorColors(R.drawable.ic_launcher_background).single()
        assertTrue("Launcher background must be warm white", background.luminance() >= .85f && background.red > background.blue)
        val iconColors = vectorColors(R.drawable.ic_launcher_foreground)
        assertTrue("Launcher must include orange", iconColors.any { it.red > it.green && it.green > it.blue })
        assertTrue("Launcher must include fresh green", iconColors.any { it.green > it.red && it.green > it.blue })
        for (drawable in listOf(R.drawable.equipment_bodyweight, R.drawable.equipment_dumbbell, R.drawable.equipment_barbell, R.drawable.equipment_machine)) {
            val colors = vectorColors(drawable)
            assertTrue("Each equipment illustration must contain recognizable geometry", colors.size >= 6)
            for (color in colors) {
                val ratio = contrast(color, ReadyGymColors.SelectedSurface)
                assertTrue("Equipment lines/fills must remain visible on light cards, was $ratio", ratio >= 3f)
            }
        }
    }

    private fun vectorColors(drawable: Int): List<Color> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val colors = mutableListOf<Color>()
        context.resources.getXml(drawable).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "path") {
                    for (attribute in listOf("fillColor", "strokeColor")) {
                        val value = parser.getAttributeValue("http://schemas.android.com/apk/res/android", attribute)
                        if (value != null && value.startsWith("#")) {
                            val color = Color(android.graphics.Color.parseColor(value))
                            if (color.alpha > 0f) colors.add(color)
                        }
                    }
                }
                parser.next()
            }
        }
        return colors
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }
}
