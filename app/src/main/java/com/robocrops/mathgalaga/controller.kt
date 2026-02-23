package com.robocrops.mathgalaga

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import kotlin.random.Random

class GameController(val context: Context, val view: GameView) {
    var level = 1
    val world = mutableMapOf<String, MutableMap<Int, Any>>()
    private var nextEntityId = 0
    val playerEids = mutableListOf<Int>()

    val inputSystem = InputSystem(this)
    val movementSystem = MovementSystem(this)
    val shootingSystem = ShootingSystem(this)
    val collisionSystem = CollisionSystem(this)
    val lifespanSystem = LifespanSystem(this)
    val boundsSystem = BoundsSystem(this)
    val renderingSystem = RenderingSystem(this)

    var previousState: BaseState? = null
    var currentState: BaseState

    private val states = mapOf(
        "playing" to ::PlayingState,
        "paused" to ::PausedState,
        "level_transition" to ::LevelTransitionState,
        "game_over" to ::GameOverState,
        "win" to ::WinState
    )

    var restart = false
    var exit = false

    init {
        playerEids.add(newEntity())
        playerEids.add(newEntity())
        setupPlayers()
        currentState = CalibrationState(this)
    }

    fun isEndState(): Boolean = currentState is GameOverState || currentState is WinState

    private fun setupPlayers() {
        val colors = listOf(Config.ColorSettings.RED, Config.ColorSettings.BLUE)
        val startXs = listOf(100f, 600f)
        val sprites = listOf(Config.playerRedSprite, Config.playerBlueSprite)
        val now = System.currentTimeMillis()
        playerEids.forEachIndexed { i, eid ->
            val dm = DifficultyManager()
            val sprite = sprites[i]
            val size = Size(sprite.width, sprite.height)
            world.getOrPut("position") { mutableMapOf() }[eid] =
                Position(startXs[i], Config.ScreenSettings.HEIGHT - size.height - 10f)
            world.getOrPut("size") { mutableMapOf() }[eid] = size
            world.getOrPut("player") { mutableMapOf() }[eid] = Player(
                colors[i],
                dm,
                Config.PlayerSettings.LIVES,
                0.0,
                generateAdaptiveProblem(dm),
                0,
                "active",
                0L,
                false,
                startXs[i],
                now,
                0,
                0,
                0,
                0L
            )
            world.getOrPut("player_movement") { mutableMapOf() }[eid] = mapOf("speed" to Config.PlayerSettings.SPEED)
            world.getOrPut("shooter") { mutableMapOf() }[eid] = Shooter(500L, 0L, null, Config.BulletSettings.PLAYER_SPEED)
            world.getOrPut("render") { mutableMapOf() }[eid] = mapOf("type" to "sprite", "image" to sprite, "text" to null)
            world.getOrPut("collider") { mutableMapOf() }[eid] = true
        }
    }

    fun newEntity(): Int = nextEntityId++

    fun removeEntity(eid: Int) {
        world.values.forEach { it.remove(eid) }
    }

    fun switchState(stateName: String) {
        previousState = currentState
        currentState = states[stateName]?.invoke(this)
            ?: throw IllegalArgumentException("Invalid state: $stateName")
    }

    fun setupLevel() {
        world["position"]?.keys?.filter { !playerEids.contains(it) }?.toList()?.forEach { removeEntity(it) }

        val settings = Config.currentBandSettings()
        val shape = Config.AlienSettings.SHAPE_TYPES[(level - 1) % Config.AlienSettings.SHAPE_TYPES.size]
        val speed = settings.alienBaseSpeed + (level - 1)
        val answers = playerEids.map {
            ((world["player"]?.get(it) as? Player)?.problem?.get("answer") as? Int) ?: 1
        }
        val distractors = generateDistractors(answers.firstOrNull() ?: 1, Config.currentGradeBand)
        val nums = (answers + distractors).distinct().shuffled().take(settings.topTargetCount)

        getTopFormationPositions(level, settings.topTargetCount).forEachIndexed { i, (x, y) ->
            val valNum = nums.getOrNull(i) ?: Random.nextInt(1, 100)
            val eid = newEntity()
            val image = Config.alienTopSprites[shape]!!
            world.getOrPut("position") { mutableMapOf() }[eid] = Position(x, y)
            world.getOrPut("size") { mutableMapOf() }[eid] = Size(image.width, image.height)
            world.getOrPut("render") { mutableMapOf() }[eid] = mapOf("type" to "sprite", "image" to image, "text" to valNum.toString())
            world.getOrPut("alien") { mutableMapOf() }[eid] = Alien(valNum, shape)
            world.getOrPut("alien_movement") { mutableMapOf() }[eid] = AlienMovement(speed, 1)
            world.getOrPut("shooter") { mutableMapOf() }[eid] = Shooter(settings.alienShootIntervalMs, 0L, settings.alienShootChance, Config.BulletSettings.ALIEN_SPEED)
            world.getOrPut("collider") { mutableMapOf() }[eid] = true
        }

        val lowerCount = Random.nextInt(settings.lowerEnemyMin, settings.lowerEnemyMax + 1)
        getLowerFormationPositions(level, lowerCount).forEach { (x, y) ->
            val eid = newEntity()
            val image = Config.alienLowerSprites[shape]!!
            world.getOrPut("position") { mutableMapOf() }[eid] = Position(x, y)
            world.getOrPut("size") { mutableMapOf() }[eid] = Size(image.width, image.height)
            world.getOrPut("render") { mutableMapOf() }[eid] = mapOf("type" to "sprite", "image" to image, "text" to null)
            world.getOrPut("alien") { mutableMapOf() }[eid] = Alien(null, shape)
            world.getOrPut("alien_movement") { mutableMapOf() }[eid] = AlienMovement(speed, 1)
            world.getOrPut("shooter") { mutableMapOf() }[eid] = Shooter(settings.alienShootIntervalMs, 0L, settings.alienShootChance, Config.BulletSettings.ALIEN_SPEED)
            world.getOrPut("collider") { mutableMapOf() }[eid] = true
        }
    }

    fun getRect(eid: Int): Rect {
        val pos = world["position"]?.get(eid) as? Position ?: Position(0f, 0f)
        val size = world["size"]?.get(eid) as? Size ?: Size(0, 0)
        return Rect(pos.x.toInt(), pos.y.toInt(), pos.x.toInt() + size.width, pos.y.toInt() + size.height)
    }

    fun createBullet(shooterEid: Int, speed: Int) {
        if ((world["bullet"]?.size ?: 0) > Config.currentBandSettings().maxBullets) return

        val posS = world["position"]?.get(shooterEid) as? Position ?: return
        val sizeS = world["size"]?.get(shooterEid) as? Size ?: return
        val bx = posS.x + sizeS.width / 2 - Config.BulletSettings.WIDTH / 2
        val by = if (speed < 0) posS.y - Config.BulletSettings.HEIGHT else posS.y + sizeS.height
        val eid = newEntity()
        world.getOrPut("position") { mutableMapOf() }[eid] = Position(bx, by)
        world.getOrPut("size") { mutableMapOf() }[eid] = Size(Config.BulletSettings.WIDTH, Config.BulletSettings.HEIGHT)
        world.getOrPut("velocity") { mutableMapOf() }[eid] = Velocity(0, speed)
        world.getOrPut("render") { mutableMapOf() }[eid] = mapOf("type" to "rect", "color" to Config.ColorSettings.WHITE)
        world.getOrPut("bullet") { mutableMapOf() }[eid] = Bullet(shooterEid)
        world.getOrPut("collider") { mutableMapOf() }[eid] = true
    }

    fun update(delta: Double) = currentState.update(delta)

    fun draw(canvas: Canvas) = currentState.draw(canvas)

    fun handleJoystick(playerIndex: Int, dx: Float, dy: Float, shouldFire: Boolean) {
        val eid = playerEids.getOrNull(playerIndex) ?: return
        val p = world["player"]?.get(eid) as? Player ?: return
        if (p.state != "active") return
        val pos = world["position"]?.get(eid) as? Position ?: return
        val speed = (world["player_movement"]?.get(eid) as? Map<*, Int>)?.get("speed") ?: 10
        pos.x += dx * speed.toFloat()
        pos.y += dy * speed.toFloat()
        val size = world["size"]?.get(eid) as? Size ?: return
        pos.x = pos.x.coerceIn(0f, Config.ScreenSettings.WIDTH - size.width.toFloat())
        pos.y = pos.y.coerceIn(0f, Config.ScreenSettings.HEIGHT - size.height.toFloat())
        if (shouldFire) {
            Log.d("MathGalaga", "Joystick fire requested for player $playerIndex!")
            val s = world["shooter"]?.get(eid) as? Shooter ?: return
            val now = System.currentTimeMillis()
            if (now - s.lastShot > s.interval) {
                Log.d("MathGalaga", "Creating bullet for player $playerIndex!")
                createBullet(eid, s.bulletSpeed)
                s.lastShot = now
                view.playShootSound()
            }
        }
    }

    fun restartGame() {
        level = 1
        world.clear()
        nextEntityId = 0
        playerEids.clear()
        playerEids.add(newEntity())
        playerEids.add(newEntity())
        setupPlayers()
        Config.initSprites(context)
        switchState("level_transition")
    }
}

fun generateDistractors(correctAnswer: Int, band: Config.GradeBand): List<Int> {
    val near = listOf(correctAnswer - 2, correctAnswer - 1, correctAnswer + 1, correctAnswer + 2, correctAnswer + 10, correctAnswer - 10)
        .filter { it > 0 }
    val confusionStep = when (band) {
        Config.GradeBand.G1_2 -> 5
        Config.GradeBand.G3_4 -> 8
        Config.GradeBand.G5_PLUS -> 12
    }
    val far = (correctAnswer + confusionStep * 3).coerceAtLeast(1)
    return (near + listOf(correctAnswer + confusionStep, far))
        .filter { it != correctAnswer }
        .distinct()
        .shuffled()
        .take(4)
}
