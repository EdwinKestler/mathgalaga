package com.robocrops.mathgalaga

import android.content.Context
import android.graphics.*
import kotlin.random.Random

object Config {

    object ScreenSettings {
        var WIDTH: Int = 800
        var HEIGHT: Int = 600
    }

    object FontSettings {
        val MAIN: Paint = Paint().apply {
            textSize = 30f
            color = Color.WHITE
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val COMBO: Paint = Paint().apply {
            textSize = 24f
            color = Color.YELLOW
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }

    object ColorSettings {
        val WHITE = Color.WHITE
        val BLACK = Color.BLACK
        val YELLOW = Color.YELLOW
        val GREEN = Color.GREEN
        val RED = Color.RED
        val BLUE = Color.BLUE
    }

    object PlayerSettings {
        const val WIDTH = 40
        const val HEIGHT = 40
        const val SPEED = 8 // px per frame
        const val LIVES = 3
        const val RESPAWN_DURATION = 1000L // ms
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
        // Map: level (1-based) -> inclusive range (lo..hi)
        val LEVELS: Map<Int, Pair<Int, Int>> = mapOf(
            1 to (1 to 5),
            2 to (1 to 10),
            3 to (1 to 20),
            4 to (1 to 50),
            5 to (5 to 50)
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
        Bitmap.createBitmap(ScreenSettings.WIDTH, ScreenSettings.HEIGHT, Bitmap.Config.ARGB_8888).apply {
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

    /**
     * Safely loads a sprite by resource name. Returns a gray placeholder if missing.
     */
    private fun loadSprite(context: Context, name: String, fallbackColor: Int = Color.GRAY): Bitmap {
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        return if (resId != 0) {
            BitmapFactory.decodeResource(context.resources, resId)
        } else {
            // Create a visible placeholder
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
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