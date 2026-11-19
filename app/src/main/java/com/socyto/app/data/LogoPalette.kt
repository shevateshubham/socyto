package com.socyto.app.data

import android.graphics.Bitmap
import android.graphics.Color
import java.util.Locale
import kotlin.math.abs

/**
 * Suggests visible colours from a small, already-decoded business logo.
 * These are suggestions only: a Super Admin approves the final palette.
 */
object LogoPalette {
    fun suggestions(bitmap: Bitmap): List<String> {
        val counts = mutableMapOf<Int, Int>()
        val stepX = (bitmap.width / 64).coerceAtLeast(1)
        val stepY = (bitmap.height / 64).coerceAtLeast(1)
        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) < 200) continue
                val hsv = FloatArray(3)
                Color.colorToHSV(pixel, hsv)
                if (hsv[1] < 0.16f || hsv[2] < 0.14f || hsv[2] > 0.94f) continue
                // Quantize to avoid treating near-identical antialiased pixels as different colours.
                val r = (Color.red(pixel) / 32) * 32 + 16
                val g = (Color.green(pixel) / 32) * 32 + 16
                val b = (Color.blue(pixel) / 32) * 32 + 16
                val quantized = Color.rgb(r.coerceAtMost(255), g.coerceAtMost(255), b.coerceAtMost(255))
                counts[quantized] = (counts[quantized] ?: 0) + 1
            }
        }
        val choices = mutableListOf<Int>()
        for (entry in counts.entries.sortedByDescending { it.value }) {
            val next = entry.key
            val different = choices.all { previous ->
                abs(Color.red(previous) - Color.red(next)) +
                    abs(Color.green(previous) - Color.green(next)) +
                    abs(Color.blue(previous) - Color.blue(next)) > 110
            }
            if (different) choices.add(next)
            if (choices.size == 3) break
        }
        return choices.map { String.format(Locale.ROOT, "#%06X", it and 0xFFFFFF) }
    }
}
