package com.mc2islamictv.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableString
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay

@UnstableApi
internal object MC2ExportRenderer {
    private const val SIZE = 1080
    private val gold = Color.rgb(220, 170, 35)
    private val green = Color.rgb(0, 66, 38)
    private val cream = Color.rgb(255, 248, 229)
    private val black = Color.BLACK
    private val white = Color.WHITE

    fun buildEffects(project: Project): List<androidx.media3.common.Effect> {
        val layers = mutableListOf<androidx.media3.effect.TextureOverlay>()
        if (project.overlays.isNotEmpty()) layers += TimedTextOverlay(project.overlays)
        layers += BitmapOverlay.createStaticBitmapOverlay(createTemplateBitmap(project), StaticOverlaySettings.Builder().build())

        val effects = mutableListOf<androidx.media3.common.Effect>()
        // The export canvas is always square; this is also the preview canvas ratio.
        effects += Presentation.createForAspectRatio(1f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
        val totalCrop = (project.crop.coerceIn(0f, 0.45f) +
            ((1f - (1f / project.zoom.coerceIn(1f, 4f))) / 2f)).coerceIn(0f, 0.46f)
        val available = (1f - 2f * totalCrop).coerceAtLeast(0.08f)
        val px = (-project.panX.coerceIn(-1f, 1f) * totalCrop).coerceIn(-totalCrop, totalCrop)
        val py = (project.panY.coerceIn(-1f, 1f) * totalCrop).coerceIn(-totalCrop, totalCrop)
        val left = (-1f + 2f * totalCrop + 2f * px).coerceIn(-1f, 0.92f)
        val right = (1f - 2f * totalCrop + 2f * px).coerceIn(left + 0.08f, 1f)
        val bottom = (-1f + 2f * totalCrop + 2f * py).coerceIn(-1f, 0.92f)
        val top = (1f - 2f * totalCrop + 2f * py).coerceIn(bottom + 0.08f, 1f)
        if (totalCrop > 0f || project.panX != 0f || project.panY != 0f) effects += Crop(left, right, bottom, top)
        if (project.rotation != 0f) effects += ScaleAndRotateTransformation.Builder().setRotationDegrees(project.rotation).build()
        if (project.brightness != 0f) effects += Brightness(project.brightness.coerceIn(-1f, 1f))
        if (project.contrast != 0f) effects += Contrast(project.contrast.coerceIn(-1f, 1f))
        if (project.saturation != 0f) effects += HslAdjustment.Builder().adjustSaturation(project.saturation.coerceIn(-100f, 100f)).build()
        effects += OverlayEffect(layers)
        return effects
    }

    fun createTemplateBitmap(project: Project, width: Int = SIZE, height: Int = SIZE): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.scale(width / SIZE.toFloat(), height / SIZE.toFloat())
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 13f; color = gold }
        val fineGold = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = gold }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif", Typeface.BOLD) }

        // Rich green square frame and restrained Islamic geometric detailing.
        fill.color = green
        canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), fill)
        val pattern = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(18, 91, 54); style = Paint.Style.STROKE; strokeWidth = 2f }
        for (x in -100..1180 step 80) {
            canvas.drawLine(x.toFloat(), 0f, (x + 1080).toFloat(), 1080f, pattern)
            canvas.drawLine(x.toFloat(), 1080f, (x + 1080).toFloat(), 0f, pattern)
        }
        canvas.drawRoundRect(RectF(12f, 12f, 1068f, 1068f), 48f, 48f, line)
        canvas.drawRoundRect(RectF(30f, 30f, 1050f, 1050f), 36f, 36f, fineGold)

        // B: compact arched description panel on the left.
        val b = Path().apply {
            moveTo(48f, 785f); lineTo(48f, 245f); cubicTo(48f, 150f, 105f, 100f, 205f, 100f)
            cubicTo(305f, 100f, 365f, 155f, 365f, 245f); lineTo(365f, 785f); close()
        }
        fill.color = cream; canvas.drawPath(b, fill); canvas.drawPath(b, line)
        text.color = black; text.textSize = 42f
        drawWrapped(canvas, project.description.ifBlank { "DESCRIPTION" }, 206f, 350f, 275f, text, 54f, 8)
        val ornament = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; style = Paint.Style.STROKE; strokeWidth = 5f }
        canvas.drawLine(105f, 705f, 307f, 705f, ornament)
        canvas.drawCircle(206f, 705f, 22f, ornament)

        // A: the video is composited beneath this frame and clipped to this aperture.
        val aperture = Path().apply {
            moveTo(410f, 118f); cubicTo(590f, 62f, 840f, 58f, 1034f, 118f)
            lineTo(1034f, 778f); cubicTo(900f, 820f, 560f, 820f, 410f, 778f); close()
        }
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        canvas.drawPath(aperture, clear)
        clear.xfermode = null
        canvas.drawPath(aperture, line)

        // C: one nameplate. Editable teacher name above fixed channel identity below.
        fill.color = green
        val nameplate = RectF(90f, 830f, 990f, 1018f)
        canvas.drawRoundRect(nameplate, 74f, 74f, fill)
        canvas.drawRoundRect(nameplate, 74f, 74f, line)
        text.color = white; text.textSize = 58f
        drawFit(canvas, project.teacher.ifBlank { "TEACHER NAME" }, 540f, 910f, 790f, text)
        fill.color = cream
        val channelPill = RectF(285f, 936f, 795f, 1000f)
        canvas.drawRoundRect(channelPill, 32f, 32f, fill)
        text.color = black; text.textSize = 34f
        canvas.drawText("MC2 ISLAMIC TV", 540f, 980f, text)
        canvas.restore()
        return bitmap
    }

    private fun drawWrapped(canvas: Canvas, value: String, cx: Float, y: Float, maxWidth: Float, paint: Paint, lineHeight: Float, maxLines: Int) {
        val lines = mutableListOf<String>(); var line = ""
        value.split(Regex("\\s+")).forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth) line = candidate
            else { if (line.isNotEmpty()) lines += line; line = word }
        }
        if (line.isNotEmpty()) lines += line
        lines.take(maxLines).forEachIndexed { i, s -> canvas.drawText(s, cx, y + i * lineHeight, paint) }
    }

    private fun drawFit(canvas: Canvas, value: String, cx: Float, y: Float, maxWidth: Float, paint: Paint) {
        while (paint.measureText(value) > maxWidth && paint.textSize > 22f) paint.textSize -= 2f
        canvas.drawText(value, cx, y, paint)
    }
}

@UnstableApi
private class TimedTextOverlay(private val clips: List<TextClip>) : TextOverlay() {
    override fun getText(presentationTimeUs: Long): SpannableString {
        val ms = presentationTimeUs / 1000L
        return SpannableString(clips.filter { ms >= it.startMs && ms < it.endMs }.joinToString("\\n") { it.text })
    }
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        StaticOverlaySettings.Builder().setScale(0.55f, 0.20f).setBackgroundFrameAnchor(0f, 0f).build()
}
