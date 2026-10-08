package io.github.xtratter.cathunt

import android.content.Context
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Game settings, persisted in SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var mice by flag("mice")
    var roach by flag("roach")
    var rope by flag("rope")
    var butterfly by flag("butterfly")
    var laser by flag("laser")
    var fish by flag("fish")
    var bird by flag("bird")
    var fly by flag("fly")
    var ladybug by flag("ladybug")
    var lizard by flag("lizard")
    var firefly by flag("firefly")
    /** Rotate a few critters at a time so the cat does not get bored. */
    var variety by flag("variety")
    /** Boxes and pots to hide under. */
    var covers by flag("covers")

    var lure by flag("lure")
    var flash by flag("flash")
    var showScore by flag("show_score")

    /** Multiplier for how fast critters move, 0.5..2. */
    var speed by number("speed", 1f)
    /** Multiplier for critter size, 0.6..1.6. */
    var size by number("size", 1f)
    /** Play timer in minutes, 0 = off. */
    var playMinutes by number("play_minutes", 10f)
    /** Sound volume, 0..1. */
    var volume by number("volume", 1f)

    /** Changes when the set of critters must be rebuilt. */
    fun rosterKey() = listOf(mice, roach, rope, butterfly, laser, fish, bird, fly, ladybug, lizard, firefly, variety, covers, size).joinToString()

    private fun flag(key: String, default: Boolean = true) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getBoolean(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
            prefs.edit().putBoolean(key, value).apply()
    }

    private fun number(key: String, default: Float) = object : ReadWriteProperty<Any?, Float> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getFloat(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) =
            prefs.edit().putFloat(key, value).apply()
    }
}

/** The kit theme: the game's dark teal with the icon's amber accent. */
object Theme {
    val cat = io.github.xtratter.uikit.M3.Custom(
        primary = 0xFFFFC107.toInt(), secondary = 0xFFE0C98A.toInt(), tertiary = 0xFF7FD6C8.toInt(),
        base = 0xFF14222A.toInt(), surface = 0xFF1B2A34.toInt(),
    )
}
