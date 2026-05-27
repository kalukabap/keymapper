package com.example.ui.panels

import android.widget.*
import com.example.engine.*

class SwipeConfigActivity : BasePanelActivity() {

    private var startX = 200f
    private var startY = 1200f
    private var endX = 800f
    private var endY = 1200f
    private var duration = 300L
    private var repeatMode = false

    override fun getTitle() = "Swipe Configuration"

    override fun buildPanel(layout: LinearLayout) {
        addLabel(layout, "Start Point")
        addSlider(layout, "Start X", 0f, 1080f, startX) { startX = it }
        addSlider(layout, "Start Y", 0f, 2400f, startY) { startY = it }

        addSeparator(layout)
        addLabel(layout, "End Point")
        addSlider(layout, "End X", 0f, 1080f, endX) { endX = it }
        addSlider(layout, "End Y", 0f, 2400f, endY) { endY = it }

        addSeparator(layout)
        addLabel(layout, "Timing")
        addSlider(layout, "Duration (ms)", 50f, 2000f, duration.toFloat()) { duration = it.toLong() }

        addSeparator(layout)
        addToggle(layout, "Repeat while held", repeatMode) { repeatMode = it }
    }

    override fun onSave() {
        Toast.makeText(this, "Swipe config saved", Toast.LENGTH_SHORT).show().show()
    }
}
