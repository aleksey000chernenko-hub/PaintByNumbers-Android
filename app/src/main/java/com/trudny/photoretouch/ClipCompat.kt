package com.trudny.photoretouch

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope

inline fun DrawScope.clipRect(
    left: Float = 0f,
    top: Float = 0f,
    right: Float = size.width,
    bottom: Float = size.height,
    block: DrawScope.() -> Unit
) {
    val canvas = drawContext.canvas
    canvas.save()
    canvas.clipRect(Rect(left, top, right, bottom))
    try {
        block()
    } finally {
        canvas.restore()
    }
}
