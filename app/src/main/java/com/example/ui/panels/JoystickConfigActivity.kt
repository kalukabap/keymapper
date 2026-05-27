package com.example.ui.panels

import android.widget.*
import com.example.engine.*

class JoystickConfigActivity : BasePanelActivity() {

    private var centerX = 200f
    private var centerY = 600f
    private var radius = 120f
    private var deadZone = 0.15f
    private var sensitivity = 1.0f
    private var invertX = false
    private var invertY = false

    override fun getPanelTitle() = "Joystick Configuration"

    override fun buildPanel(layout: LinearLayout) {
        addLabel(layout, "Position")
        addSlider(layout, "Center X", 0f, 1080f, centerX) { centerX = it }
        addSlider(layout, "Center Y", 0f, 2400f, centerY) { centerY = it }

        addSeparator(layout)
        addLabel(layout, "Behaviour")
        addSlider(layout, "Radius", 50f, 300f, radius) { radius = it }
        addSlider(layout, "Dead Zone", 0f, 0.5f, deadZone) { deadZone = it }
        addSlider(layout, "Sensitivity", 0.1f, 3.0f, sensitivity) { sensitivity = it }

        addSeparator(layout)
        addToggle(layout, "Invert X Axis", invertX) { invertX = it }
        addToggle(layout, "Invert Y Axis", invertY) { invertY = it }
    }

    override fun onSave() {
        Toast.makeText(this, "Joystick config saved", Toast.LENGTH_SHORT).show()
    }
}
