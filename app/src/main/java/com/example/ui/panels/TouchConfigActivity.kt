package com.example.ui.panels

import android.widget.*
import com.example.engine.*

class TouchConfigActivity : BasePanelActivity() {

    private var touchMode = "TAP"
    private var holdDuration = 200L
    private var releaseDelay = 0L
    private var targetX = 540f
    private var targetY = 960f

    override fun getTitle() = "Touch Configuration"

    override fun buildPanel(layout: LinearLayout) {
        addLabel(layout, "Touch Mode")
        val modes = listOf("TAP", "HOLD", "DOWN_UP", "LONG_PRESS")
        val modeGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        modes.forEach { mode ->
            modeGroup.addView(RadioButton(this@TouchConfigActivity).apply {
                text = mode
                setTextColor(0xFFCCCCCC.toInt())
                isChecked = mode == touchMode
                setOnCheckedChangeListener { _, checked -> if (checked) touchMode = mode }
            })
        }
        layout.addView(modeGroup)

        addSeparator(layout)
        addLabel(layout, "Timing")
        addSlider(layout, "Hold Duration (ms)", 50f, 2000f, holdDuration.toFloat()) {
            holdDuration = it.toLong()
        }
        addSlider(layout, "Release Delay (ms)", 0f, 1000f, releaseDelay.toFloat()) {
            releaseDelay = it.toLong()
        }

        addSeparator(layout)
        addLabel(layout, "Target Position")
        addSlider(layout, "X Position", 0f, 1080f, targetX) { targetX = it }
        addSlider(layout, "Y Position", 0f, 2400f, targetY) { targetY = it }
    }

    override fun onSave() {
        // Save to profile via repository
        Toast.makeText(this, "Touch config saved", Toast.LENGTH_SHORT).show()
    }
}
