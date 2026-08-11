package com.trudny.photoretouch

import android.graphics.Bitmap
import kotlin.math.roundToInt

data class CanvasSize(val label: String, val widthCm: Int, val heightCm: Int) {
    val aspect: Float get() = widthCm.toFloat() / heightCm.toFloat()
}

object CanvasCropper {
    val sizes = listOf(
        CanvasSize("60×40 см", 60, 40),
        CanvasSize("60×39 см", 60, 39),
        CanvasSize("50×70 см", 50, 70),
        CanvasSize("40×40 см", 40, 40)
    )

    fun crop(source: Bitmap, canvas: CanvasSize, offsetX: Float, offsetY: Float): Bitmap {
        val targetAspect = canvas.aspect
        val sourceAspect = source.width.toFloat() / source.height.toFloat()

        var cropW = source.width
        var cropH = source.height
        var left = 0
        var top = 0

        if (sourceAspect > targetAspect) {
            cropW = (source.height * targetAspect).roundToInt().coerceAtMost(source.width)
            val overflow = source.width - cropW
            left = (overflow * offsetX.coerceIn(0f, 1f)).roundToInt()
        } else if (sourceAspect < targetAspect) {
            cropH = (source.width / targetAspect).roundToInt().coerceAtMost(source.height)
            val overflow = source.height - cropH
            top = (overflow * offsetY.coerceIn(0f, 1f)).roundToInt()
        }

        return Bitmap.createBitmap(source, left, top, cropW, cropH)
    }
}
