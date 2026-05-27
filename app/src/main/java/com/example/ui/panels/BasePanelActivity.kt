package com.example.ui.panels

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.ui.theme.HudTheme

/**
 * Base class for feature configuration panels.
 * Provides common layout structure: title bar, scrollable content, save button.
 */
abstract class BasePanelActivity : AppCompatActivity() {

    protected lateinit var contentLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Window setup — floating dialog style
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.CENTER)
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE616162A.toInt())
            setPadding(32, 32, 32, 32)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Title bar
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = getTitle()
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 20f
            setPadding(16, 8, 16, 8)
            setOnClickListener { finish() }
        }

        titleBar.addView(title)
        titleBar.addView(closeBtn)
        root.addView(titleBar)

        // Divider
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
            ).apply { topMargin = 16; bottomMargin = 16 }
            setBackgroundColor(0x33FFFFFF)
        }
        root.addView(divider)

        // Scrollable content
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1f }
        }

        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(contentLayout)
        root.addView(scrollView)

        // Build content
        buildPanel(contentLayout)

        // Save button
        val saveBtn = Button(this).apply {
            text = "Save"
            setOnClickListener {
                onSave()
                finish()
            }
        }
        root.addView(saveBtn)

        setContentView(root)
    }

    abstract fun getTitle(): String
    abstract fun buildPanel(layout: LinearLayout)
    abstract fun onSave()

    // ── HELPER WIDGETS ──

    protected fun addSlider(
        layout: LinearLayout,
        label: String,
        min: Float,
        max: Float,
        value: Float,
        onChange: (Float) -> Unit
    ): SeekBar {
        val labelView = TextView(this).apply {
            text = "$label: ${"%.2f".format(value)}"
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 14f
        }
        layout.addView(labelView)

        val slider = SeekBar(this).apply {
            this.max = ((max - min) * 100).toInt()
            progress = ((value - min) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = min + progress / 100f
                    labelView.text = "$label: ${"%.2f".format(v)}"
                    onChange(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        layout.addView(slider)
        return slider
    }

    protected fun addToggle(
        layout: LinearLayout,
        label: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): Switch {
        val toggle = Switch(this).apply {
            text = label
            setTextColor(0xFFCCCCCC.toInt())
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        }
        layout.addView(toggle)
        return toggle
    }

    protected fun addLabel(layout: LinearLayout, text: String, size: Float = 14f) {
        layout.addView(TextView(this).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            textSize = size
            setPadding(0, 16, 0, 8)
        })
    }

    protected fun addSeparator(layout: LinearLayout) {
        layout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
            ).apply { topMargin = 12; bottomMargin = 12 }
            setBackgroundColor(0x22FFFFFF)
        })
    }
}
