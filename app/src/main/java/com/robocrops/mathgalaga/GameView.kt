package com.robocrops.mathgalaga

import android.content.Context
import android.graphics.Canvas
import android.hardware.input.InputManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.HandlerThread
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.util.Log
import android.view.Choreographer
import android.os.Handler

/**
 * The main view for MathGalaga.
 * Handles the game loop, drawing, sounds, and input.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, InputManager.InputDeviceListener {

    // Game control
    @Volatile private var running = false

    // Game controller (handles ECS, state, game logic)
    private val controller: GameController

    // Sounds
    private val audioThread = HandlerThread("AudioDecode").apply { start() }
    private val audioHandler = Handler(audioThread.looper)
    private lateinit var soundPool: SoundPool
    private var shootSoundId: Int = 0
    private var hitSoundId: Int = 0
    private var explosionSoundId: Int = 0

    private val joystickX = FloatArray(2)
    private val joystickY = FloatArray(2)
    private val firePressed = BooleanArray(2)

    // Debounce logic for fire (avoid firing too fast)
    private val fireButtonWasPressed = BooleanArray(2)

    val playerJoystickMap = mutableMapOf<Int, Int>() // deviceId to playerIndex (0 or 1)

    // Calibration mode
    var calibratingPlayer: Int = -1
        private set
    private var onCalibrationDetected: ((deviceId: Int, player: Int, isFire: Boolean) -> Unit)? = null
    private val detectedDevices = mutableSetOf<Int>() // To prevent re-detection of the same device

    // Choreographer for vsync-aware rendering
    private val choreographer: Choreographer = Choreographer.getInstance()
    private var lastFrameTime: Long = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running || !holder.surface.isValid) {
                if (running) choreographer.postFrameCallback(this)
                return
            }

            val deltaMs: Double
            if (lastFrameTime == 0L) {
                deltaMs = 16.0 // Assume 60 FPS for first frame
            } else {
                val deltaNs = frameTimeNanos - lastFrameTime
                deltaMs = deltaNs / 1_000_000.0
            }
            lastFrameTime = frameTimeNanos

            val canvas: Canvas? = holder.lockCanvas()
            if (canvas != null) {
                try {
                    synchronized(holder) {
                        val deltaFactor = deltaMs / 16.666

                        // Handle joystick input for each player
                        for (player in 0..1) {
                            val effectiveX: Float = (joystickX[player] * deltaFactor).toFloat()
                            val effectiveY: Float = (joystickY[player] * deltaFactor).toFloat()
                            val shouldFire = firePressed[player] && !fireButtonWasPressed[player]
                            controller.handleJoystick(player, effectiveX, effectiveY, shouldFire)
                            fireButtonWasPressed[player] = firePressed[player]
                        }

                        // Performance logging
                        val startUpdate = System.nanoTime()
                        controller.update(deltaMs)
                        val updateTime = (System.nanoTime() - startUpdate) / 1_000_000.0
                        Log.d("Perf", "Update: $updateTime ms")

                        val startDraw = System.nanoTime()
                        controller.draw(canvas)
                        val drawTime = (System.nanoTime() - startDraw) / 1_000_000.0
                        Log.d("Perf", "Draw: $drawTime ms")
                    }
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }

            if (running) choreographer.postFrameCallback(this)
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            && event.action == MotionEvent.ACTION_MOVE) {
            val deviceId = event.deviceId
            if (calibratingPlayer != -1 && !detectedDevices.contains(deviceId)) {
                // Detect during calibration if movement is significant
                if (Math.abs(event.getAxisValue(MotionEvent.AXIS_X)) > 0.1f || Math.abs(event.getAxisValue(MotionEvent.AXIS_Y)) > 0.1f) {
                    onCalibrationDetected?.invoke(deviceId, calibratingPlayer, false)
                    detectedDevices.add(deviceId)
                    return true
                }
            }
            val player = playerJoystickMap[deviceId] ?: return super.onGenericMotionEvent(event)
            // Input debouncing: apply deadzone
            joystickX[player] = if (Math.abs(event.getAxisValue(MotionEvent.AXIS_X)) > 0.1f) -event.getAxisValue(MotionEvent.AXIS_X) else 0f
            joystickY[player] = if (Math.abs(event.getAxisValue(MotionEvent.AXIS_Y)) > 0.1f) -event.getAxisValue(MotionEvent.AXIS_Y) else 0f
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val deviceId = event.deviceId
        if (calibratingPlayer != -1 && keyCode == KeyEvent.KEYCODE_BUTTON_11 && !detectedDevices.contains(deviceId)) {
            onCalibrationDetected?.invoke(deviceId, calibratingPlayer, true)
            detectedDevices.add(deviceId)
            return true
        }
        val player = playerJoystickMap[deviceId] ?: return super.onKeyDown(keyCode, event)
        if (keyCode == KeyEvent.KEYCODE_BUTTON_11) { // 198
            firePressed[player] = true
            Log.d("MathGalaga", "Joystick button DOWN detected for player $player! (keyCode $keyCode, source: ${event.source})")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val player = playerJoystickMap[event.deviceId] ?: return super.onKeyUp(keyCode, event)
        if (keyCode == KeyEvent.KEYCODE_BUTTON_11) { // 198
            firePressed[player] = false
            Log.d("MathGalaga", "Joystick button UP detected for player $player! (keyCode $keyCode, source: ${event.source})")
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    init {
        holder.addCallback(this)

        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()

        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(this, null)

        // Scan existing devices (optional fallback assignment)
        for (deviceId in inputManager.inputDeviceIds) {
            onInputDeviceAdded(deviceId)
        }

        controller = GameController(context, this)
        initSounds()
    }

    fun startCalibration(onDetected: (deviceId: Int, player: Int, isFire: Boolean) -> Unit) {
        onCalibrationDetected = onDetected
        calibratingPlayer = 0 // Start with player 1
        detectedDevices.clear() // Clear for new calibration
    }

    fun calibrateNextPlayer() {
        calibratingPlayer = 1 // Switch to player 2
    }

    fun endCalibration() {
        calibratingPlayer = -1
        onCalibrationDetected = null
        detectedDevices.clear()
    }

    fun setCalibratingPlayer(player: Int) {
        calibratingPlayer = player
    }

    /**
     * Loads all game sounds.
     */
    private fun initSounds() {
        val maxStreams = 5
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(maxStreams)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { pool, sampleId, status ->
            if (status != 0) {
                Log.e("MathGalaga", "Failed to load sound ID $sampleId (status: $status)")
            } else {
                Log.d("MathGalaga", "Sound ID $sampleId loaded successfully")
            }
        }

        shootSoundId = soundPool.load(context, R.raw.shoot, 1)
        hitSoundId = soundPool.load(context, R.raw.hit, 1)
        explosionSoundId = soundPool.load(context, R.raw.explosion, 1)
    }

    // Sound trigger functions, used by GameController and systems
    fun playShootSound() = soundPool.play(shootSoundId, 1f, 1f, 1, 0, 1f)
    fun playHitSound() = soundPool.play(hitSoundId, 1f, 1f, 1, 0, 1f)
    fun playExplosionSound() = soundPool.play(explosionSoundId, 1f, 1f, 1, 0, 1f)

    /**
     * Called when the SurfaceView is ready to draw.
     * Sets screen dimensions and loads sprites before starting the game thread.
     */
    override fun surfaceCreated(holder: SurfaceHolder) {
        Config.ScreenSettings.WIDTH = width
        Config.ScreenSettings.HEIGHT = height
        Config.initSprites(context)
        startGame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Optional: handle screen resize here (rare for a game)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pause()
        soundPool.release()
        audioThread.quitSafely()
    }

    /**
     * Starts the main game loop.
     */
    private fun startGame() {
        if (running) return
        running = true
        lastFrameTime = 0L
        choreographer.postFrameCallback(frameCallback)
    }

    /**
     * Safely pauses the game loop.
     */
    fun pause() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    /**
     * Resumes or restarts the game loop.
     */
    fun resume() {
        startGame()
    }

    /**
     * Forwards all touch events to the game controller.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        controller.handleTouch(event)
        return true
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        val device = InputDevice.getDevice(deviceId) ?: return
        if ((device.sources and InputDevice.SOURCE_JOYSTICK != 0) && device.name.contains("Joystick")) {
            if (playerJoystickMap.size < 2) {
                val player = playerJoystickMap.size
                playerJoystickMap[deviceId] = player
                Log.d("MathGalaga", "Fallback assigned joystick device $deviceId (${device.name}) to player $player")
            }
        }
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        playerJoystickMap.remove(deviceId)
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        // No-op for now
    }
}
