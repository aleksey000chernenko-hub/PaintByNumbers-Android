package com.trudny.photoretouch

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object AcrylicPainter {
    fun render(source: Bitmap, maxSide: Int = 1600, strength: Float = 0.72f): Bitmap {
        val src = scaleDown(source, maxSide)
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val posterized = IntArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = posterize(Color.red(c), 32)
            val g = posterize(Color.green(c), 32)
            val b = posterize(Color.blue(c), 32)
            posterized[i] = Color.rgb(r, g, b)
        }

        val softened = IntArray(pixels.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var rs = 0
                var gs = 0
                var bs = 0
                var count = 0
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy !in 0 until h) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx !in 0 until w) continue
                        val c = posterized[yy * w + xx]
                        rs += Color.red(c)
                        gs += Color.green(c)
                        bs += Color.blue(c)
                        count++
                    }
                }
                val base = posterized[y * w + x]
                val br = Color.red(base)
                val bg = Color.green(base)
                val bb = Color.blue(base)
                val ar = rs / count
                val ag = gs / count
                val ab = bs / count
                softened[y * w + x] = Color.rgb(
                    mix(br, ar, strength * 0.35f),
                    mix(bg, ag, strength * 0.35f),
                    mix(bb, ab, strength * 0.35f)
                )
            }
        }

        val out = IntArray(pixels.size)
        val rnd = Random(19)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val c = softened[i]
                val right = softened[y * w + min(w - 1, x + 2)]
                val down = softened[min(h - 1, y + 2) * w + x]
                val edge = (
                    abs(Color.red(c) - Color.red(right)) +
                    abs(Color.green(c) - Color.green(right)) +
                    abs(Color.blue(c) - Color.blue(right)) +
                    abs(Color.red(c) - Color.red(down)) +
                    abs(Color.green(c) - Color.green(down)) +
                    abs(Color.blue(c) - Color.blue(down))
                ) / 6f

                val grain = (rnd.nextInt(-10, 11) * strength).toInt()
                val edgeBoost = min(22f, edge * 0.35f) * strength

                var r = Color.red(c)
                var g = Color.green(c)
                var b = Color.blue(c)

                val avg = (r + g + b) / 3f
                r = clamp((avg + (r - avg) * (1.12f + 0.22f * strength) + grain + edgeBoost).toInt())
                g = clamp((avg + (g - avg) * (1.12f + 0.22f * strength) + grain + edgeBoost).toInt())
                b = clamp((avg + (b - avg) * (1.12f + 0.22f * strength) + grain + edgeBoost).toInt())

                if (((x + y) / 5) % 2 == 0) {
                    r = clamp(r + (4 * strength).toInt())
                    g = clamp(g + (3 * strength).toInt())
                }

                out[i] = Color.rgb(r, g, b)
            }
        }

        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val largest = max(src.width, src.height)
        if (largest <= maxSide) return src.copy(Bitmap.Config.ARGB_8888, false)
        val scale = maxSide.toFloat() / largest.toFloat()
        val nw = max(1, (src.width * scale).toInt())
        val nh = max(1, (src.height * scale).toInt())
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    private fun posterize(v: Int, step: Int): Int = clamp(((v + step / 2) / step) * step)
    private fun mix(a: Int, b: Int, t: Float): Int = clamp((a * (1f - t) + b * t).toInt())
    private fun clamp(v: Int): Int = min(255, max(0, v))
}
