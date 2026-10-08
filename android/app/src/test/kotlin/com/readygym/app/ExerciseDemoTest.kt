package com.readygym.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExerciseDemoTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun stopDuringDecodeRequiresAnotherTapAfterReturningToForeground() {
        val started = CountDownLatch(1)
        val decoded = CountDownLatch(1)
        val release = CompletableDeferred<Unit>()
        val source = AtomicReference<DecodedExerciseGif>()
        compose.setContent { ReadyGymTheme {
            ExerciseDemo("rg-v1-bodyweight-squat", Modifier.verticalScroll(rememberScrollState()),
                loadGif = { context, asset ->
                    started.countDown()
                    release.await()
                    decodeExerciseGif(context, asset).also { source.set(it); decoded.countDown() }
                })
        } }
        compose.onNodeWithText("播放动作演示").performScrollTo().performClick()
        compose.waitUntil(10000) { started.count == 0L }
        assertEquals("decode must be pending before STOP", 0L, started.count)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        release.complete(Unit)
        assertTrue("real decode must complete after STOP", decoded.await(10, TimeUnit.SECONDS))
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("继续播放").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("暂停动作演示").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("已暂停").assertExists()
        compose.runOnIdle {
            assertFalse("decoded animation must stay stopped on return", source.get().drawable!!.isRunning)
            assertFalse(findPlayer(compose.activity.window.decorView)!!.isPlaying)
        }
        compose.onNodeWithText("继续播放").performScrollTo().performClick()
        compose.onNodeWithText("正在播放").assertExists()
        compose.runOnIdle {
            assertTrue(source.get().drawable!!.isRunning)
            assertTrue(findPlayer(compose.activity.window.decorView)!!.isPlaying)
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(source.get().drawable!!.isRunning)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("已暂停").assertExists()
    }

    private fun findPlayer(view: android.view.View): ExerciseGifView? {
        if (view is ExerciseGifView) return view
        if (view is android.view.ViewGroup) for (index in 0 until view.childCount) {
            findPlayer(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    @Test fun missingMovementHasReadableCuesAndNeverOffersPlay() {
        compose.setContent { ReadyGymTheme {
            ExerciseDemo("rg-v1-dumbbell-row", Modifier.verticalScroll(rememberScrollState()))
        } }
        compose.onNodeWithText("双侧俯身哑铃划船").assertExists()
        compose.onNodeWithText("暂无已核验GIF", substring = true).assertExists()
        compose.onNodeWithText("播放动作演示").assertDoesNotExist()
        compose.onNodeWithText("双侧俯身，不借助椅子或训练凳", substring = true).performScrollTo().assertIsDisplayed()
    }
    @Test fun unknownMovementDoesNotBorrowSquatDemo() {
        compose.setContent { ReadyGymTheme { ExerciseDemo("unrecognized-id") } }
        compose.onNodeWithText("未识别动作").assertExists()
        compose.onNodeWithText("播放动作演示").assertDoesNotExist()
    }
    @Test fun squatRequiresTapToLoadAndOffersPauseWithoutAutoplay() {
        compose.setContent { ReadyGymTheme {
            ExerciseDemo("rg-v1-bodyweight-squat", Modifier.verticalScroll(rememberScrollState()))
        } }
        compose.onNodeWithText("点击播放离线GIF").assertExists()
        compose.onNodeWithText("正在播放").assertDoesNotExist()
        compose.onNodeWithText("播放动作演示").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("正在播放").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("暂停动作演示").performScrollTo().performClick()
        compose.onNodeWithText("已暂停").assertExists()
        compose.onNodeWithText("徒手深蹲").performScrollTo()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("../build/verification/media/player-paused-offscreen.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        compose.onNodeWithText("继续播放").performScrollTo().performClick()
        compose.onNodeWithText("正在播放").assertExists()
    }
}
