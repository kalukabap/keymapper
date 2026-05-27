package com.example.engine

import android.util.Log
import com.example.shizuku.ShizukuHiddenApi
import kotlinx.coroutines.*

/**
 * Scroll event injector.
 * Injects scroll events via Shizuku InputManager or fallback.
 */
class ScrollInjector(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ScrollInjector"
    }

    // ── CONFIG ──

    data class Config(
        val sensitivity: Float = 1.0f,
        val repeatRateMs: Long = 50L,
        val smoothScroll: Boolean = false,
        val direction: Direction = Direction.VERTICAL
    )

    enum class Direction { VERTICAL, HORIZONTAL }

    var config = Config()
        private set

    // ── STATE ──

    private var isScrolling = false
    private var scrollJob: Job? = null

    fun updateConfig(newConfig: Config) {
        config = newConfig
    }

    // ── SCROLL ACTIONS ──

    /**
     * Inject a single scroll event.
     * @param amount positive = scroll up/right, negative = scroll down/left
     */
    fun scrollOnce(amount: Int) {
        injectScroll(amount)
    }

    /**
     * Start continuous scrolling in a direction.
     * @param direction positive = up/right, negative = down/left
     */
    fun startContinuousScroll(direction: Int) {
        stopScrolling()
        isScrolling = true

        scrollJob = scope.launch {
            while (isScrolling && isActive) {
                injectScroll((direction * config.sensitivity).toInt().coerceAtLeast(1))
                delay(config.repeatRateMs)
            }
        }

        Log.d(TAG, "Started continuous scroll: direction=$direction")
    }

    /**
     * Stop continuous scrolling.
     */
    fun stopScrolling() {
        scrollJob?.cancel()
        scrollJob = null
        isScrolling = false
    }

    /**
     * Scroll by a specific amount with sensitivity applied.
     */
    fun scrollBy(amount: Int) {
        val adjusted = (amount * config.sensitivity).toInt()
        injectScroll(adjusted)
    }

    fun isCurrentlyScrolling(): Boolean = isScrolling

    // ── INJECTION ──

    private fun injectScroll(amount: Int) {
        try {
            if (ShizukuHiddenApi.isAvailable()) {
                // Use Shizuku to inject scroll MotionEvent
                // This would create a MotionEvent.ACTION_SCROLL with AXIS_VSCROLL
                ShizukuHiddenApi.injectScrollEvent(amount.toFloat())
            } else {
                // Fallback: no scroll injection without Shizuku
                Log.d(TAG, "Scroll (no Shizuku): $amount")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scroll injection failed", e)
        }
    }
}
