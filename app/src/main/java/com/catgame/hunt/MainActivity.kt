package com.catgame.hunt

import android.app.Activity
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty0

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
        gear.setOnLongClickListener { openSettings(); true }

        bindSwitch(R.id.sw_mice, settings::mice)
        bindSwitch(R.id.sw_roach, settings::roach)
        bindSwitch(R.id.sw_rope, settings::rope)
        bindSwitch(R.id.sw_butterfly, settings::butterfly)
        bindSwitch(R.id.sw_fish, settings::fish)
        bindSwitch(R.id.sw_laser, settings::laser)
        bindSwitch(R.id.sw_lure, settings::lure)
        bindSwitch(R.id.sw_flash, settings::flash)
        bindSwitch(R.id.sw_score, settings::showScore)

        bindSeek(R.id.seek_speed, R.id.label_speed, settings::speed, 0.5f, 2f, 0.1f) {
            getString(R.string.speed, it)
        }
        bindSeek(R.id.seek_size, R.id.label_size, settings::size, 0.6f, 1.6f, 0.1f) {
            getString(R.string.size, it)
        }
        bindSeek(R.id.seek_volume, R.id.label_volume, settings::volume, 0f, 1f, 0.1f,
            onChanged = { sounds.volume = it },
            onReleased = { sounds.play(sounds.squeak) },
        ) { getString(R.string.volume, (it * 100).roundToInt()) }

        findViewById<Button>(R.id.btn_reset).setOnClickListener {
            game.resetScore()
            Toast.makeText(this, R.string.score_reset, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btn_done).setOnClickListener { closeSettings() }
        findViewById<TextView>(R.id.version).text =
            getString(R.string.version, packageManager.getPackageInfo(packageName, 0).versionName)
    }

    private fun bindSwitch(id: Int, prop: KMutableProperty0<Boolean>) {
        findViewById<Switch>(id).apply {
            isChecked = prop.get()
            setOnCheckedChangeListener { _, checked -> prop.set(checked) }
        }
    }

    private fun bindSeek(
        seekId: Int,
        labelId: Int,
        prop: KMutableProperty0<Float>,
        min: Float,
        max: Float,
        step: Float,
        onChanged: (Float) -> Unit = {},
        onReleased: () -> Unit = {},
        label: (Float) -> String,
    ) {
        val labelView = findViewById<TextView>(labelId)
        val valueAt = { progress: Int -> min + progress * step }
        findViewById<SeekBar>(seekId).apply {
            this.max = ((max - min) / step).roundToInt()
            progress = ((prop.get() - min) / step).roundToInt()
            labelView.text = label(valueAt(progress))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val value = valueAt(progress)
                    prop.set(value)
                    labelView.text = label(value)
                    onChanged(value)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) = onReleased()
            })
        }
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
