package com.example.ui.panels

import android.widget.*
import com.example.engine.*

class HelpActivity : BasePanelActivity() {

    override fun getPanelTitle() = "Help & Tutorial"

    override fun buildPanel(layout: LinearLayout) {
        addLabel(layout, "Getting Started")
        addLabel(layout, "1. Grant Shizuku permission when prompted", 12f)
        addLabel(layout, "2. Enable Accessibility Service as fallback", 12f)
        addLabel(layout, "3. Create or select a profile", 12f)
        addLabel(layout, "4. Add key mappings in the overlay editor", 12f)
        addLabel(layout, "5. Start the service and launch your game", 12f)

        addSeparator(layout)
        addLabel(layout, "Tools")
        addLabel(layout, "Touch: Map keys to tap/hold/swipe actions", 12f)
        addLabel(layout, "Swipe: Configure drag gestures with timing", 12f)
        addLabel(layout, "Scroll: Map keys to mouse scroll events", 12f)
        addLabel(layout, "Macro: Record and playback key sequences", 12f)
        addLabel(layout, "Joystick: Virtual movement pad from WASD/DPAD", 12f)
        addLabel(layout, "FPS View: Mouse-to-camera drag for FPS games", 12f)
        addLabel(layout, "Free Look: Toggle-based camera control", 12f)
        addLabel(layout, "Keyboard: Map keys with hold/toggle/chord modes", 12f)
    }

    override fun onSave() {
        finish()
    }
}
