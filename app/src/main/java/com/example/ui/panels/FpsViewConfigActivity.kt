package com.example.ui.panels

import android.widget.*
import com.example.engine.*

class FpsViewConfigActivity : BasePanelActivity() {

    private var sensitivityX = 1.0f
    private var sensitivityY = 1.0f
    private var resetOnEdge = true
    private var holdMode = true
    private var invertY = false

    override fun getTitle() = "FPS View Configuration"

    override fun buildPanel(layout: LinearLayout) {
        addLabel(layout, "Sensitivity")
        addSlider(layout, "Horizontal", 0.1f, 5.0f, sensitivityX) { sensitivityX = it }
        addSlider(layout, "Vertical", 0.1f, 5.0f, sensitivityY) { sensitivityY = it }

        addSeparator(layout)
        addLabel(layout, "Behaviour")
        addToggle(layout, "Reset on Edge", resetOnEdge) { resetOnEdge = it }
        addToggle(layout, "Hold Mode (vs Toggle)", holdMode) { holdMode = it }
        addToggle(layout, "Invert Y Axis", invertY) { invertY = it }
    }

    override fun onSave() {
        Toast.makeText(this, "FPS View config saved", Toast.LENGTH_SHORT).show().show()
    }
}
