// MainActivity.kt (refactored for kiosk mode: full screen, immersive, no navigation)
package com.robocrops.mathgalaga

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/**
 * The main and only Activity for MathGalaga.
 * Hosts the GameView and manages its lifecycle.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        // Set immersive kiosk mode: hide system UI, navigation, full screen
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        Config.initSprites(this)
        gameView = GameView(this)
        setContentView(gameView)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (gameView.onGenericMotionEvent(ev)) return true
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Only forward key events that match the current action. This prevents
        // spurious "button down" events when releasing keys.
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (gameView.onKeyDown(event.keyCode, event)) return true
            KeyEvent.ACTION_UP -> if (gameView.onKeyUp(event.keyCode, event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        if (::gameView.isInitialized) gameView.pause()
    }

    override fun onResume() {
        super.onResume()
        if (::gameView.isInitialized) gameView.resume()
    }

    override fun onDestroy() {
        if (::gameView.isInitialized) {
            gameView.pause()       // stop any pending frame callbacks
        }
        Config.clearSprites()      // now it’s safe to recycle
        super.onDestroy()
    }
}
