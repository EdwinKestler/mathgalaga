package com.robocrops.mathgalaga

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// Component data classes for better type safety and performance
data class Position(var x: Float, var y: Float)
data class Size(val width: Int, val height: Int)
data class Velocity(val vx: Int, val vy: Int)
data class AlienMovement(var speed: Int, var direction: Int)
data class Shooter(var interval: Long, var last_shot: Long, var chance: Double?, var bullet_speed: Int)
data class Player(var color: Int, var dm: DifficultyManager, var lives: Int, var score: Double, var problem: Map<String, Any>, var streak: Int, var state: String, var respawn_time: Long, var cleared_top: Boolean, var start_x: Float)
data class Alien(val number: Int?, val shape: String)
data class Bullet(val owner_eid: Int)
data class Explosion(val start: Long)
data class Lifespan(val start: Long, val duration: Long)
data class FloatUp(val speed: Int)

// Target frame time for delta scaling (60 FPS)
private const val TARGET_FRAME_TIME_MS = 1000.0 / 60.0

// -- SYSTEMS --

open class BaseSystem(protected val controller: GameController) {
    open fun update(delta: Double) {}
}

class InputSystem(controller: GameController) : BaseSystem(controller) {
    override fun update(delta: Double) {
        // Handled by handleTouch/handleJoystick
    }
}

class MovementSystem(controller: GameController) : BaseSystem(controller) {
    override fun update(delta: Double) {
        val deltaFactor = delta / TARGET_FRAME_TIME_MS
        // Alien movement
        controller.world["alien_movement"]?.forEach { (eid, amAny) ->
            val am = amAny as? AlienMovement ?: return@forEach
            val pos = controller.world["position"]?.get(eid) as? Position ?: return@forEach
            val size = controller.world["size"]?.get(eid) as? Size ?: return@forEach
            pos.x += (am.speed * am.direction * deltaFactor).toFloat()
            if (pos.x < 0 || pos.x + size.width > Config.ScreenSettings.WIDTH) {
                am.direction = -am.direction
                pos.y += (20 * deltaFactor).toFloat()
            }
        }
        // Bullet/velocity movement
        controller.world["velocity"]?.forEach { (eid, velAny) ->
            val vel = velAny as? Velocity ?: return@forEach
            val pos = controller.world["position"]?.get(eid) as? Position ?: return@forEach
            pos.x += (vel.vx * deltaFactor).toFloat()
            pos.y += (vel.vy * deltaFactor).toFloat()
        }
    }
}

class ShootingSystem(controller: GameController) : BaseSystem(controller) {
    override fun update(delta: Double) {
        val now = System.currentTimeMillis()
        val deltaFactor = delta / TARGET_FRAME_TIME_MS
        controller.world["shooter"]?.forEach { (eid, sAny) ->
            val s = sAny as? Shooter ?: return@forEach
            if (s.chance != null) {
                // Alien shooting - scale chance for frame-rate independence
                val scaledChance = s.chance!! * deltaFactor
                if (Random.nextFloat() < scaledChance
                    && now - s.last_shot > s.interval
                ) {
                    controller.createBullet(eid, s.bullet_speed)
                    s.last_shot = now
                }
            }
            // Player shooting: Handled in input
        }
    }
}

class CollisionSystem(controller: GameController) : BaseSystem(controller) {
    override fun update(delta: Double) {
        val hits = mutableListOf<Pair<Int, Int>>()
        val bulletEids = controller.world["bullet"]?.keys?.toList() ?: emptyList()
        val alienEids = controller.world["alien"]?.keys?.toList() ?: emptyList()

        if (bulletEids.isEmpty() || alienEids.isEmpty()) return

        // Optimized: Sort by x-position for sweep-and-prune
        val sortedBullets = bulletEids.sortedBy { (controller.world["position"]?.get(it) as? Position)?.x ?: Float.MAX_VALUE }
        val sortedAliens = alienEids.sortedBy { (controller.world["position"]?.get(it) as? Position)?.x ?: Float.MAX_VALUE }

        var alienIndex = 0
        sortedBullets.forEach { bEid ->
            val bPos = controller.world["position"]?.get(bEid) as? Position ?: return@forEach
            val bSize = controller.world["size"]?.get(bEid) as? Size ?: return@forEach
            val bRect = controller.getRect(bEid)
            var hit = false

            // Advance alienIndex to potential overlaps
            while (alienIndex < sortedAliens.size) {
                val aEidTemp = sortedAliens[alienIndex]
                val aPos = controller.world["position"]?.get(aEidTemp) as? Position ?: break
                val aSize = controller.world["size"]?.get(aEidTemp) as? Size ?: break
                if (aPos.x + aSize.width >= bPos.x) break
                alienIndex++
            }

            // Check overlapping aliens
            for (j in alienIndex until sortedAliens.size) {
                val aEid = sortedAliens[j]
                if (controller.world["explosion"]?.containsKey(aEid) == true) continue
                val aPos = controller.world["position"]?.get(aEid) as? Position ?: continue
                val aSize = controller.world["size"]?.get(aEid) as? Size ?: continue
                if (aPos.x > bPos.x + bSize.width) break // No more overlaps
                val aRect = controller.getRect(aEid)
                if (Utils.collides(bRect, aRect)) {
                    hits.add(Pair(bEid, aEid))
                    hit = true
                    break
                }
            }
            if (hit) return@forEach

            // Player checks (fewer, no sorting needed)
            controller.playerEids.forEach { pEid ->
                val p = controller.world["player"]?.get(pEid) as? Player ?: return@forEach
                if (p.state != "active") return@forEach
                val pRect = controller.getRect(pEid)
                if (Utils.collides(bRect, pRect)) {
                    hits.add(Pair(bEid, pEid))
                    return@forEach
                }
            }
        }
        // Process all hits
        hits.forEach { (bEid, targetEid) ->
            val ownerEid = (controller.world["bullet"]?.get(bEid) as? Bullet)?.owner_eid ?: return@forEach
            controller.removeEntity(bEid)
            if (controller.playerEids.contains(targetEid)) {
                // Alien bullet hit player
                if (controller.playerEids.contains(ownerEid)) return@forEach // Skip player bullet hitting player
                val p = controller.world["player"]?.get(targetEid) as? Player ?: return@forEach
                p.lives -= 1
                controller.view.playHitSound()
                if (p.lives > 0) {
                    p.state = "respawning"
                    p.respawn_time = System.currentTimeMillis()
                } else {
                    p.state = "dead"
                }
            } else {
                // Player bullet hit alien
                if (!controller.playerEids.contains(ownerEid)) return@forEach // Only process player bullets on aliens
                val p = controller.world["player"]?.get(ownerEid) as? Player ?: return@forEach
                val a = controller.world["alien"]?.get(targetEid) as? Alien ?: return@forEach
                var canKill = true
                if (a.number != null) {
                    if (a.number != (p.problem["answer"] as? Int) || p.cleared_top)
                        canKill = false
                }
                if (!canKill) return@forEach
                // Kill alien
                val now = System.currentTimeMillis()
                controller.world.getOrPut("explosion") { mutableMapOf() }[targetEid] = Explosion(now)
                controller.world.getOrPut("lifespan") { mutableMapOf() }[targetEid] = Lifespan(now, Config.ExplosionSettings.DURATION)
                controller.world["alien_movement"]?.remove(targetEid)
                controller.world["shooter"]?.remove(targetEid)
                controller.world["collider"]?.remove(targetEid)
                controller.world["render"]?.set(
                    targetEid, mapOf(
                        "type" to "explosion",
                        "color" to Config.ColorSettings.YELLOW,
                        "max_radius" to 30
                    )
                )
                controller.view.playExplosionSound()
                if (a.number == null) {
                    p.score += 1.0
                } else {
                    p.cleared_top = true
                    p.streak += 1
                    val bonus = 1 + 0.1 * p.streak
                    p.score += bonus
                    val pos = controller.world["position"]?.get(targetEid) as? Position ?: return@forEach
                    val comboEid = controller.newEntity()
                    controller.world["position"]?.set(comboEid, Position(pos.x, pos.y))
                    controller.world["render"]?.set(
                        comboEid, mapOf(
                            "type" to "combo_text",
                            "text" to "×${p.streak}",
                            "color" to Config.ColorSettings.YELLOW,
                            "font" to Config.FontSettings.COMBO
                        )
                    )
                    controller.world.getOrPut("lifespan") { mutableMapOf() }[comboEid] =
                        Lifespan(now, Config.ComboSettings.DURATION)
                    controller.world.getOrPut("float_up") { mutableMapOf() }[comboEid] =
                        FloatUp(Config.ComboSettings.FLOAT_SPEED)
                    p.dm.record(true)
                }
            }
        }
    }
}

class LifespanSystem(controller: GameController) : BaseSystem(controller) {
    override fun update(delta: Double) {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<Int>()
        controller.world["lifespan"]?.forEach { (eid, lAny) ->
            val l = lAny as? Lifespan ?: return@forEach
            if (now - l.start > l.duration) {
                toRemove.add(eid)
            }
        }
        toRemove.forEach { controller.removeEntity(it) }
        // Respawn logic
        controller.world["player"]?.forEach { (eid, pAny) ->
            val p = pAny as? Player ?: return@forEach
            if (p.state == "respawning" && now - p.respawn_time > Config.PlayerSettings.RESPAWN_DURATION) {
                p.state = "active"
                val pos = controller.world["position"]?.get(eid) as? Position ?: return@forEach
                pos.x = p.start_x
            }
        }
    }
}

class BoundsSystem(controller: GameController) : BaseSystem(controller) {
    override fun update(delta: Double) {
        val toRemove = mutableListOf<Int>()
        controller.world["bullet"]?.keys?.forEach { eid ->
            val pos = controller.world["position"]?.get(eid) as? Position ?: return@forEach
            val size = controller.world["size"]?.get(eid) as? Size ?: return@forEach
            if (pos.y < 0 || pos.y + size.height > Config.ScreenSettings.HEIGHT) {
                toRemove.add(eid)
            }
        }
        toRemove.forEach { controller.removeEntity(it) }
    }
}

class RenderingSystem(controller: GameController) : BaseSystem(controller) {
    override fun update(delta: Double) {}

    fun draw(canvas: Canvas) {
        // FIX: Iterate over copy of keys to avoid ConcurrentModificationException
        val renderKeys = controller.world["render"]?.keys?.toList() ?: emptyList()
        for (eid in renderKeys) {
            if (controller.playerEids.contains(eid) &&
                (controller.world["player"]?.get(eid) as? Player)?.state == "dead"
            ) continue
            val r = controller.world["render"]?.get(eid) as? Map<String, Any> ?: continue
            val pos = controller.world["position"]?.get(eid) as? Position ?: continue
            val size = controller.world["size"]?.get(eid) as? Size ?: Size(0, 0)
            when (r["type"]) {
                "rect" -> {
                    val paint = Paint().apply { color = r["color"] as? Int ?: Config.ColorSettings.WHITE }
                    canvas.drawRect(controller.getRect(eid), paint)
                }
                "sprite" -> {
                    val image = r["image"] as? Bitmap ?: continue
                    if (image.isRecycled) {
                        Log.e("MathGalaga", "Recycled bitmap in sprite render, skipping")
                        continue
                    }
                    canvas.drawBitmap(image, pos.x, pos.y, null)
                    val text = r["text"] as? String
                    if (!text.isNullOrEmpty()) {
                        val paint = Config.FontSettings.MAIN
                        val bounds = Rect()
                        paint.getTextBounds(text, 0, text.length, bounds)
                        val centerX = pos.x + size.width / 2 - bounds.width() / 2
                        val centerY = pos.y + size.height / 2 + bounds.height() / 2
                        canvas.drawText(text, centerX, centerY, paint)
                    }
                }
                "explosion" -> {
                    val exp = controller.world["explosion"]?.get(eid) as? Explosion ?: continue
                    val elapsed = System.currentTimeMillis() - exp.start
                    val radius = ((elapsed.toFloat() / Config.ExplosionSettings.DURATION) * (r["max_radius"] as? Int
                        ?: 30)).toInt()
                    val centerX = pos.x + size.width / 2
                    val centerY = pos.y + size.height / 2
                    val paint = Paint().apply { color = r["color"] as? Int ?: Config.ColorSettings.YELLOW }
                    canvas.drawCircle(centerX, centerY, radius.toFloat(), paint)
                }
                "combo_text" -> {
                    val lifespan = controller.world["lifespan"]?.get(eid) as? Lifespan ?: continue
                    val elapsed = System.currentTimeMillis() - lifespan.start
                    val alpha = (255 * (1 - elapsed.toFloat() / lifespan.duration)).toInt().coerceIn(0, 255)
                    val paint = (r["font"] as? Paint ?: Config.FontSettings.COMBO)
                    paint.alpha = alpha

                    // Floating up effect
                    val floatUp = controller.world["float_up"]?.get(eid) as? FloatUp ?: continue
                    val ypos = pos.y - (elapsed.toFloat() / lifespan.duration) * floatUp.speed.toFloat()

                    // Draw the combo text
                    canvas.drawText(r["text"] as? String ?: "", pos.x, ypos, paint)
                }
            }
        }
    }
}

// -- GAME STATES --

abstract class BaseState(protected val controller: GameController) {
    open fun handleTouch(event: MotionEvent) {}
    open fun update(delta: Double) {}
    open fun draw(canvas: Canvas) {}
}

class PlayingState(controller: GameController) : BaseState(controller) {
    override fun update(delta: Double) {
        controller.inputSystem.update(delta)
        controller.shootingSystem.update(delta)
        controller.movementSystem.update(delta)
        controller.collisionSystem.update(delta)
        controller.lifespanSystem.update(delta)
        controller.boundsSystem.update(delta)
        // Level advance
        if (controller.playerEids.all {
                (controller.world["player"]?.get(it) as? Player)?.cleared_top ?: false
            }) {
            controller.playerEids.forEach { eid ->
                val p = controller.world["player"]?.get(eid) as? Player ?: return@forEach
                p.cleared_top = false
                p.problem = generateAdaptiveProblem(p.dm)
            }
            controller.level++
            if (controller.level > Config.GameSettings.MAX_LEVEL) {
                controller.switchState("win")
            } else {
                controller.switchState("level_transition")
            }
        }
        // Check for player death
        if (controller.playerEids.any {
                (controller.world["player"]?.get(it) as? Player)?.lives ?: 1 <= 0
            }) {
            controller.switchState("game_over")
        }
    }

    override fun draw(canvas: Canvas) {
        // Use pre-rendered background
        if (Config.backgroundBitmap.isRecycled) {
            Config.initSprites(controller.context)
        }
        canvas.drawBitmap(Config.backgroundBitmap, 0f, 0f, null)
        controller.renderingSystem.draw(canvas)
        // HUD
        controller.playerEids.forEachIndexed { i, eid ->
            val p = controller.world["player"]?.get(eid) as? Player ?: return@forEachIndexed
            val x0 = if (i == 0) 10f else Config.ScreenSettings.WIDTH - 140f
            canvas.drawText((p.problem["question"] as? String) ?: "", x0, 30f, Config.FontSettings.MAIN)
            canvas.drawText("P${i + 1} Score:${p.score.toInt()}", x0, 70f, Config.FontSettings.MAIN)
            canvas.drawText("P${i + 1} Lives:${p.lives}", x0, 110f, Config.FontSettings.MAIN)
        }
        val lvlTxt = "Level:${controller.level}"
        val bounds = Rect()
        Config.FontSettings.MAIN.getTextBounds(lvlTxt, 0, lvlTxt.length, bounds)
        canvas.drawText(
            lvlTxt,
            (Config.ScreenSettings.WIDTH / 2 - bounds.width() / 2).toFloat(),
            30f,
            Config.FontSettings.MAIN
        )
    }
}

class PausedState(controller: GameController) : BaseState(controller) {
    override fun draw(canvas: Canvas) {
        controller.previousState?.draw(canvas)
        val text = "PAUSED"
        val paint = Config.FontSettings.MAIN.apply { color = Config.ColorSettings.YELLOW }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(
            text,
            (Config.ScreenSettings.WIDTH / 2 - bounds.width() / 2).toFloat(),
            (Config.ScreenSettings.HEIGHT / 2).toFloat(),
            paint
        )
    }
}

class LevelTransitionState(controller: GameController) : BaseState(controller) {
    private val startTime = System.currentTimeMillis()

    init {
        controller.setupLevel()
    }

    override fun update(delta: Double) {
        if (System.currentTimeMillis() - startTime > 2000) {
            controller.switchState("playing")
        }
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(Config.ColorSettings.BLACK)
        val msg = "Level ${controller.level}! Good luck!"
        val paint = Config.FontSettings.MAIN
        val bounds = Rect()
        paint.getTextBounds(msg, 0, msg.length, bounds)
        canvas.drawText(
            msg,
            (Config.ScreenSettings.WIDTH / 2 - bounds.width() / 2).toFloat(),
            (Config.ScreenSettings.HEIGHT / 2 - bounds.height() / 2).toFloat(),
            paint
        )
    }
}

class GameOverState(controller: GameController) : BaseState(controller) {
    override fun draw(canvas: Canvas) {
        canvas.drawColor(Config.ColorSettings.BLACK)
        val over = "Better luck next time"
        val instr = "Tap to retry or back to exit"
        val paint = Config.FontSettings.MAIN
        val boundsOver = Rect()
        paint.getTextBounds(over, 0, over.length, boundsOver)
        canvas.drawText(
            over,
            (Config.ScreenSettings.WIDTH / 2 - boundsOver.width() / 2).toFloat(),
            (Config.ScreenSettings.HEIGHT / 2 - 30).toFloat(),
            paint
        )
        val boundsInstr = Rect()
        paint.getTextBounds(instr, 0, instr.length, boundsInstr)
        canvas.drawText(
            instr,
            (Config.ScreenSettings.WIDTH / 2 - boundsInstr.width() / 2).toFloat(),
            (Config.ScreenSettings.HEIGHT / 2 + 10).toFloat(),
            paint
        )
    }
}

class WinState(controller: GameController) : BaseState(controller) {
    override fun draw(canvas: Canvas) {
        canvas.drawColor(Config.ColorSettings.BLACK)
        val winMsg = "Congratulations! You Won!"
        val instr = "Tap to play again or back to exit"
        val paint = Config.FontSettings.MAIN
        val boundsWin = Rect()
        paint.getTextBounds(winMsg, 0, winMsg.length, boundsWin)
        canvas.drawText(
            winMsg,
            (Config.ScreenSettings.WIDTH / 2 - boundsWin.width() / 2).toFloat(),
            (Config.ScreenSettings.HEIGHT / 2 - 30).toFloat(),
            paint
        )
        val boundsInstr = Rect()
        paint.getTextBounds(instr, 0, instr.length, boundsInstr)
        canvas.drawText(
            instr,
            (Config.ScreenSettings.WIDTH / 2 - boundsInstr.width() / 2).toFloat(),
            (Config.ScreenSettings.HEIGHT / 2 + 10).toFloat(),
            paint
        )
    }
}

// Add this new state
class CalibrationState(controller: GameController) : BaseState(controller) {
    private var currentStep = 0 // 0: P1 move, 1: P2 move, 2: P1 fire, 3: P2 fire
    private var pauseStartTime: Long = 0L
    private var isPausing = false

    private val handler = Handler(Looper.getMainLooper())

    init {
        setupCalibration()
    }

    private fun setupCalibration() {
        controller.view.startCalibration { deviceId, player, isFire ->
            controller.view.playerJoystickMap[deviceId] = player
            Log.d("MathGalaga", "Calibrated device $deviceId for player $player (isFire: $isFire)")
            startPause()
        }
    }

    private fun startPause() {
        isPausing = true
        pauseStartTime = System.currentTimeMillis()
        controller.view.setCalibratingPlayer(-1) // Ignore inputs during pause
        handler.postDelayed({
            isPausing = false
            currentStep++
            if (currentStep > 3) {
                controller.view.endCalibration()
                controller.switchState("level_transition")
            } else {
                controller.view.setCalibratingPlayer(if (currentStep == 0 || currentStep == 2) 0 else 1)
                setupCalibration() // Reset listener for next step
            }
        }, 2000)
    }

    override fun update(delta: Double) {
        if (isPausing) return
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(Config.ColorSettings.BLACK)
        val msg = if (isPausing) "Calibrated! Preparing next step..." else when (currentStep) {
            0 -> "Player 1: Move your joystick"
            1 -> "Player 2: Move your joystick"
            2 -> "Player 1: Press fire button"
            3 -> "Player 2: Press fire button"
            else -> ""
        }
        val paint = Config.FontSettings.MAIN
        val bounds = Rect()
        paint.getTextBounds(msg, 0, msg.length, bounds)
        canvas.drawText(
            msg,
            (Config.ScreenSettings.WIDTH / 2 - bounds.width() / 2).toFloat(),
            (Config.ScreenSettings.HEIGHT / 2).toFloat(),
            paint
        )
    }
}

// -- CONTROLLER --

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

    private fun setupPlayers() {
        val dm1 = DifficultyManager()
        val dm2 = DifficultyManager()
        val colors = listOf(Config.ColorSettings.BLUE, Config.ColorSettings.RED)
        val startXs = listOf(100f, 600f)
        val sprites = listOf(Config.playerBlueSprite, Config.playerRedSprite)
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

    fun getTopFormationPositions(level: Int): List<Pair<Float, Float>> {
        return when (level) {
            1 -> List(5) { Pair(100f + it * 120f, 50f) }
            2 -> List(5) { Pair(100f + it * 120f, 50f + if (it % 2 == 0) 50f else 0f) }
            3 -> {
                val centerX = Config.ScreenSettings.WIDTH / 2f
                List(5) { Pair(centerX - 200f + it * 100f, 50f + kotlin.math.abs(2 - it) * 30f) }
            }
            4 -> List(5) { Pair(100f + it * 120f, 50f + kotlin.math.abs(it - 2) * 30f) }
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

    fun handleTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val mid = Config.ScreenSettings.WIDTH / 2f
                val eid = if (event.x < mid) playerEids[0] else playerEids[1]
                val p = world["player"]?.get(eid) as? Player ?: return
                if (p.state != "active") return
                val pos = world["position"]?.get(eid) as? Position ?: return
                val size = world["size"]?.get(eid) as? Size ?: return
                pos.x = max(0f, min(event.x - size.width / 2f, Config.ScreenSettings.WIDTH - size.width.toFloat()))
                // Shoot if tap above player y
                val s = world["shooter"]?.get(eid) as? Shooter ?: return
                val now = System.currentTimeMillis()
                if (event.y < pos.y && now - s.last_shot > s.interval) {
                    createBullet(eid, s.bullet_speed)
                    s.last_shot = now
                    view.playShootSound()
                }
            }
        }
        currentState.handleTouch(event)
        // Handle restart on game over/win
        if (currentState is GameOverState || currentState is WinState) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                restartGame()
            }
        }
    }

    fun handleJoystick(playerIndex: Int, dx: Float, dy: Float, shouldFire: Boolean) {
        val eid = playerEids.getOrNull(playerIndex) ?: return
        val p = world["player"]?.get(eid) as? Player ?: return
        if (p.state != "active") return
        val pos = world["position"]?.get(eid) as? Position ?: return
        val speed = (world["player_movement"]?.get(eid) as? Map<String, Int>)?.get("speed") ?: 10
        pos.x += dx * speed.toFloat()
        pos.y += dy * speed.toFloat()
        val size = world["size"]?.get(eid) as? Size ?: return
        pos.x = pos.x.coerceIn(0f, Config.ScreenSettings.WIDTH - size.width.toFloat())
        pos.y = pos.y.coerceIn(0f, Config.ScreenSettings.HEIGHT - size.height.toFloat())
        if (shouldFire) {
            Log.d("MathGalaga", "Joystick fire requested for player $playerIndex!")
            val s = world["shooter"]?.get(eid) as? Shooter ?: return
            val now = System.currentTimeMillis()
            if (now - s.last_shot > s.interval) {
                Log.d("MathGalaga", "Creating bullet for player $playerIndex!")
                createBullet(eid, s.bullet_speed)
                s.last_shot = now
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