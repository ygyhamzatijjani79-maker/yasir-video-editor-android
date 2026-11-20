package com.mc2islamictv.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableString
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Crop
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay

/**
 * Generates an export-time overlay with a transparent video window.
 * This is a clean first native rendering interpretation of the supplied A/B/C
 * layout; the exact ornate reference artwork can replace this renderer later.
 */
@UnstableApi
internal object MC2ExportRenderer {
    private const val W = 1920
    private const val H = 1080

    fun buildEffects(project: Project): List<androidx.media3.common.Effect> {
        val layers = mutableListOf<androidx.media3.effect.TextureOverlay>()
        val template = createTemplateBitmap(project)
        layers += BitmapOverlay.createStaticBitmapOverlay(
            template,
            StaticOverlaySettings.Builder().build()
        )
        if (project.overlays.isNotEmpty()) {
            layers += TimedTextOverlay(project.overlays)
        }
        val effects = mutableListOf<androidx.media3.common.Effect>()
        if (project.brightness != 0f) effects += Brightness(project.brightness.coerceIn(-1f, 1f))
        val crop = project.crop.coerceIn(0f, 0.45f)
        if (crop > 0f) {
            val edge = (-1f + 2f * crop)
            effects += Crop(edge, -edge, edge, -edge)
        }
        effects += OverlayEffect(layers)
        return effects
    }

    private fun createTemplateBitmap(project: Project): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 13f
            color = Color.rgb(225, 174, 35)
        }

        // Deep-green outer frame, with the video window cleared below.
        fill.color = Color.rgb(3, 63, 30)
        canvas.drawRoundRect(RectF(8f, 8f, W - 8f, H - 8f), 42f, 42f, fill)

        // B: description panel.
        val panel = RectF(38f, 45f, 650f, 830f)
        fill.color = Color.rgb(255, 242, 211)
        canvas.drawRoundRect(panel, 58f, 58f, fill)
        canvas.drawRoundRect(panel, 58f, 58f, stroke)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(5, 76, 37)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD)
            textSize = 48f
        }
        drawWrappedText(canvas, project.description.ifBlank { " " }, 340f, 390f, 500f, titlePaint, 58f, 9)

        // A: clear transparent viewport. The source video remains visible here.
        val videoWindow = RectF(690f, 55f, 1878f, 820f)
        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        }
        canvas.drawRoundRect(videoWindow, 52f, 52f, clearPaint)
        clearPaint.xfermode = null
        canvas.drawRoundRect(videoWindow, 52f, 52f, stroke)

        // C: teacher-name banner.
        val banner = RectF(135f, 858f, 1785f, 1000f)
        fill.color = Color.rgb(2, 67, 31)
        canvas.drawRoundRect(banner, 72f, 72f, fill)
        canvas.drawRoundRect(banner, 72f, 72f, stroke)
        val teacherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD)
            textSize = 62f
        }
        drawFitText(canvas, project.teacher.ifBlank { " " }, 960f, 948f, 1500f, teacherPaint)

        // Fixed channel label: not editable in the project.
        val channel = RectF(565f, 1010f, 1355f, 1070f)
        fill.color = Color.rgb(255, 243, 216)
        canvas.drawRoundRect(channel, 30f, 30f, fill)
        val channelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(4, 73, 35)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD)
            textSize = 43f
        }
        canvas.drawText("▶  |  MC2 ISLAMIC TV", 960f, 1056f, channelPaint)
        return bitmap
    }

    private fun drawWrappedText(canvas: Canvas, text: String, centerX: Float, startY: Float, maxWidth: Float, paint: Paint, lineHeight: Float, maxLines: Int) {
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var line = ""
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth) line = candidate
            else {
                if (line.isNotEmpty()) lines += line
                line = word
            }
            if (lines.size >= maxLines) break
        }
        if (line.isNotEmpty() && lines.size < maxLines) lines += line
        lines.forEachIndexed { index, value -> canvas.drawText(value, centerX, startY + index * lineHeight, paint) }
    }

    private fun drawFitText(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        while (paint.measureText(text) > maxWidth && paint.textSize > 24f) paint.textSize -= 2f
        canvas.drawText(text, x, y, paint)
    }
}

@UnstableApi
private class TimedTextOverlay(private val clips: List<TextClip>) : TextOverlay() {
    override fun getText(presentationTimeUs: Long): SpannableString {
        val timeMs = presentationTimeUs / 1000L
        val visible = clips.filter { timeMs >= it.startMs && timeMs < it.endMs }
        return SpannableString(visible.joinToString("\n") { it.text })
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        return StaticOverlaySettings.Builder()
            .setScale(0.55f, 0.20f)
            .setBackgroundFrameAnchor(0.28f, -0.12f)
            .build()
    }
}
