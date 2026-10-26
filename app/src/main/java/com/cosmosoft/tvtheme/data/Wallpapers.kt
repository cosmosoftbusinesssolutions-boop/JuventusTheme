package com.cosmosoft.tvtheme.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.cosmosoft.tvtheme.ui.theme.BianconeriTheme
import com.cosmosoft.tvtheme.ui.theme.LauncherTheme
import com.cosmosoft.tvtheme.ui.theme.LauncherThemes
import java.io.File
import android.graphics.Color as AndroidColor

/** What the user picked as their background. */
sealed interface WallpaperChoice {
    /** A plain gradient from [LauncherThemes]. */
    data class Preset(val index: Int) : WallpaperChoice

    /** One of the pictures in [BuiltinWallpapers]. */
    data class Builtin(val id: String) : WallpaperChoice

    /** The user's own picture; [stamp] changes each time a new one is saved so it reloads. */
    data class Custom(val stamp: Long) : WallpaperChoice

    fun serialize(): String = when (this) {
        is Preset -> "preset:$index"
        is Builtin -> "builtin:$id"
        is Custom -> "custom:$stamp"
    }

    companion object {
        fun parse(value: String?): WallpaperChoice? {
            val (type, arg) = value?.split(':', limit = 2)?.takeIf { it.size == 2 } ?: return null
            return when (type) {
                "preset" -> arg.toIntOrNull()?.takeIf { it in LauncherThemes.indices }?.let(::Preset)
                "builtin" -> arg.takeIf { id -> BuiltinWallpapers.any { it.id == id } }?.let(::Builtin)
                "custom" -> arg.toLongOrNull()?.let(::Custom)
                else -> null
            }
        }
    }
}

/** A wallpaper drawn in code, so the APK ships no image files. */
class BuiltinWallpaper(
    val id: String,
    val name: String,
    /** Colors to use with this wallpaper; null means derive them from the picture. */
    val theme: LauncherTheme? = null,
    private val paint: (Canvas, Int, Int) -> Unit,
) {
    constructor(
        id: String,
        name: String,
        base: Int,
        /** Each glow is (x, y, radius) as fractions of the width, plus a color. */
        glows: List<Pair<Triple<Float, Float, Float>, Int>>,
    ) : this(id, name, null, { canvas, width, height -> drawGlows(canvas, width, height, base, glows) })

    fun render(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        paint(Canvas(bitmap), width, height)
        return bitmap
    }
}

private fun drawGlows(canvas: Canvas, width: Int, height: Int, base: Int, glows: List<Pair<Triple<Float, Float, Float>, Int>>) {
    canvas.drawColor(base)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    for ((position, color) in glows) {
        val (x, y, radius) = position
        paint.shader = RadialGradient(
            x * width, y * height, radius * width,
            color, color and 0x00FFFFFF, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}

/**
 * Black-and-white vertical stripes, fading to black on the left where the text and cards sit,
 * with a warm gold glow in the bottom corner.
 */
private fun drawStripes(canvas: Canvas, width: Int, height: Int) {
    val w = width.toFloat()
    val h = height.toFloat()
    canvas.drawColor(AndroidColor.BLACK)
    val stripe = w / 12
    val white = Paint().apply { color = 0xFFF4F4F4.toInt() }
    for (i in 1 until 12 step 2) canvas.drawRect(i * stripe, 0f, (i + 1) * stripe, h, white)

    val fade = Paint().apply {
        shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(0xF5000000.toInt(), 0xCC000000.toInt(), 0x40000000),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, w, h, fade)
    val gold = Paint().apply {
        shader = RadialGradient(w * 0.95f, h, w * 0.4f, 0x80D4AF37.toInt(), 0x00D4AF37, Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, w, h, gold)
}

/** Shown on first launch, and used whenever picture wallpapers aren't available. */
val DefaultWallpaper = WallpaperChoice.Builtin("bianconeri")

val BuiltinWallpapers = listOf(
    BuiltinWallpaper(id = "bianconeri", name = "Bianconeri", theme = BianconeriTheme, paint = ::drawStripes),
    BuiltinWallpaper(
        id = "nebula", name = "Nebula", base = 0xFF0A0820.toInt(),
        glows = listOf(
            Triple(0.8f, 0.2f, 0.6f) to 0xFFC2185B.toInt(),
            Triple(0.2f, 0.8f, 0.7f) to 0xFF3949AB.toInt(),
            Triple(0.6f, 0.7f, 0.35f) to 0xFF00ACC1.toInt(),
        ),
    ),
    BuiltinWallpaper(
        id = "lagoon", name = "Lagoon", base = 0xFF021B1F.toInt(),
        glows = listOf(
            Triple(0.3f, 0.3f, 0.6f) to 0xFF00897B.toInt(),
            Triple(0.85f, 0.75f, 0.5f) to 0xFF26C6DA.toInt(),
            Triple(0.1f, 0.9f, 0.5f) to 0xFF1A237E.toInt(),
        ),
    ),
    BuiltinWallpaper(
        id = "ember", name = "Ember", base = 0xFF1A0A05.toInt(),
        glows = listOf(
            Triple(0.75f, 0.35f, 0.55f) to 0xFFE65100.toInt(),
            Triple(0.9f, 0.9f, 0.4f) to 0xFFFFB300.toInt(),
            Triple(0.15f, 0.2f, 0.6f) to 0xFF6A1B9A.toInt(),
        ),
    ),
)

/** A wallpaper ready to draw: an optional picture plus the colors that go with it. */
class LoadedWallpaper(val image: ImageBitmap?, val theme: LauncherTheme)

object Wallpapers {
    private const val WIDTH = 1920
    private const val HEIGHT = 1080

    /** Must be called off the main thread. */
    fun load(context: Context, choice: WallpaperChoice, profileId: String): LoadedWallpaper {
        val fallback = LoadedWallpaper(null, BianconeriTheme)
        return when (choice) {
            is WallpaperChoice.Preset -> LoadedWallpaper(null, LauncherThemes[choice.index])
            is WallpaperChoice.Builtin -> {
                val wallpaper = BuiltinWallpapers.firstOrNull { it.id == choice.id } ?: return fallback
                val bitmap = wallpaper.render(WIDTH, HEIGHT)
                wallpaper.theme?.let { LoadedWallpaper(bitmap.asImageBitmap(), it) } ?: fromBitmap(bitmap, wallpaper.name)
            }
            is WallpaperChoice.Custom -> {
                val bitmap = BitmapFactory.decodeFile(customFile(context, profileId).path) ?: return fallback
                fromBitmap(bitmap, "Your picture")
            }
        }
    }

    /** Each profile has its own picture; the main profile keeps the original file name. */
    fun customFile(context: Context, profileId: String) =
        File(context.filesDir, if (profileId == Profile.MAIN_ID) "wallpaper.jpg" else "wallpaper_$profileId.jpg")

    /** Copies a picture into app storage, scaled down to TV size. Returns false if it can't be read. */
    fun saveCustom(context: Context, uri: Uri, profileId: String): Boolean {
        val bitmap = decodeSampled(context, uri, WIDTH, HEIGHT) ?: return false
        customFile(context, profileId).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return true
    }

    /** Newest pictures on the device. Needs the photo permission. */
    fun deviceImages(context: Context, limit: Int = 60): List<Uri> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        return context.contentResolver.query(collection, projection, null, null, sort)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            buildList {
                while (cursor.moveToNext() && size < limit) {
                    add(ContentUris.withAppendedId(collection, cursor.getLong(idColumn)))
                }
            }
        }.orEmpty()
    }

    /** Decodes [uri] at the smallest power-of-two scale that still covers [width] x [height]. */
    fun decodeSampled(context: Context, uri: Uri, width: Int, height: Int): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    fun decodeFileSampled(file: File, width: Int, height: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun fromBitmap(bitmap: Bitmap, name: String) =
        LoadedWallpaper(bitmap.asImageBitmap(), themeFrom(bitmap, name))

    /** Builds launcher colors from a picture: dark panels from its main color, accent from its liveliest one. */
    private fun themeFrom(bitmap: Bitmap, name: String): LauncherTheme {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val dominant = palette.getDominantColor(0xFF1B1F3B.toInt())
        val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.lightMutedSwatch
        var accent = swatch?.rgb ?: 0xFF00D1FF.toInt()
        // The accent marks the focused item, so it has to stand out against dark panels.
        while (ColorUtils.calculateLuminance(accent) < 0.35) {
            accent = ColorUtils.blendARGB(accent, AndroidColor.WHITE, 0.25f)
        }
        val deep = ColorUtils.blendARGB(dominant, AndroidColor.BLACK, 0.75f)
        return LauncherTheme(
            name = name,
            background = listOf(Color(deep), Color(ColorUtils.blendARGB(dominant, AndroidColor.BLACK, 0.55f)), Color(deep)),
            accent = Color(accent),
            card = Color(ColorUtils.blendARGB(dominant, AndroidColor.BLACK, 0.5f)),
            text = Color.White,
        )
    }
}
