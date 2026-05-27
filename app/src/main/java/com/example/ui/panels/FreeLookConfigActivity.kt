package com.example.ui.panels

import android.widget.*
import com.example.engine.*

class FreeLookConfigActivity : BasePanelActivity() {

    private var sensitivity = 1.0f
    private var resetOnEdge = true
    private var hotkey = 0

    override fun getPanelTitle() = "Free Look Configuration"

    override fun buildPanel(layout: LinearLayout) {
        addLabel(layout, "Sensitivity")
        addSlider(layout, "Look Sensitivity", 0.1f, 5.0f, sensitivity) { sensitivity = it }

        addSeparator(layout)
        addToggle(layout, "Reset on Edge", resetOnEdge) { resetOnEdge = it }

        addSeparator(layout)
        addLabel(layout, "Hotkey: ${if (hotkey == 0) "Not Set" else hotkey}")
        val bindBtn = Button(this).apply {
            text = "Bind Hotkey"
            setOnClickListener {
                // TODO: open key capture dialog
            }
        }
        layout.addView(bindBtn)
    }

    override fun onSave() {
        Toast.makeText(this, "Free Look config saved", Toast.LENGTH_SHORT).show()
    }
}
