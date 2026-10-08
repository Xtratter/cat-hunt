package io.github.xtratter.cathunt

import android.app.Activity
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import io.github.xtratter.uikit.Expressive
import io.github.xtratter.uikit.Haptics
import io.github.xtratter.uikit.M3

class MainActivity : Activity() {
    private lateinit var settings: Settings
    private lateinit var sounds: Sounds
    private lateinit var game: GameView
    private lateinit var gear: View
    private lateinit var panel: View
    private var lastBack = 0L
    private var rosterOnOpen = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        volumeControlStream = AudioManager.STREAM_MUSIC
        M3.apply(this, M3.Mode.DARK, translucent = false, custom = Theme.cat)
        Haptics.init(this, getSharedPreferences("prefs", MODE_PRIVATE))
        Haptics.onTouch = { v, e -> Expressive.morph(v, e) }
        settings = Settings(this)
        sounds = Sounds(this).apply { volume = settings.volume }
        game = GameView(this, sounds, settings)
        setContentView(R.layout.activity_main)
        findViewById<FrameLayout>(R.id.game_container).addView(game)
        setupSettings()
    }

    /** The gear needs a long press so a cat's paw can't open the menu by accident. */
    private fun setupSettings() {
        gear = findViewById(R.id.gear)
        panel = findViewById(R.id.settings_panel)
        gear.setOnClickListener { Toast.makeText(this, R.string.hold_for_settings, Toast.LENGTH_SHORT).show() }
        gear.setOnLongClickListener { Haptics.play(Haptics.Kind.OPEN); openSettings(); true }

        Haptics.onClick(gear, Haptics.Kind.TICK)
        panel.setOnClickListener { closeSettings() }
        val width = (360 * resources.displayMetrics.density).toInt()
        val margin = (12 * resources.displayMetrics.density).toInt()
        (panel as FrameLayout).addView(SettingsPanel(
            this, settings,
            onVolume = { sounds.volume = it },
            onVolumeSet = { sounds.play(sounds.squeak) },
            onReset = {
                game.resetScore()
                Toast.makeText(this, R.string.score_reset, Toast.LENGTH_SHORT).show()
            },
            onDone = { closeSettings() },
        ).build().apply { isClickable = true }, FrameLayout.LayoutParams(width, -1, Gravity.TOP or Gravity.END).apply {
            setMargins(margin, margin, margin, margin)
        })
    }

    private fun openSettings() {
        rosterOnOpen = settings.rosterKey()
        game.frozen = true
        panel.visibility = View.VISIBLE
        gear.visibility = View.GONE
    }

    private fun closeSettings() {
        panel.visibility = View.GONE
        gear.visibility = View.VISIBLE
        if (settings.rosterKey() != rosterOnOpen) game.rebuild()
        game.frozen = false
        hideSystemUi()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        game.resume()
    }

    override fun onPause() {
        game.pause()
        super.onPause()
    }

    override fun onDestroy() {
        sounds.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Only needed on Android 11-14; Android 15+ is edge-to-edge anyway.
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    /** A cat's paw can easily hit "Back", so exiting requires a double press. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (panel.visibility == View.VISIBLE) {
            closeSettings()
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastBack < 2000) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        } else {
            lastBack = now
            Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).show()
        }
    }
}
