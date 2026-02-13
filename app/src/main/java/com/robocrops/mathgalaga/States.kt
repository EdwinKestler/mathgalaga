package com.robocrops.mathgalaga

import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log

abstract class BaseState(protected val controller: GameController) {
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
                (controller.world["player"]?.get(it) as? Player)?.clearedTop ?: false
            }) {
            controller.playerEids.forEach { eid ->
                val p = controller.world["player"]?.get(eid) as? Player ?: return@forEach
                p.clearedTop = false
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
                ((controller.world["player"]?.get(it) as? Player)?.lives ?: 1) <= 0
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
        val instr = "Press any button to restart"
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
        val instr = "Press any button to play again"
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
    private var currentStep = 0
    private val messages = listOf(
        "Player 1: Move RED joystick all the way LEFT",
        "Player 2: Move BLUE joystick all the way RIGHT",
        "Player 1: Press RED fire button",
        "Player 2: Press BLUE fire button"
    )

    private var isPausing = false
    private val handler = Handler(Looper.getMainLooper())

    init {
        setupCalibration()
    }

    private fun setupCalibration() {
        val player = currentStep % 2
        if (currentStep == 0) {
            Log.d("MathGalaga", "now calibrating first player")
        } else if (currentStep == 1) {
            Log.d("MathGalaga", "now calibrating second player")
        }
        Log.d("MathGalaga", "Setup calibration for step $currentStep, player $player")
        controller.view.setCalibratingPlayer(player)
        controller.view.startCalibration { deviceId, calibratedPlayer, isFire ->
            Log.d("MathGalaga", "Detection for player $calibratedPlayer, device $deviceId, isFire $isFire")
            val isMoveStep = currentStep < 2
            if (isMoveStep == isFire) {
                Log.d("MathGalaga", "Wrong input type for step $currentStep (isFire: $isFire), ignoring")
                return@startCalibration
            }

            // During calibration we intentionally learn device-to-player mapping dynamically.
            val otherPlayer = if (calibratedPlayer == 0) 1 else 0
            if (controller.view.playerDeviceIds[otherPlayer] == deviceId) {
                Log.d("MathGalaga", "Device $deviceId is already assigned to player $otherPlayer, ignoring")
                return@startCalibration
            }

            controller.view.playerDeviceIds[calibratedPlayer] = deviceId
            Log.d(
                "MathGalaga",
                "Calibrated device $deviceId for player $calibratedPlayer (isFire: $isFire)"
            )
            controller.view.playerJoystickMap[deviceId] = calibratedPlayer
            Log.d("MathGalaga", "player ${calibratedPlayer + 1} calibrated successfully")
            controller.view.setCalibratingPlayer(-1)
            isPausing = true
            handler.postDelayed({ advanceStep() }, 1500)
        }
    }

    private fun advanceStep() {
        Log.d("MathGalaga", "Advancing from step $currentStep")
        isPausing = false
        currentStep++
        Log.d("MathGalaga", "To step $currentStep")
        if (currentStep >= messages.size) {
            controller.view.endCalibration()
            controller.switchState("level_transition")
        } else {
            setupCalibration()
        }
    }

    override fun update(delta: Double) {
        // No-op
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(Config.ColorSettings.BLACK)
        val msg = if (isPausing) {
            "Calibrated! Preparing next step..."
        } else {
            messages[currentStep]
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

// Note: No code changes were needed in this file for the requested improvements (explosions on player hits/crashes, respawn aura, bigger retro fonts). The bigger retro fonts from Config.kt will automatically apply to all text drawn in the states (e.g., HUD in PlayingState, messages in other states). Explosions and aura are handled and rendered via the ECS systems (CollisionSystem, LifespanSystem, RenderingSystem) during PlayingState.
