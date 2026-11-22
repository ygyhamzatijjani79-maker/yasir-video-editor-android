package com.mc2islamictv.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.media3.effect.Brightness
import androidx.media3.effect.CanvasOverlay
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
    private val cream = Color.rgb(255, 248, 229)
    private val black = Color.BLACK
    private val white = Color.WHITE

    fun buildEffects(project: Project, context: Context): List<androidx.media3.common.Effect> {
        // The source is first converted to a square frame; the full-screen template is then
        // composited over it on every encoded frame, with the clear aperture exposing the video.
        val logo = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        val layers = mutableListOf<androidx.media3.effect.TextureOverlay>()
        layers += AnimatedTemplateOverlay(project, logo)
        if (project.overlays.isNotEmpty()) layers += TimedTextOverlay(project.overlays)

        val effects = mutableListOf<androidx.media3.common.Effect>()
        effects += Presentation.createForAspectRatio(1f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
        val totalCrop = (project.crop.coerceIn(0f, 0.4f) +
            ((1f - (1f / project.zoom.coerceIn(1f, 4f))) / 2f)).coerceIn(0f, 0.46f)
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

    fun createTemplateBitmap(project: Project, width: Int = SIZE, height: Int = SIZE, phase: Float = 0f, logoBitmap: Bitmap? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.scale(width / SIZE.toFloat(), height / SIZE.toFloat())
        drawTemplate(canvas, project, phase, logoBitmap)
        canvas.restore()
        return bitmap
    }

    internal fun drawTemplate(canvas: Canvas, project: Project, phase: Float, logoBitmap: Bitmap? = null) {
        val t = ((phase % 1f) + 1f) % 1f
        val green = cycleColor(t, intArrayOf(Color.rgb(0, 66, 38), Color.rgb(12, 39, 70), Color.rgb(61, 27, 64)))
        val gold = cycleColor(t, intArrayOf(Color.rgb(220, 170, 35), Color.rgb(88, 199, 222), Color.rgb(231, 162, 92)))
        val pulse = 0.85f + 0.15f * kotlin.math.sin(t * Math.PI * 8).toFloat()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 13f * pulse; color = gold }
        val fineGold = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = gold }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif", Typeface.BOLD) }

        fill.color = green
        canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), fill)
        val pattern = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blend(green, gold, 0.13f); style = Paint.Style.STROKE; strokeWidth = 2f }
        for (x in -100..1180 step 80) {
            canvas.drawLine(x.toFloat(), 0f, (x + 1080).toFloat(), 1080f, pattern)
            canvas.drawLine(x.toFloat(), 1080f, (x + 1080).toFloat(), 0f, pattern)
        }
        canvas.drawRoundRect(RectF(12f, 12f, 1068f, 1068f), 48f, 48f, line)
        canvas.drawRoundRect(RectF(30f, 30f, 1050f, 1050f), 36f, 36f, fineGold)

        // Glowing flower ornaments rotate in sync with the exported video timestamp.
        val flowerPoints = listOf(82f to 82f, 998f to 82f, 82f to 998f, 998f to 998f)
        flowerPoints.forEachIndexed { index, point ->
            val cx = point.first
            val cy = point.second
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; style = Paint.Style.FILL; alpha = 38 }
            val petalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = gold; style = Paint.Style.FILL
                alpha = (150 + 90 * (0.5f + 0.5f * kotlin.math.sin(t * Math.PI * 6).toFloat())).toInt().coerceIn(0, 240)
            }
            canvas.drawCircle(cx, cy, 43f, glow)
            canvas.save()
            canvas.rotate(t * 360f * if (index % 2 == 0) 1f else -1f, cx, cy)
            for (petal in 0 until 6) {
                canvas.save()
                canvas.rotate(petal * 60f, cx, cy)
                canvas.drawCircle(cx, cy - 22f, 13f, petalPaint)
                canvas.restore()
            }
            val centre = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cream; style = Paint.Style.FILL }
            canvas.drawCircle(cx, cy, 8f, centre)
            canvas.restore()
        }

        val b = Path().apply {
            moveTo(48f, 785f); lineTo(48f, 245f); cubicTo(48f, 150f, 105f, 100f, 205f, 100f)
            cubicTo(305f, 100f, 365f, 155f, 365f, 245f); lineTo(365f, 785f); close()
        }
        fill.color = cream; canvas.drawPath(b, fill); canvas.drawPath(b, line)
        text.color = black; text.textSize = 42f
        drawWrapped(canvas, project.description.ifBlank { "DESCRIPTION" }, 206f, 350f, 275f, text, 54f, 8)
        val ornament = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; style = Paint.Style.STROKE; strokeWidth = 5f }
        canvas.drawLine(105f, 705f, 307f, 705f, ornament)
        canvas.save()
        canvas.rotate(t * 360f, 206f, 705f)
        canvas.drawCircle(206f, 705f, 22f, ornament)
        canvas.restore()

        val aperture = Path().apply {
            moveTo(410f, 118f); cubicTo(590f, 62f, 840f, 58f, 1034f, 118f)
            lineTo(1034f, 778f); cubicTo(900f, 820f, 560f, 820f, 410f, 778f); close()
        }
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        canvas.drawPath(aperture, clear)
        clear.xfermode = null
        canvas.drawPath(aperture, line)

        fill.color = green
        val nameplate = RectF(90f, 830f, 990f, 1018f)
        canvas.drawRoundRect(nameplate, 74f, 74f, fill)
        canvas.drawRoundRect(nameplate, 74f, 74f, line)
        text.color = white; text.textSize = 58f
        drawFit(canvas, project.teacher.ifBlank { "TEACHER NAME" }, 540f, 910f, 790f, text)
        // Replace the old channel-name text plate with the user's supplied MC2 logo.
        fill.color = cream
        val logoPlate = RectF(500f, 928f, 580f, 1008f)
        canvas.drawRoundRect(logoPlate, 18f, 18f, fill)
        logoBitmap?.let { logo ->
            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(logo, null, RectF(503f, 931f, 577f, 1005f), logoPaint)
        }
    }

    private fun cycleColor(phase: Float, colors: IntArray): Int {
        val position = phase * colors.size
        val index = position.toInt() % colors.size
        return blend(colors[index], colors[(index + 1) % colors.size], position - position.toInt())
    }

    private fun blend(a: Int, b: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
        )
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
private class AnimatedTemplateOverlay(private val project: Project, private val logoBitmap: Bitmap) : CanvasOverlay(false) {
    init { setCanvasSize(1080, 1080) }
    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val phase = (presentationTimeUs / 1_000_000f / 9f) % 1f
        MC2ExportRenderer.drawTemplate(canvas, project, phase, logoBitmap)
    }
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        StaticOverlaySettings.Builder()
            .setScale(1f, 1f)
            .setBackgroundFrameAnchor(0f, 0f)
            .setOverlayFrameAnchor(0f, 0f)
            .build()
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
