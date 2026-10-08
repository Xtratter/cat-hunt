package io.github.xtratter.cathunt

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.xtratter.uikit.EdgeBlur
import io.github.xtratter.uikit.Haptics
import io.github.xtratter.uikit.M3
import io.github.xtratter.uikit.M3Surface
import io.github.xtratter.uikit.M3Widgets
import kotlin.reflect.KMutableProperty0

/** The settings panel, built from android-ui-kit widgets. */
class SettingsPanel(
    private val ctx: Context,
    private val settings: Settings,
    private val onVolume: (Float) -> Unit,
    private val onVolumeSet: () -> Unit,
    private val onReset: () -> Unit,
    private val onDone: () -> Unit,
) {
    private val dp = ctx.resources.displayMetrics.density
    private fun px(v: Float) = (v * dp).toInt()

    fun build(): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20f), px(20f), px(20f), px(16f))
        }
        col.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.settings)
            textSize = 22f; typeface = M3.bold; setTextColor(M3.TEXT)
        })

        col.section(R.string.section_critters)
        col.toggle(R.string.critter_mice, settings::mice)
        col.toggle(R.string.critter_roach, settings::roach)
        col.toggle(R.string.critter_rope, settings::rope)
        col.toggle(R.string.critter_butterfly, settings::butterfly)
        col.toggle(R.string.critter_fish, settings::fish)
        col.toggle(R.string.critter_bird, settings::bird)
        col.toggle(R.string.critter_fly, settings::fly)
        col.toggle(R.string.critter_ladybug, settings::ladybug)
        col.toggle(R.string.critter_lizard, settings::lizard)
        col.toggle(R.string.critter_firefly, settings::firefly)
        col.toggle(R.string.critter_laser, settings::laser)
        col.toggle(R.string.variety, settings::variety)
        col.toggle(R.string.covers, settings::covers)

        col.section(R.string.section_game)
        col.choice(R.string.speed_title, settings::speed,
            listOf("0.5×" to 0.5f, "1×" to 1f, "1.5×" to 1.5f, "2×" to 2f))
        col.choice(R.string.size_title, settings::size,
            listOf(ctx.getString(R.string.size_s) to 0.7f, ctx.getString(R.string.size_m) to 1f,
                ctx.getString(R.string.size_l) to 1.4f))

        col.section(R.string.section_effects)
        col.choice(R.string.timer_title, settings::playMinutes,
            listOf(ctx.getString(R.string.timer_off) to 0f, ctx.getString(R.string.timer_min, 5) to 5f,
                ctx.getString(R.string.timer_min, 10) to 10f, ctx.getString(R.string.timer_min, 15) to 15f))
        col.choice(R.string.volume_title, settings::volume,
            listOf(ctx.getString(R.string.volume_off) to 0f, "30%" to 0.3f, "60%" to 0.6f, "100%" to 1f),
            onChanged = { onVolume(it); onVolumeSet() })
        col.toggle(R.string.lure, settings::lure)
        col.toggle(R.string.flash, settings::flash)
        col.toggle(R.string.show_score, settings::showScore)

        val buttons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(M3Widgets.button(ctx, ctx.getString(R.string.reset_score), M3Widgets.ButtonKind.TONAL) { onReset() },
            LinearLayout.LayoutParams(0, px(48f), 1f).apply { marginEnd = px(8f) })
        buttons.addView(M3Widgets.button(ctx, ctx.getString(R.string.done), M3Widgets.ButtonKind.FILLED) { onDone() },
            LinearLayout.LayoutParams(0, px(48f), 1f))
        col.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(18f) })

        val version = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        col.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.version, version)
            textSize = 12f; setTextColor(M3.TEXT3); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8f) })

        val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; addView(col) }
        val frame = android.widget.FrameLayout(ctx).apply {
            background = M3Surface(ctx, 28f, M3.dialogSolid)
            clipToOutline = true
            addView(scroll, android.widget.FrameLayout.LayoutParams(-1, -1))
        }
        EdgeBlur.wrap(scroll, 0f, M3.withAlpha(M3.base, 0.55f), M3.withAlpha(M3.base, 0.35f))
        return frame
    }

    private fun LinearLayout.section(title: Int) = addView(TextView(ctx).apply {
        text = ctx.getString(title)
        textSize = 14f; typeface = M3.bold; setTextColor(M3.primary)
    }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(18f); bottomMargin = px(4f) })

    private fun LinearLayout.toggle(title: Int, prop: KMutableProperty0<Boolean>) =
        addView(M3Widgets.switchRow(ctx, ctx.getString(title), prop.get()) { prop.set(it) })

    /** A row of chips acting as a segmented choice; the stored value snaps to the nearest option. */
    private fun LinearLayout.choice(
        title: Int,
        prop: KMutableProperty0<Float>,
        options: List<Pair<String, Float>>,
        onChanged: (Float) -> Unit = {},
    ) {
        addView(TextView(ctx).apply {
            text = ctx.getString(title); textSize = 16f; setTextColor(M3.TEXT)
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(10f); bottomMargin = px(6f) })
        val row = LinearLayout(ctx)
        addView(row, LinearLayout.LayoutParams(-1, px(40f)))
        var current = options.minByOrNull { kotlin.math.abs(it.second - prop.get()) }!!.second
        fun fill() {
            row.removeAllViews()
            for ((label, value) in options) {
                val chip = M3Widgets.chip(ctx, label, value == current) {
                    current = value; prop.set(value); onChanged(value); fill()
                }
                // four chips share a narrow panel: keep "✓ label" on one line
                chip.setPadding(px(4f), 0, px(4f), 0)
                chip.isSingleLine = true
                row.addView(chip, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = px(6f) })
            }
        }
        fill()
    }
}
