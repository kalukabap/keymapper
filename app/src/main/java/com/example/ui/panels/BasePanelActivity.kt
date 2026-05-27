package com.example.ui.panels

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.example.engine.RuntimeEngine
import com.example.engine.RuntimeEngine.EngineState

/**
 * Base class for all feature configuration panels.
 * Uses plain Activity (no appcompat) with programmatic UI.
 */
abstract class BasePanelActivity : Activity() {

    protected lateinit var engine: RuntimeEngine

    abstract fun getTitle(): String
    abstract fun buildPanel(layout: LinearLayout)
    abstract fun onSave()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window.setBackgroundDrawableResource(android.R.color.transparent)

        engine = RuntimeEngine.getInstance(this)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xE61A1A2E.toInt())
            setPadding(24, 24, 24, 24)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Title
        root.addView(TextView(this).apply {
            text = getTitle()
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        })

        // Runtime status
        if (engine.currentState != EngineState.IDLE && engine.currentState != EngineState.PERMISSION_MISSING) {
            root.addView(TextView(this).apply {
                text = "● Runtime: ${engine.currentState.name}"
                setTextColor(if (engine.currentState == EngineState.READY || engine.currentState == EngineState.AIM_MODE) 0xFF00E676.toInt() else 0xFFFFAB00.toInt())
                textSize = 12f
                setPadding(0, 0, 0, 12)
            })
        }

        // Build feature-specific controls
        buildPanel(root)

        // Save button
        root.addView(Button(this).apply {
            text = "Save"
            setOnClickListener {
                onSave()
                finish()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    // ── HELPER WIDGETS ──

    protected fun addLabel(parent: LinearLayout, text: String, sizeSp: Float = 14f) {
        parent.addView(TextView(this).apply {
            this.text = text
            setTextColor(0xFFCCCCCC.toInt())
            textSize = sizeSp
            setPadding(0, 8, 0, 4)
        })
    }

    protected fun addSeparator(parent: LinearLayout) {
        parent.addView(View(this).apply {
            setBackgroundColor(0x33FFFFFF)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 2
            ).apply { topMargin = 12; bottomMargin = 8 }
        })
    }

    protected fun addSlider(
        parent: LinearLayout,
        label: String,
        min: Float,
        max: Float,
        current: Float,
        onChange: (Float) -> Unit
    ) {
        parent.addView(TextView(this).apply {
            this.text = "$label: %.1f".format(current)
            setTextColor(0xFFBBBBBB.toInt())
            textSize = 12f
            setPadding(0, 6, 0, 2)
        })
        parent.addView(SeekBar(this).apply {
            this.max = 1000
            progress = ((current - min) / (max - min) * 1000).toInt().coerceIn(0, 1000)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, prog: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val value = min + (prog / 1000f) * (max - min)
                        onChange(value)
                        (parent.getChildAt(parent.indexOfChild(sb) - 1) as? TextView)?.text =
                            "$label: %.1f".format(value)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        })
    }

    protected fun addToggle(
        parent: LinearLayout,
        label: String,
        current: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        parent.addView(Switch(this).apply {
            text = label
            isChecked = current
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 13f
            setPadding(0, 6, 0, 6)
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        })
    }
}
