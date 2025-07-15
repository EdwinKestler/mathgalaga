package com.robocrops.mathgalaga

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import kotlin.math.max
import kotlin.math.min
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
        currentState = CalibrationState(this) // Start here instead of LevelTransitionState
    }

    fun isEndState(): Boolean = currentState is GameOverState || currentState is WinState

    private fun setupPlayers() {
        val dm1 = DifficultyManager()
        val dm2 = DifficultyManager()
        val colors = listOf(Config.ColorSettings.RED, Config.ColorSettings.BLUE)
        val startXs = listOf(100f, 600f)
        val sprites = listOf(Config.playerRedSprite, Config.playerBlueSprite)
        playerEids.forEachIndexed { i, eid ->
            val sprite = sprites[i]
            val size = Size(sprite.width, sprite.height)
            world.getOrPut("position") { mutableMapOf() }[eid] =
                Position(startXs[i], Config.ScreenSettings.HEIGHT - size.height - 10f)
            world.getOrPut("size") { mutableMapOf() }[eid] = size
            world.getOrPut("player") { mutableMapOf() }[eid] = Player(
                colors[i],
                if (i == 0) dm1 else dm2,
                Config.PlayerSettings.LIVES,
                0.0,
                generateAdaptiveProblem(if (i == 0) dm1 else dm2),
                0,
                "active",
                0L,
                false,
                startXs[i]
            )
            world.getOrPut("player_movement") { mutableMapOf() }[eid] =
                mapOf("speed" to Config.PlayerSettings.SPEED) // Keep as map for simplicity
            world.getOrPut("shooter") { mutableMapOf() }[eid] = Shooter(
                500L,
                0L,
                null,
                Config.BulletSettings.PLAYER_SPEED
            )
            world.getOrPut("render") { mutableMapOf() }[eid] = mapOf(
                "type" to "sprite",
                "image" to sprite,
                "text" to null
            )
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
        // Remove all non-player entities
        world["position"]?.keys?.filter { !playerEids.contains(it) }?.toList()?.forEach { removeEntity(it) }

        val shape =
            Config.AlienSettings.SHAPE_TYPES[(level - 1) % Config.AlienSettings.SHAPE_TYPES.size]
        val speed = Config.AlienSettings.BASE_SPEED + (level - 1)
        val answers = playerEids.map {
            ((world["player"]?.get(it) as? Player)?.problem?.get("answer") as? Int) ?: 1
        }
        val distractors = List(3) { Random.nextInt(1, 100) }
        val nums = (answers + distractors).shuffled()

        // Place top aliens (numbered)
        getTopFormationPositions(level).forEachIndexed { i, (x, y) ->
            val valNum = nums.getOrNull(i) ?: Random.nextInt(1, 100)
            val eid = newEntity()
            val image = Config.alienTopSprites[shape]!!
            world.getOrPut("position") { mutableMapOf() }[eid] = Position(x, y)
            world.getOrPut("size") { mutableMapOf() }[eid] = Size(image.width, image.height)
            world.getOrPut("render") { mutableMapOf() }[eid] =
                mapOf("type" to "sprite", "image" to image, "text" to valNum.toString())
            world.getOrPut("alien") { mutableMapOf() }[eid] = Alien(valNum, shape)
            world.getOrPut("alien_movement") { mutableMapOf() }[eid] =
                AlienMovement(speed, 1)
            world.getOrPut("shooter") { mutableMapOf() }[eid] = Shooter(
                Config.AlienSettings.SHOOT_INTERVAL,
                0L,
                Config.AlienSettings.SHOOT_CHANCE,
                Config.BulletSettings.ALIEN_SPEED
            )
            world.getOrPut("collider") { mutableMapOf() }[eid] = true
        }
        // Place lower aliens (no number)
        getLowerFormationPositions(level).forEach { (x, y) ->
            val eid = newEntity()
            val image = Config.alienLowerSprites[shape]!!
            world.getOrPut("position") { mutableMapOf() }[eid] = Position(x, y)
            world.getOrPut("size") { mutableMapOf() }[eid] = Size(image.width, image.height)
            world.getOrPut("render") { mutableMapOf() }[eid] =
                mapOf("type" to "sprite", "image" to image, "text" to null)
            world.getOrPut("alien") { mutableMapOf() }[eid] = Alien(null, shape)
            world.getOrPut("alien_movement") { mutableMapOf() }[eid] =
                AlienMovement(speed, 1)
            world.getOrPut("shooter") { mutableMapOf() }[eid] = Shooter(
                Config.AlienSettings.SHOOT_INTERVAL,
                0L,
                Config.AlienSettings.SHOOT_CHANCE,
                Config.BulletSettings.ALIEN_SPEED
            )
            world.getOrPut("collider") { mutableMapOf() }[eid] = true
        }
    }

    fun getRect(eid: Int): Rect {
        val pos = world["position"]?.get(eid) as? Position ?: Position(0f, 0f)
        val size = world["size"]?.get(eid) as? Size ?: Size(0, 0)
        return Rect(pos.x.toInt(), pos.y.toInt(), pos.x.toInt() + size.width, pos.y.toInt() + size.height)
    }

    fun createBullet(shooterEid: Int, speed: Int) {
        // Optional: Limit max bullets
        if ((world["bullet"]?.size ?: 0) > 20) return

        val posS = world["position"]?.get(shooterEid) as? Position ?: return
        val sizeS = world["size"]?.get(shooterEid) as? Size ?: return
        val bx = posS.x + sizeS.width / 2 - Config.BulletSettings.WIDTH / 2
        val by = if (speed < 0) posS.y - Config.BulletSettings.HEIGHT else posS.y + sizeS.height
        val eid = newEntity()
        world.getOrPut("position") { mutableMapOf() }[eid] = Position(bx, by)
        world.getOrPut("size") { mutableMapOf() }[eid] =
            Size(Config.BulletSettings.WIDTH, Config.BulletSettings.HEIGHT)
        world.getOrPut("velocity") { mutableMapOf() }[eid] = Velocity(0, speed)
        world.getOrPut("render") { mutableMapOf() }[eid] =
            mapOf("type" to "rect", "color" to Config.ColorSettings.WHITE)
        world.getOrPut("bullet") { mutableMapOf() }[eid] = Bullet(shooterEid)
        world.getOrPut("collider") { mutableMapOf() }[eid] = true
    }

    fun update(delta: Double) = currentState.update(delta)

    fun draw(canvas: Canvas) = currentState.draw(canvas)

    // Removed: fun handleTouch(event: MotionEvent) {}  (no touch support)

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
        // Reset game state without restarting the activity
        level = 1
        world.clear()
        nextEntityId = 0
        playerEids.clear()
        playerEids.add(newEntity())
        playerEids.add(newEntity())
        setupPlayers()
        Config.initSprites(context) // Reload sprites to ensure they are not recycled
        switchState("level_transition")
    }
}

// Note: No major code changes were needed in this file for the requested improvements (explosions on player hits/crashes, respawn aura, bigger retro fonts). The logic for explosions and aura is handled in Systems.kt (CollisionSystem, LifespanSystem, RenderingSystem), and font changes are in Config.kt. This file remains as is, with the systems integrating the new features via the world ECS.