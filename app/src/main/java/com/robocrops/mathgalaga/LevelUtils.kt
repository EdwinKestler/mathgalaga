package com.robocrops.mathgalaga

import kotlin.math.abs
import kotlin.random.Random

fun getTopFormationPositions(level: Int): List<Pair<Float, Float>> {
    return when (level) {
        1 -> List(5) { Pair(100f + it * 120f, 50f) }
        2 -> List(5) { Pair(100f + it * 120f, 50f + if (it % 2 == 0) 50f else 0f) }
        3 -> {
            val centerX = Config.ScreenSettings.WIDTH / 2f
            List(5) { Pair(centerX - 200f + it * 100f, 50f + abs(2 - it) * 30f) }
        }
        4 -> List(5) { Pair(100f + it * 120f, 50f + abs(it - 2) * 30f) }
        5 -> List(5) {
            Pair(
                Random.nextFloat() * (Config.ScreenSettings.WIDTH - 100f) + 50f,
                Random.nextFloat() * 100f + 50f
            )
        }
        else -> List(5) { Pair(100f + it * 120f, 50f) }
    }
}

fun getLowerFormationPositions(level: Int): List<Pair<Float, Float>> {
    val positions = mutableListOf<Pair<Float, Float>>()
    when (level) {
        1 -> for (i in 0..<5) for (j in 0..<2) positions.add(Pair(100f + i * 100f, 150f + j * 50f))
        2 -> for (j in 0..<3) for (i in 0..<4) positions.add(
            Pair(
                50f + (j % 2) * 50f + i * 100f,
                150f + j * 40f
            )
        )
        3 -> for (i in 0..<6) for (j in 0..<2) positions.add(
            Pair(
                50f + i * 80f,
                150f + j * 50f + (if (i % 2 == 0) 0f else 20f)
            )
        )
        4 -> for (j in 0..<4) for (i in 0..<(5 - j)) positions.add(
            Pair(
                100f + (j * 50f) + i * 100f,
                150f + j * 40f
            )
        )
        5 -> for (i in 0..<8) for (j in 0..<3) positions.add(
            Pair(
                50f + i * 60f,
                100f + j * 30f
            )
        )
        else -> for (i in 0..<5) for (j in 0..<2) positions.add(Pair(100f + i * 100f, 150f + j * 50f))
    }
    return positions
}
