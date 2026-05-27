package com.example.ui.panels

import android.widget.*
import com.example.engine.*

class ScrollConfigActivity : BasePanelActivity() {

    private var sensitivity = 1.0f
    private var repeatRate = 50L
    private var smoothScroll = false

    override fun getPanelTitle() = "Scroll Configuration"

    override fun buildPanel(layout: LinearLayout) {
        addLabel(layout, "Scroll Settings")
        addSlider(layout, "Sensitivity", 0.1f, 5.0f, sensitivity) { sensitivity = it }
        addSlider(layout, "Repeat Rate (ms)", 10f, 200f, repeatRate.toFloat()) { repeatRate = it.toLong() }
        addSeparator(layout)
        addToggle(layout, "Smooth Scroll", smoothScroll) { smoothScroll = it }
    }

    override fun onSave() {
        Toast.makeText(this, "Scroll config saved", Toast.LENGTH_SHORT).show()
    }
}
