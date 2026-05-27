package com.example.ui.panels

import android.widget.*
import com.example.engine.*

class KeymapConfigActivity : BasePanelActivity() {

    override fun getTitle() = "Profile Manager"

    override fun buildPanel(layout: LinearLayout) {
        addLabel(layout, "Profiles")
        addLabel(layout, "Select a profile to edit or create new.", 12f)

        val newBtn = Button(this).apply {
            text = "New Profile"
            setOnClickListener { /* TODO */ }
        }
        layout.addView(newBtn)

        val importBtn = Button(this).apply {
            text = "Import Profile"
            setOnClickListener { /* TODO */ }
        }
        layout.addView(importBtn)

        val exportBtn = Button(this).apply {
            text = "Export Profile"
            setOnClickListener { /* TODO */ }
        }
        layout.addView(exportBtn)
    }

    override fun onSave() {
        Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show().show()
    }
}
