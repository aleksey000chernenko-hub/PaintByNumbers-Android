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

    fun crop(
        source: Bitmap,
        canvas: CanvasSize,
        offsetX: Float,
        offsetY: Float,
        zoom: Float = 1f
    ): Bitmap {
        val targetAspect = canvas.aspect
        val sourceAspect = source.width.toFloat() / source.height.toFloat()
        val safeZoom = zoom.coerceIn(1f, 4f)

        var baseW = source.width
        var baseH = source.height

        if (sourceAspect > targetAspect) {
            baseW = (source.height * targetAspect).roundToInt().coerceAtMost(source.width)
        } else if (sourceAspect < targetAspect) {
            baseH = (source.width / targetAspect).roundToInt().coerceAtMost(source.height)
        }

        var cropW = (baseW / safeZoom).roundToInt().coerceAtLeast(1)
        var cropH = (baseH / safeZoom).roundToInt().coerceAtLeast(1)

        // Keep the exact canvas aspect after zoom.
        if (cropW.toFloat() / cropH > targetAspect) {
            cropW = (cropH * targetAspect).roundToInt().coerceAtLeast(1)
        } else {
            cropH = (cropW / targetAspect).roundToInt().coerceAtLeast(1)
        }

        cropW = cropW.coerceAtMost(source.width)
        cropH = cropH.coerceAtMost(source.height)

        val overflowX = (source.width - cropW).coerceAtLeast(0)
        val overflowY = (source.height - cropH).coerceAtLeast(0)
        val left = (overflowX * offsetX.coerceIn(0f, 1f)).roundToInt().coerceIn(0, overflowX)
        val top = (overflowY * offsetY.coerceIn(0f, 1f)).roundToInt().coerceIn(0, overflowY)

        return Bitmap.createBitmap(source, left, top, cropW, cropH)
    }
}
