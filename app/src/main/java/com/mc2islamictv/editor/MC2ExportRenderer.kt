package com.mc2islamictv.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableString
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.RgbMatrix
import androidx.media3.common.util.Size
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay

@UnstableApi
internal object MC2ExportRenderer {
    private const val SIZE = 1080
    private val cream = Color.rgb(255, 248, 229)
    private val black = Color.BLACK
    private val white = Color.WHITE

    // Preview viewport in the square composition, expressed as fractions of its size.
    private const val VIDEO_APERTURE_WIDTH = 0.575f
    private const val VIDEO_APERTURE_HEIGHT = 0.70f
    private const val VIDEO_APERTURE_LEFT = 0.38f
    private const val VIDEO_APERTURE_TOP = 0.055f
    // MatrixTransformation uses normalized device coordinates (-1..1).
    private const val VIDEO_APERTURE_CENTER_X_NDC =
        2f * (VIDEO_APERTURE_LEFT + VIDEO_APERTURE_WIDTH / 2f) - 1f
    private const val VIDEO_APERTURE_CENTER_Y_NDC =
        1f - 2f * (VIDEO_APERTURE_TOP + VIDEO_APERTURE_HEIGHT / 2f)

    /** Uses the same RGB matrix as the TextureView preview for color adjustments. */
    fun buildSourceVideoEffects(project: Project): List<androidx.media3.common.Effect> {
        return if (project.brightness == 0f && project.contrast == 0f && project.saturation == 0f) {
            emptyList()
        } else {
            listOf(PreviewColorMatrixEffect(project))
        }
    }

    /**
     * One-pass equivalent of applyPreviewTransform in TemplatePreview.kt.
     *
     * The preview computes a pixel-space scale from the rotated source bounds,
     * then applies user zoom/crop and pan inside the aperture. This transformation
     * performs that same calculation directly from the decoded input dimensions
     * and maps the source into the final square composition in one matrix.
     *
     * Combining rotation, fit-crop, user scale, pan and aperture placement avoids
     * intermediate Presentation frames whose aspect-ratio rounding can shift the
     * crop/zoom result relative to the live TextureView.
     */
    private class PreviewMatchedApertureTransformation(
        private val project: Project
    ) : MatrixTransformation {
        private var transformationMatrix = Matrix()

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            val sourceWidth = inputWidth.coerceAtLeast(1).toFloat()
            val sourceHeight = inputHeight.coerceAtLeast(1).toFloat()
            val angle = Math.toRadians(project.rotation.toDouble())
            val cosine = kotlin.math.cos(angle).toFloat()
            val sine = kotlin.math.sin(angle).toFloat()

            // Same rotated-bounds calculation used by applyPreviewTransform().
            val rotatedWidth =
                kotlin.math.abs(sourceWidth * cosine) + kotlin.math.abs(sourceHeight * sine)
            val rotatedHeight =
                kotlin.math.abs(sourceHeight * cosine) + kotlin.math.abs(sourceWidth * sine)
            val baseScale = max(
                VIDEO_APERTURE_WIDTH * SIZE / rotatedWidth,
                VIDEO_APERTURE_HEIGHT * SIZE / rotatedHeight
            )
            val userScale = (
                project.zoom.coerceIn(1f, 4f) /
                    (1f - project.crop.coerceIn(0f, 0.4f))
                ).coerceIn(1f, 7f)
            val scale = baseScale * userScale

            // Preview pan is 45% of the aperture in pixel space. Convert that
            // exact displacement to square-frame NDC, including screen Y inversion.
            val panX = project.panX.coerceIn(-1f, 1f) *
                (2f * 0.45f * VIDEO_APERTURE_WIDTH)
            val panY = -project.panY.coerceIn(-1f, 1f) *
                (2f * 0.45f * VIDEO_APERTURE_HEIGHT)

            val widthScale = scale * sourceWidth / SIZE
            val heightScale = scale * sourceHeight / SIZE

            // Input NDC has y-up coordinates; the preview TextureView uses y-down
            // pixel coordinates. This matrix converts between them while rotating
            // clockwise exactly as the preview's positive Android Matrix rotation.
            transformationMatrix = Matrix().apply {
                setValues(floatArrayOf(
                    widthScale * cosine,
                    heightScale * sine,
                    VIDEO_APERTURE_CENTER_X_NDC + panX,
                    -widthScale * sine,
                    heightScale * cosine,
                    VIDEO_APERTURE_CENTER_Y_NDC + panY,
                    0f, 0f, 1f
                ))
            }
            return Size(SIZE, SIZE)
        }

        override fun getMatrix(presentationTimeUs: Long): Matrix =
            Matrix(transformationMatrix)
    }

    /** Same saturation → contrast → brightness ColorMatrix composition as the preview. */
    private class PreviewColorMatrixEffect(private val project: Project) : RgbMatrix {
        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
            val saturation = (1f + project.saturation.coerceIn(-100f, 100f) / 100f).coerceIn(0f, 2f)
            val lr = 0.213f
            val lg = 0.715f
            val lb = 0.072f
            val inverse = 1f - saturation
            val s = floatArrayOf(
                lr * inverse + saturation, inverse * lg, inverse * lb, 0f,
                inverse * lr, lg * inverse + saturation, inverse * lb, 0f,
                inverse * lr, inverse * lg, lb * inverse + saturation, 0f,
                0f, 0f, 0f, 1f
            )
            val contrast = 1f + project.contrast.coerceIn(-1f, 1f)
            val offset = project.brightness.coerceIn(-1f, 1f) +
                (128f / 255f) * (1f - contrast)
            // Android ColorMatrix postConcat applies the contrast/brightness
            // adjustment after saturation. Convert its row-major matrix to GL
            // column-major order, using normalized RGB values.
            return floatArrayOf(
                contrast * s[0], contrast * s[4], contrast * s[8], 0f,
                contrast * s[1], contrast * s[5], contrast * s[9], 0f,
                contrast * s[2], contrast * s[6], contrast * s[10], 0f,
                offset, offset, offset, 1f
            )
        }
    }

    fun buildEffects(project: Project): List<androidx.media3.common.Effect> {
        val layers = mutableListOf<androidx.media3.effect.TextureOverlay>()
        layers += AnimatedTemplateOverlay(project)
        if (project.overlays.isNotEmpty()) layers += TimedTextOverlay(project.overlays)

        val effects = mutableListOf<androidx.media3.common.Effect>()
        // Apply the same source-dimension-aware transform as the live TextureView.
        // A single matrix performs rotation, fit-crop, zoom, pan and aperture
        // placement, then the square template overlay is composed on top.
        effects += buildSourceVideoEffects(project)
        effects += PreviewMatchedApertureTransformation(project)
        effects += OverlayEffect(layers)
        return effects
    }

    fun createTemplateBitmap(project: Project, width: Int = SIZE, height: Int = SIZE, phase: Float = 0f): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.scale(width / SIZE.toFloat(), height / SIZE.toFloat())
        drawTemplate(canvas, project, phase)
        canvas.restore()
        return bitmap
    }

    internal fun drawTemplate(canvas: Canvas, project: Project, phase: Float) {
        val t = ((phase % 1f) + 1f) % 1f
        val green = cycleColor(t, intArrayOf(Color.rgb(0, 66, 38), Color.rgb(12, 39, 70), Color.rgb(61, 27, 64)))
        val gold = cycleColor(t, intArrayOf(Color.rgb(220, 170, 35), Color.rgb(88, 199, 222), Color.rgb(231, 162, 92)))
        val pulse = 0.85f + 0.15f * kotlin.math.sin(t * Math.PI * 8).toFloat()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 13f * pulse; color = gold }
        val fineGold = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = gold }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif", Typeface.BOLD) }

        // Clip around the video aperture so its pixels remain transparent in the overlay texture.
        val aperture = Path().apply {
            moveTo(410f, 118f); cubicTo(590f, 62f, 840f, 58f, 1034f, 118f)
            lineTo(1034f, 778f); cubicTo(900f, 820f, 560f, 820f, 410f, 778f); close()
        }
        canvas.save()
        canvas.clipOutPath(aperture)

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

        canvas.restore()
        canvas.drawPath(aperture, line)

        fill.color = green
        val nameplate = RectF(90f, 830f, 990f, 1018f)
        canvas.drawRoundRect(nameplate, 74f, 74f, fill)
        canvas.drawRoundRect(nameplate, 74f, 74f, line)
        text.color = white; text.textSize = 58f
        drawFit(canvas, project.teacher.ifBlank { "TEACHER NAME" }, 540f, 910f, 790f, text)
        drawSocialFooter(canvas)
    }


    private fun drawSocialFooter(canvas: Canvas) {
        val xs = floatArrayOf(300f, 380f, 460f, 540f, 620f, 700f, 780f)
        val colors = intArrayOf(Color.BLACK, Color.rgb(37,211,102), Color.rgb(225,48,108), Color.rgb(24,119,242), Color.RED, Color.BLACK, Color.rgb(34,158,217))
        xs.forEachIndexed { i, x ->
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[i] }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4.5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawCircle(x, 958f, 24f, bg)
            when (i) {
                0 -> { stroke.strokeWidth = 5f; canvas.drawLine(x+4f,943f,x+4f,962f,stroke); canvas.drawLine(x+4f,943f,x+14f,946f,stroke); canvas.drawCircle(x-3f,965f,6f,solid) }
                1 -> { val bubble=Path().apply { moveTo(x-13f,945f); lineTo(x+11f,945f); lineTo(x+14f,960f); lineTo(x+8f,970f); lineTo(x-8f,970f); lineTo(x-16f,976f); close() }; canvas.drawPath(bubble,stroke); val phone=Path().apply { moveTo(x-7f,951f); cubicTo(x-4f,962f,x+2f,966f,x+8f,962f) }; canvas.drawPath(phone,stroke) }
                2 -> { canvas.drawRoundRect(RectF(x-14f,944f,x+14f,972f),7f,7f,stroke); canvas.drawCircle(x,958f,7f,stroke); canvas.drawCircle(x+8f,950f,2f,solid) }
                3 -> { val t=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textAlign=Paint.Align.CENTER; textSize=37f; typeface=Typeface.create("sans-serif",Typeface.BOLD) }; canvas.drawText("f",x+1f,971f,t) }
                4 -> { canvas.drawRoundRect(RectF(x-15f,948f,x+15f,968f),6f,6f,solid); val tri=Path().apply { moveTo(x-3f,952f); lineTo(x+8f,958f); lineTo(x-3f,964f); close() }; val red=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=colors[i] }; canvas.drawPath(tri,red) }
                5 -> { stroke.strokeWidth=4f; canvas.drawLine(x-11f,946f,x+11f,970f,stroke); canvas.drawLine(x+10f,946f,x-10f,970f,stroke) }
                6 -> { val plane=Path().apply { moveTo(x-15f,957f); lineTo(x+15f,946f); lineTo(x+7f,972f); lineTo(x,963f); lineTo(x-7f,968f); lineTo(x-5f,960f); close() }; canvas.drawPath(plane,solid) }
            }
        }
        val handle=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textAlign=Paint.Align.CENTER; textSize=29f; typeface=Typeface.create("sans-serif",Typeface.BOLD) }
        canvas.drawText("@MC2 ISLAMIC TV",540f,1008f,handle)
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
private class AnimatedTemplateOverlay(private val project: Project) : CanvasOverlay(true) {
    // OverlayEffect receives the final square frame after both Presentation effects.
    // Matching that actual frame size guarantees a full-frame template texture.
    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val phase = (presentationTimeUs / 1_000_000f / 9f) % 1f
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        canvas.save()
        canvas.scale(canvas.width / 1080f, canvas.height / 1080f)
        MC2ExportRenderer.drawTemplate(canvas, project, phase)
        canvas.restore()
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
