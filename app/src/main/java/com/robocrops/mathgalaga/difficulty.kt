package com.robocrops.mathgalaga

import kotlin.random.Random
import java.util.ArrayDeque

/**
 * Adaptive difficulty tracker for MathGalaga.
 * Keeps a fixed window of recent results and adjusts level accordingly.
 */
class DifficultyManager {
    private val window: ArrayDeque<Boolean> = ArrayDeque()
    var level: Int = 1
        private set

    /**
     * Record whether the last attempt was correct.
     * Updates the difficulty level up/down if the accuracy threshold is reached.
     */
    fun record(correct: Boolean) {
        if (window.size == Config.DifficultySettings.WINDOW) {
            window.removeFirst()
        }
        window.addLast(correct)

        // Only adjust when window is full
        if (window.size == Config.DifficultySettings.WINDOW) {
            val acc = window.count { it }.toFloat() / window.size
            val levelsCount = Config.DifficultySettings.LEVELS.size
            if (acc > 0.8f && level < levelsCount) {
                level++
            } else if (acc < 0.5f && level > 1) {
                level--
            }
            // Clamp for safety
            level = level.coerceIn(1, levelsCount)
        }
    }

    /**
     * Get current bounds for random math problems.
     */
    fun bounds(): Pair<Int, Int> =
        Config.DifficultySettings.LEVELS[level]
            ?: Config.DifficultySettings.LEVELS[1]
            ?: (1 to 5)
}

/**
 * Generates a multiplication problem within the bounds of the given DifficultyManager.
 * Returns a map with keys "question" (String) and "answer" (Int).
 */
fun generateAdaptiveProblem(dm: DifficultyManager): Map<String, Any> {
    val (lo, hi) = dm.bounds()
    val a = Random.nextInt(lo, hi + 1)
    val b = Random.nextInt(lo, hi + 1)
    return mapOf("question" to "$a×$b", "answer" to a * b)
}
