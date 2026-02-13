package com.robocrops.mathgalaga

import android.content.Context
import android.graphics.*
import android.graphics.Color.argb
import kotlin.random.Random
import androidx.core.graphics.createBitmap
import com.robocrops.mathgalaga.R  // Import for direct R.drawable access

object Config {

    enum class GradeBand { G1_2, G3_4, G5_PLUS }

    enum class MultiplayerMode { SINGLE_PLAYER, COOP_SHARED, COOP_INDEPENDENT }

    data class GradeBandSettings(
        val levelBounds: Map<Int, Pair<Int, Int>>,
        val alienBaseSpeed: Int,
        val alienShootChance: Double,
        val alienShootIntervalMs: Long,
        val maxBullets: Int,
        val topTargetCount: Int,
        val lowerEnemyMin: Int,
        val lowerEnemyMax: Int,
        val scoreCorrect: Int,
        val speedBonusMax: Int,
        val wrongTopPenalty: Int,
        val hintWrongAttempts: Int,
        val hintTimeoutMs: Long,
        val speedTargetMs: Long,
        val upThreshold: Float,
        val downThreshold: Float,
        val hintsEnabledByDefault: Boolean
    )

    object ScreenSettings {
        var WIDTH: Int = 800
        var HEIGHT: Int = 600
    }

    object FontSettings {
        val MAIN: Paint = Paint().apply {
            textSize = 48f  // Changed: Increased from 30f to 48f for bigger math questions and better visibility
            color = Color.WHITE
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)  // Changed: Set to monospace bold to imitate 8-bit retro bold font
            isAntiAlias = false  // Changed: Set to false for pixelated retro look without custom font
        }
        val COMBO: Paint = Paint().apply {
            textSize = 36f  // Changed: Increased from 24f to 36f for consistency with MAIN font scaling
            color = Color.YELLOW
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)  // Changed: Set to monospace bold to imitate 8-bit retro bold font
            isAntiAlias = false  // Changed: Set to false for pixelated retro look
        }
    }

    object ColorSettings {
        const val WHITE = Color.WHITE
        const val BLACK = Color.BLACK
        const val YELLOW = Color.YELLOW
        const val GREEN = Color.GREEN
        const val RED = Color.RED
        const val BLUE = Color.BLUE
        const val AURA_GREEN = -2147418368  // Added: Semi-transparent green for respawn aura effect (equivalent to Color.argb(128, 0, 255, 0))
        const val AURA_CYAN = -2147418113  // Added: Semi-transparent cyan as alternative for aura (equivalent to Color.argb(128, 0, 255, 255))

    }

    object PlayerSettings {
        const val WIDTH = 30
        const val HEIGHT = 30
        const val SPEED = 8 // px per frame
        const val LIVES = 3
        const val RESPAWN_DURATION = 1000L // ms
    }

    // Added: New settings for player hit effects, including explosions when hit or crashing
    object PlayerHitSettings {
        const val EXPLOSION_DURATION = 500L  // ms for hit explosion animation
        const val EXPLOSION_MAX_RADIUS = 40  // Max radius for player hit explosion
        const val EXPLOSION_COLOR = ColorSettings.RED  // Color for player hit explosion
    }

    // Added: New settings for respawn aura animation to indicate new ship
    object RespawnAuraSettings {
        const val DURATION = 1500L  // ms for aura effect after respawn
        const val RADIUS_FACTOR = 1.5f  // Multiplier for aura radius based on player size
        val COLORS = listOf(ColorSettings.AURA_GREEN, ColorSettings.AURA_CYAN)  // Colors for flashing aura (can alternate)
        const val FLASH_INTERVAL = 250L  // ms between color flashes in aura
    }

    object AlienSettings {
        const val TOP_SIZE = 40
        const val LOWER_SIZE = 20
        const val BASE_SPEED = 2
        const val SHOOT_INTERVAL = 2000L // ms
        const val SHOOT_CHANCE = 0.005 // per frame
        val SHAPE_TYPES = listOf("square", "triangle", "circle")
    }

    object BulletSettings {
        const val WIDTH = 5
        const val HEIGHT = 10
        const val PLAYER_SPEED = -5
        const val ALIEN_SPEED = 5
    }

    object ComboSettings {
        const val DURATION = 1000L // ms
        const val FLOAT_SPEED = 15 // px total
    }

    object DifficultySettings {
        const val WINDOW = 10
        val DEFAULT_LEVELS: Map<Int, Pair<Int, Int>> = mapOf(
            1 to (1 to 12),
            2 to (1 to 12),
            3 to (1 to 20),
            4 to (1 to 20),
            5 to (1 to 20)
        )
    }

    object ExplosionSettings {
        const val DURATION = 500L // ms
    }

    object BackgroundSettings {
        const val STARS_COUNT = 50
    }

    object GameSettings {
        const val MAX_LEVEL = 5
    }

    val GRADE_BAND_PRESETS: Map<GradeBand, GradeBandSettings> = mapOf(
        GradeBand.G1_2 to GradeBandSettings(
            levelBounds = mapOf(1 to (1 to 5), 2 to (1 to 5), 3 to (1 to 10), 4 to (1 to 10), 5 to (1 to 10)),
            alienBaseSpeed = 1,
            alienShootChance = 0.0025,
            alienShootIntervalMs = 2600L,
            maxBullets = 12,
            topTargetCount = 4,
            lowerEnemyMin = 6,
            lowerEnemyMax = 8,
            scoreCorrect = 10,
            speedBonusMax = 5,
            wrongTopPenalty = 3,
            hintWrongAttempts = 2,
            hintTimeoutMs = 8_000L,
            speedTargetMs = 9_000L,
            upThreshold = 0.75f,
            downThreshold = 0.50f,
            hintsEnabledByDefault = true
        ),
        GradeBand.G3_4 to GradeBandSettings(
            levelBounds = mapOf(1 to (1 to 12), 2 to (1 to 12), 3 to (1 to 20), 4 to (1 to 20), 5 to (1 to 20)),
            alienBaseSpeed = 2,
            alienShootChance = 0.004,
            alienShootIntervalMs = 2200L,
            maxBullets = 16,
            topTargetCount = 5,
            lowerEnemyMin = 10,
            lowerEnemyMax = 14,
            scoreCorrect = 12,
            speedBonusMax = 8,
            wrongTopPenalty = 4,
            hintWrongAttempts = 3,
            hintTimeoutMs = 10_000L,
            speedTargetMs = 7_000L,
            upThreshold = 0.80f,
            downThreshold = 0.50f,
            hintsEnabledByDefault = true
        ),
        GradeBand.G5_PLUS to GradeBandSettings(
            levelBounds = mapOf(1 to (1 to 20), 2 to (1 to 20), 3 to (1 to 20), 4 to (5 to 50), 5 to (5 to 50)),
            alienBaseSpeed = 3,
            alienShootChance = 0.006,
            alienShootIntervalMs = 1800L,
            maxBullets = 20,
            topTargetCount = 6,
            lowerEnemyMin = 14,
            lowerEnemyMax = 20,
            scoreCorrect = 15,
            speedBonusMax = 10,
            wrongTopPenalty = 5,
            hintWrongAttempts = 3,
            hintTimeoutMs = 12_000L,
            speedTargetMs = 5_500L,
            upThreshold = 0.85f,
            downThreshold = 0.55f,
            hintsEnabledByDefault = false
        )
    )

    var currentGradeBand: GradeBand = GradeBand.G3_4
    var multiplayerMode: MultiplayerMode = MultiplayerMode.COOP_SHARED

    fun currentBandSettings(): GradeBandSettings =
        GRADE_BAND_PRESETS[currentGradeBand]
            ?: error("Missing grade-band settings for $currentGradeBand")

    /** Randomly generated background star positions */
    val starsPositions: List<Pair<Int, Int>> by lazy {
        List(BackgroundSettings.STARS_COUNT) {
            Pair(
                Random.nextInt(0, ScreenSettings.WIDTH),
                Random.nextInt(0, ScreenSettings.HEIGHT)
            )
        }
    }

    /** Pre-rendered background bitmap with black fill and stars */
    val backgroundBitmap: Bitmap by lazy {
        createBitmap(ScreenSettings.WIDTH, ScreenSettings.HEIGHT).apply {
            val canvas = Canvas(this)
            canvas.drawColor(ColorSettings.BLACK)
            val paint = Paint().apply { color = ColorSettings.WHITE }
            starsPositions.forEach { (x, y) ->
                canvas.drawCircle(x.toFloat(), y.toFloat(), 1f, paint)
            }
        }
    }

    // -- Asset Bitmaps (should be loaded after SurfaceView is measured!) --
    lateinit var playerBlueSprite: Bitmap
    lateinit var playerRedSprite: Bitmap
    val alienTopSprites: MutableMap<String, Bitmap> = mutableMapOf()
    val alienLowerSprites: MutableMap<String, Bitmap> = mutableMapOf()
    private var spritesLoaded: Boolean = false

    // Map for direct resource IDs (static names)
    private val spriteMap: Map<String, Int> = mapOf(
        "player_blue" to R.drawable.player_blue,
        "player_red" to R.drawable.player_red,
        "alien_square_green" to R.drawable.alien_square_green,
        "alien_triangle_green" to R.drawable.alien_triangle_green,
        "alien_circle_green" to R.drawable.alien_circle_green,
        "alien_square_red" to R.drawable.alien_square_red,
        "alien_triangle_red" to R.drawable.alien_triangle_red,
        "alien_circle_red" to R.drawable.alien_circle_red
    )

    /**
     * Safely loads a sprite by resource name. Returns a gray placeholder if missing.
     */
    private fun loadSprite(context: Context, name: String, fallbackColor: Int = Color.GRAY): Bitmap {
        val resId = spriteMap[name] ?: 0
        return if (resId != 0) {
            BitmapFactory.decodeResource(context.resources, resId)
        } else {
            // Create a visible placeholder
            createBitmap(32, 32).apply {
                eraseColor(fallbackColor)
                val c = Canvas(this)
                val p = Paint().apply { color = Color.DKGRAY; strokeWidth = 3f }
                c.drawLine(0f, 0f, 32f, 32f, p)
                c.drawLine(32f, 0f, 0f, 32f, p)
            }
        }
    }

    /**
     * Loads all required sprites, only once per app run.
     * Should be called after setting ScreenSettings to actual view size!
     */
    fun initSprites(context: Context) {
        if (spritesLoaded) return // Only load once
        playerBlueSprite = loadSprite(context, "player_blue")
        playerRedSprite = loadSprite(context, "player_red")
        alienTopSprites.clear()
        alienLowerSprites.clear()
        for (shape in AlienSettings.SHAPE_TYPES) {
            alienTopSprites[shape] = loadSprite(context, "alien_${shape}_green")
            alienLowerSprites[shape] = loadSprite(context, "alien_${shape}_red")
        }
        spritesLoaded = true
    }

    // Add bitmap recycling
    fun clearSprites() {
        playerBlueSprite.recycle()
        playerRedSprite.recycle()
        alienTopSprites.values.forEach { it.recycle() }
        alienLowerSprites.values.forEach { it.recycle() }
        alienTopSprites.clear()
        alienLowerSprites.clear()
        backgroundBitmap.recycle()
        spritesLoaded = false
    }
}
