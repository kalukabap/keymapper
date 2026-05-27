package com.example.ui.panels

import android.widget.*
import com.example.engine.*

class SettingsActivity : BasePanelActivity() {

    private var touchPaths = false
    private var pointerVisible = true
    private var pointerSize = 24
    private var pollingRate = 0
    private var debugLogging = false

    override fun getPanelTitle() = "Settings"

    override fun buildPanel(layout: LinearLayout) {
        addLabel(layout, "Display")
        addToggle(layout, "Show Touch Paths", touchPaths) { touchPaths = it }
        addToggle(layout, "Mouse Pointer Visible", pointerVisible) { pointerVisible = it }
        addSlider(layout, "Pointer Size (dp)", 8f, 64f, pointerSize.toFloat()) { pointerSize = it.toInt() }

        addSeparator(layout)
        addLabel(layout, "Performance")
        val rates = listOf("Uncapped", "500 Hz", "250 Hz", "125 Hz")
        val rateGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        rates.forEachIndexed { i, rate ->
            rateGroup.addView(RadioButton(this).apply {
                text = rate
                setTextColor(0xFFCCCCCC.toInt())
                isChecked = i == 0
            })
        }
        layout.addView(rateGroup)

        addSeparator(layout)
        addLabel(layout, "Advanced")
        addToggle(layout, "Debug Logging", debugLogging) { debugLogging = it }
    }

    override fun onSave() {
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }
}
