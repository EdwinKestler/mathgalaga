package com.robocrops.mathgalaga

import android.graphics.Rect

/**
 * Utility functions for MathGalaga.
 */
object Utils {
    /**
     * Checks if two rectangles intersect.
     * Safe (does NOT modify arguments).
     */
    fun collides(r1: Rect, r2: Rect): Boolean = Rect.intersects(r1, r2)
}
