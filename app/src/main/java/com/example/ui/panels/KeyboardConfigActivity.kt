package com.example.ui.panels

import android.widget.*
import com.example.engine.*

class KeyboardConfigActivity : BasePanelActivity() {

    override fun getPanelTitle() = "Keyboard Binding"

    override fun buildPanel(layout: LinearLayout) {
        addLabel(layout, "Key Bindings")
        addLabel(layout, "Press a key to bind, then select action type.", 12f)

        val bindBtn = Button(this).apply {
            text = "Capture Key Binding"
            setOnClickListener {
                // TODO: open key capture dialog
            }
        }
        layout.addView(bindBtn)

        addSeparator(layout)
        addLabel(layout, "Options")
        addToggle(layout,
            "Repeat Suppression", true) {}
    }

    override fun onSave() {
        Toast.makeText(this, "Keyboard config saved", Toast.LENGTH_SHORT).show()
    }
}
