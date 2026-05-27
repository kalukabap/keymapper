package com.example.ui.panels

import android.widget.*
import com.example.engine.*

class MacroConfigActivity : BasePanelActivity() {

    private var loopCount = 0
    private var stopKeyCode = -1

    override fun getPanelTitle() = "Macro Editor"

    override fun buildPanel(layout: LinearLayout) {
        addLabel(layout, "Macro Settings")
        addSlider(layout, "Loop Count (0=once, -1=infinite)", -1f, 10f, loopCount.toFloat()) {
            loopCount = it.toInt()
        }

        addSeparator(layout)
        addLabel(layout, "Steps will be recorded from key presses.")
        addLabel(layout, "Press 'Record' to start, press keys, then 'Stop'.", 12f)

        val recordBtn = Button(this).apply {
            text = "Start Recording"
            setOnClickListener {
                // Toggle recording via MacroEngine
                text = if (text == "Start Recording") "Stop Recording" else "Start Recording"
            }
        }
        layout.addView(recordBtn)
    }

    override fun onSave() {
        Toast.makeText(this, "Macro saved", Toast.LENGTH_SHORT).show()
    }
}
