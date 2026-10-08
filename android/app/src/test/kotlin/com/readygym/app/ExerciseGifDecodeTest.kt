@file:Suppress("DEPRECATION")

package com.readygym.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** Decoder verification uses real bundled bytes and native host graphics, not mock drawables. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExerciseGifDecodeTest {
    private val asset = "exercise-demos/squats-wensceslao.gif"
    @Test @Config(sdk = [35]) fun api35DecodesAsAnimatedDrawableAndNativeStartStopWorks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gif = decodeExerciseGif(context, asset)
        assertNotNull(gif.drawable)
        assertNull(gif.movie)
        assertEquals(640, gif.drawable!!.intrinsicWidth)
        assertEquals(480, gif.drawable.intrinsicHeight)
        val view = ExerciseGifView(context)
        view.setSource(gif)
        assertFalse(view.isPlaying)
        assertFalse(gif.drawable.isRunning)
        view.setPlaying(true)
        assertTrue(view.isPlaying)
        assertTrue(gif.drawable.isRunning)
        view.setPlaying(false)
        assertFalse(view.isPlaying)
        assertFalse(gif.drawable.isRunning)
    }
    @org.junit.Ignore("Robolectric4.14.1 has no Movie native shadow; decodeStream returns null. Requires API26/27 device verification.")
    @Test @Config(sdk = [35]) fun legacyMovieDrawsDifferentFramesThenPauseFreezesFrame() {
        // Exercise the actual Movie renderer using cached API35 native graphics; not an API27 device claim.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val movie = context.assets.open(asset).use { android.graphics.Movie.decodeStream(it) }
        val gif = DecodedExerciseGif(movie = movie)
        assertNotNull(gif.movie)
        assertNull(gif.drawable)
        assertEquals(2640, gif.movie!!.duration())
        val view = ExerciseGifView(context)
        view.layout(0, 0, 640, 480)
        view.setSource(gif)
        fun frame(): IntArray {
            val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val pixels = IntArray(640 * 480)
            bitmap.getPixels(pixels, 0, 640, 0, 0, 640, 480)
            bitmap.recycle()
            return pixels
        }
        view.setPlaying(true)
        val first = frame()
        ShadowSystemClock.advanceBy(Duration.ofMillis(440))
        val later = frame()
        assertFalse("time-driven fallback must not show the same static frame", first.contentEquals(later))
        view.setPlaying(false)
        val paused = frame()
        ShadowSystemClock.advanceBy(Duration.ofMillis(440))
        assertArrayEquals(paused, frame())
    }
    @Test @Config(sdk = [35]) fun missingFileFailsDecodeInsteadOfPretendingToLoad() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(runCatching { decodeExerciseGif(context, "exercise-demos/not-present.gif") }.isFailure)
        assertTrue(runCatching { decodeExerciseGif(context, "../other.gif") }.isFailure)
    }
}
