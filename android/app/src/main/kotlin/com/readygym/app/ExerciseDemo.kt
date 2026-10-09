@file:Suppress("DEPRECATION")

package com.readygym.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lazy, offline-only demonstration. Parent should render only its currently expanded exercise. */
@Composable
fun ExerciseDemo(stableId: String, modifier: Modifier = Modifier) {
    ExerciseDemo(stableId, modifier, loadGif = { context, asset -> decodeExerciseGif(context, asset) })
}

/** Internal suspend seam keeps the real player testable across delayed decode/lifecycle events. */
@Composable
internal fun ExerciseDemo(stableId: String, modifier: Modifier, loadGif: suspend (Context, String) -> DecodedExerciseGif) {
    val media = ExerciseMediaCatalog.find(stableId)
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(null, stableId, media?.localThumbnail) {
        val asset = media?.localThumbnail ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                require(asset.startsWith("exercise-demos/") && !asset.contains(".."))
                context.assets.open(asset).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var requested by remember(stableId) { mutableStateOf(false) }
    var decoded by remember(stableId) { mutableStateOf<DecodedExerciseGif?>(null) }
    var failed by remember(stableId) { mutableStateOf(false) }
    var playIntent by remember(stableId) { mutableStateOf(false) }
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED)
    }
    val playing = decoded != null && playIntent && resumed
    var player by remember(stableId) { mutableStateOf<ExerciseGifView?>(null) }
    LaunchedEffect(stableId, requested) {
        if (requested && media?.status == MediaStatus.AVAILABLE) {
            val result = withContext(Dispatchers.IO) {
                runCatching { loadGif(context, requireNotNull(media.localAsset)) }
            }
            result.onSuccess {
                decoded = it
                if (lifecycleOwner.lifecycle.currentState != Lifecycle.State.RESUMED) playIntent = false
            }.onFailure { failed = true; playIntent = false }
        }
    }
    DisposableEffect(lifecycleOwner, stableId) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED
            if (event == Lifecycle.Event.ON_STOP) playIntent = false
            if (!resumed) player?.setPlaying(false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player?.setPlaying(false)
        }
    }
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(media?.displayTitle ?: "未识别动作", style = MaterialTheme.typography.titleLarge)
            if (media == null) {
                Text("此动作没有已核验的演示资料，不用其他动作冒充", color = MaterialTheme.colorScheme.error)
            } else {
                Text("主要参与：${media.muscles.joinToString("、")}", style = MaterialTheme.typography.bodyMedium)
                if (decoded == null) thumbnail?.let { image ->
                    Image(image, contentDescription = "${media.displayTitle}动作静态预览图（不是GIF播放）",
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                }
                when {
                    media.status == MediaStatus.MISSING -> Text("暂无已核验GIF：${media.missingReason}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    failed -> Text("GIF加载失败，演示不可用；请阅读动作说明，不把失败当作播放成功", color = MaterialTheme.colorScheme.error)
                    decoded != null -> {
                        key(stableId) {
                            AndroidView(
                                factory = { ExerciseGifView(it).also { view -> player = view; view.setSource(decoded!!) } },
                                update = { it.setPlaying(playing && playIntent && lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) },
                                onRelease = { it.setPlaying(false) },
                                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                                    .semantics { contentDescription = "${media.displayTitle}动作识别GIF；${if (playing) "正在播放" else "已暂停"}" }
                            )
                        }
                        Text(if (playing) "正在播放" else "已暂停", style = MaterialTheme.typography.labelLarge)
                        Button(onClick = { playIntent = !playing && lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (playing) "暂停动作演示" else "继续播放")
                        }
                    }
                    requested -> { CircularProgressIndicator(); Text("正在加载离线GIF") }
                    else -> {
                        Text("点击播放离线GIF", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = {
                            playIntent = lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED
                            requested = true
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("播放动作演示") }
                    }
                }
                Text("动作要点", style = MaterialTheme.typography.titleMedium)
                media.cues.forEachIndexed { index, cue -> Text("${index + 1}. $cue", style = MaterialTheme.typography.bodyMedium) }
                Text("注意事项", style = MaterialTheme.typography.titleMedium)
                media.cautions.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                Text(media.reviewNotice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                media.source?.let { source ->
                    Text("${source.title} — ${source.author} · ${source.license}\n${source.changes}\n来源：${source.sourceUrl}\n许可：${source.licenseUrl}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal class DecodedExerciseGif(val drawable: AnimatedImageDrawable? = null, val movie: Movie? = null)

@Suppress("DEPRECATION")
internal fun decodeExerciseGif(context: Context, asset: String): DecodedExerciseGif {
    require(asset.startsWith("exercise-demos/") && !asset.contains("..")) { "Only bundled exercise assets allowed" }
    return if (Build.VERSION.SDK_INT >= 28) {
        val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets, asset))
        require(drawable is AnimatedImageDrawable) { "Demonstration must be animated" }
        drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        DecodedExerciseGif(drawable = drawable)
    } else {
        val movie = context.assets.open(asset).use { Movie.decodeStream(it) }
        require(movie != null && movie.duration() > 0) { "GIF decoding failed" }
        DecodedExerciseGif(movie = movie)
    }
}

/** Only the active demo schedules frames; API26/27 fallback advances Movie time rather than a static frame. */
@Suppress("DEPRECATION")
internal class ExerciseGifView(context: Context) : View(context) {
    private var source: DecodedExerciseGif? = null
    internal var isPlaying: Boolean = false
        private set
    private var elapsed = 0L
    private var startedAt = 0L
    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
    fun setSource(value: DecodedExerciseGif) {
        setPlaying(false)
        source?.drawable?.callback = null
        source = value
        value.drawable?.callback = this
        elapsed = 0
        invalidate()
    }
    fun setPlaying(value: Boolean) {
        if (isPlaying == value) return
        if (value) {
            startedAt = SystemClock.uptimeMillis()
            source?.drawable?.start()
        } else {
            elapsed += SystemClock.uptimeMillis() - startedAt
            source?.drawable?.stop()
        }
        isPlaying = value
        invalidate()
    }
    override fun verifyDrawable(who: android.graphics.drawable.Drawable): Boolean = who === source?.drawable || super.verifyDrawable(who)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gif = source ?: return
        val drawable = gif.drawable
        val movie = gif.movie
        val w = drawable?.intrinsicWidth ?: movie?.width() ?: return
        val h = drawable?.intrinsicHeight ?: movie?.height() ?: return
        if (w <= 0 || h <= 0) return
        val scale = minOf(width.toFloat() / w, height.toFloat() / h)
        canvas.save()
        canvas.translate((width - w * scale) / 2, (height - h * scale) / 2)
        canvas.scale(scale, scale)
        if (drawable != null) {
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
        } else if (movie != null) {
            val time = elapsed + if (isPlaying) SystemClock.uptimeMillis() - startedAt else 0
            movie.setTime((time % movie.duration().coerceAtLeast(1)).toInt())
            movie.draw(canvas, 0f, 0f)
            if (isPlaying) postInvalidateOnAnimation()
        }
        canvas.restore()
    }
    override fun onDetachedFromWindow() {
        setPlaying(false)
        source?.drawable?.callback = null
        super.onDetachedFromWindow()
    }
}
