package com.readygym.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmallViewportUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun largeTextKeepsLastEquipmentAndPrimaryActionReachable() {
        compose.runOnUiThread {
            compose.activity.setContent {
                val current = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(current.density, 1.6f)) {
                    ReadyGymTheme { OnboardingScreen() }
                }
            }
        }
        completeConditions()
        compose.onNodeWithText("固定器械").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("下一步").assertIsDisplayed()
        capture("05-small-large-text")
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithContentDescription("器械：固定器械").performScrollTo().assertExists()
    }

    @Test
    @Config(qualifiers = "w640dp-h320dp-land")
    fun landscapeKeepsFieldsAndEquipmentScrollableAboveActionBar() {
        completeConditions()
        compose.onNodeWithText("固定器械").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("下一步").assertIsDisplayed()
        capture("06-landscape")
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithContentDescription("器械：固定器械").performScrollTo().assertExists()
    }

    private fun completeConditions() {
        compose.onNodeWithContentDescription("年龄（岁）").performScrollTo().performTextInput("30")
        compose.onNodeWithContentDescription("身高（cm）").performScrollTo().performTextInput("170")
        compose.onNodeWithContentDescription("体重（kg）").performScrollTo().performTextInput("65")
        compose.onNodeWithContentDescription("体重（kg）").performImeAction()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("增肌").performScrollTo().performClick()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("家").performScrollTo().performClick()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val target = File("../build/verification/screenshots/$name.png")
        target.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
