package com.example.ui.panels

import android.widget.*
import com.example.engine.*

class DiagnosticsActivity : BasePanelActivity() {

    override fun getTitle() = "Diagnostics"

    override fun buildPanel(layout: LinearLayout) {
        addLabel(layout, "Runtime Status")
        addLabel(layout, "Shizuku: Checking...", 12f)
        addLabel(layout, "Engine: Idle", 12f)
        addLabel(layout, "Profile: None", 12f)
        addLabel(layout, "Touch Pointers: 0", 12f)

        addSeparator(layout)
        addLabel(layout, "Event Log")
        addLabel(layout, "Events will appear here when runtime is active.", 12f)
    }

    override fun onSave() {
        finish()
    }
}
