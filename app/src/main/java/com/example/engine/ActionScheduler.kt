     1|package com.example.engine
     2|
     3|import android.graphics.PointF
     4|import android.util.Log
     5|import kotlinx.coroutines.*
     6|import java.util.concurrent.ConcurrentLinkedQueue
     7|import java.util.concurrent.atomic.AtomicBoolean
     8|
     9|/**
    10| * Timed action execution queue.
    11| * Handles tap, hold, swipe, scroll, and macro step execution
    12| * with proper timing, cancellation, and sequencing.
    13| */
    14|class ActionScheduler(
    15|    private val injector: PersistentInjector,
    16|    private val scope: CoroutineScope
    17|) {
    18|    companion object {
    19|        private const val TAG = "ActionScheduler"
    20|    }
    21|
    22|    // ── ACTION TYPES ──
    23|
    24|    sealed class Action {
    25|        data class Tap(
    26|            val x: Float, val y: Float,
    27|            val holdMs: Long = 50L
    28|        ) : Action()
    29|
    30|        data class Hold(
    31|            val x: Float, val y: Float,
    32|            val durationMs: Long = 500L
    33|        ) : Action()
    34|
    35|        data class Swipe(
    36|            val startX: Float, val startY: Float,
    37|            val endX: Float, val endY: Float,
    38|            val durationMs: Long = 300L,
    39|            val steps: Int = 20
    40|        ) : Action()
    41|
    42|        data class Drag(
    43|            val startX: Float, val startY: Float,
    44|            val endX: Float, val endY: Float,
    45|            val durationMs: Long = 500L
    46|        ) : Action()
    47|
    48|        data class Scroll(
    49|            val amount: Int, // positive = up, negative = down
    50|            val repeatCount: Int = 1,
    51|            val repeatDelayMs: Long = 50L
    52|        ) : Action()
    53|
    54|        data class Delay(
    55|            val durationMs: Long
    56|        ) : Action()
    57|
    58|        data class MultiTouch(
    59|            val points: List<PointF>,
    60|            val holdMs: Long = 100L
    61|        ) : Action()
    62|
    63|        // Hold key → action starts on key down, stops on key up
    64|        data class HoldKeyBound(
    65|            val x: Float, val y: Float,
    66|            val keyBinding: Int // keyCode that triggers this
    67|        ) : Action()
    68|    }
    69|
    70|    // ── STATE ──
    71|
    72|    private val actionQueue = ConcurrentLinkedQueue<Action>()
    73|    private val isRunning = AtomicBoolean(false)
    74|    private var currentJob: Job? = null
    75|    private val activeHoldPointers = mutableMapOf<Int, Int>() // actionId → pointerId
    76|
    77|    // ── EXECUTION ──
    78|
    79|    /**
    80|     * Queue an action for immediate execution.
    81|     */
    82|    fun execute(action: Action) {
    83|        actionQueue.offer(action)
    84|        if (!isRunning.get()) {
    85|            processNext()
    86|        }
    87|    }
    88|
    89|    /**
    90|     * Execute a sequence of actions in order.
    91|     */
    92|    fun executeSequence(actions: List<Action>) {
    93|        currentJob?.cancel()
    94|        currentJob = scope.launch {
    95|            isRunning.set(true)
    96|            for (action in actions) {
    97|                ensureActive()
    98|                executeAndWait(action)
    99|            }
   100|            isRunning.set(false)
   101|        }
   102|    }
   103|
   104|    /**
   105|     * Cancel all pending and running actions.
   106|     */
   107|    fun cancelAll() {
   108|        currentJob?.cancel()
   109|        currentJob = null
   110|        actionQueue.clear()
   111|        // Release all held pointers
   112|        activeHoldPointers.values.forEach { pid ->
   113|            injector.touchUp(pid)
   114|        }
   115|        activeHoldPointers.clear()
   116|        isRunning.set(false)
   117|    }
   118|
   119|    /**
   120|     * Start a hold action that lasts until stopHold() is called.
   121|     * Returns an ID for the hold session.
   122|     */
   123|    fun startHold(x: Float, y: Float): Int {
   124|        val pointerId = injector.touchDown(x, y)
   125|        val id = System.identityHashCode(pointerId)
   126|        activeHoldPointers[id] = pointerId
   127|        Log.d(TAG, "Started hold #$id at ($x, $y) pointer=$pointerId")
   128|        return id
   129|    }
   130|
   131|    /**
   132|     * Stop a hold action by ID.
   133|     */
   134|    fun stopHold(id: Int) {
   135|        val pointerId = activeHoldPointers.remove(id)
   136|        if (pointerId != null) {
   137|            injector.touchUp(pointerId)
   138|            Log.d(TAG, "Stopped hold #$id")
   139|        }
   140|    }
   141|
   142|    /**
   143|     * Start a swipe that can be cancelled.
   144|     * Returns an ID for the swipe session.
   145|     */
   146|    fun startSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Int {
   147|        val id = System.identityHashCode(startX.toInt() * 1000 + startY.toInt())
   148|        scope.launch {
   149|            val pointerId = injector.touchDown(startX, startY)
   150|            activeHoldPointers[id] = pointerId
   151|
   152|            val steps = (durationMs / 16).toInt().coerceAtLeast(1)
   153|            for (i in 1..steps) {
   154|                ensureActive()
   155|                val t = i.toFloat() / steps
   156|                val x = startX + (endX - startX) * t
   157|                val y = startY + (endY - startY) * t
   158|                injector.touchMove(pointerId, x, y)
   159|                delay(16)
   160|            }
   161|            // Don't auto-release — caller decides
   162|        }
   163|        return id
   164|    }
   165|
   166|    /**
   167|     * Check if an action is currently running.
   168|     */
   169|    fun isActive(): Boolean = isRunning.get()
   170|
   171|    // ── INTERNAL ──
   172|
   173|    private fun processNext() {
   174|        val action = actionQueue.poll() ?: return
   175|        currentJob = scope.launch {
   176|            isRunning.set(true)
   177|            executeAndWait(action)
   178|            isRunning.set(false)
   179|            if (actionQueue.isNotEmpty()) {
   180|                processNext()
   181|            }
   182|        }
   183|    }
   184|
   185|    private suspend fun executeAndWait(action: Action) {
   186|        when (action) {
   187|            is Action.Tap -> {
   188|                val pid = injector.touchDown(action.x, action.y)
   189|                delay(action.holdMs)
   190|                injector.touchUp(pid)
   191|            }
   192|
   193|            is Action.Hold -> {
   194|                val pid = injector.touchDown(action.x, action.y)
   195|                delay(action.durationMs)
   196|                injector.touchUp(pid)
   197|            }
   198|
   199|            is Action.Swipe -> {
   200|                val pid = injector.touchDown(action.startX, action.startY)
   201|                val steps = action.steps
   202|                for (i in 1..steps) {
   203|                    ensureActive()
   204|                    val t = i.toFloat() / steps
   205|                    val x = action.startX + (action.endX - action.startX) * t
   206|                    val y = action.startY + (action.endY - action.startY) * t
   207|                    injector.touchMove(pid, x, y)
   208|                    delay(action.durationMs / steps)
   209|                }
   210|                injector.touchUp(pid)
   211|            }
   212|
   213|            is Action.Drag -> {
   214|                val pid = injector.touchDown(action.startX, action.startY)
   215|                val steps = (action.durationMs / 16).toInt().coerceAtLeast(1)
   216|                for (i in 1..steps) {
   217|                    ensureActive()
   218|                    val t = i.toFloat() / steps
   219|                    val x = action.startX + (action.endX - action.startX) * t
   220|                    val y = action.startY + (action.endY - action.startY) * t
   221|                    injector.touchMove(pid, x, y)
   222|                    delay(16)
   223|                }
   224|                injector.touchUp(pid)
   225|            }
   226|
   227|            is Action.Scroll -> {
   228|                // Scroll injection via Shizuku or fallback
   229|                for (i in 0 until action.repeatCount) {
   230|                    ensureActive()
   231|                    injectScroll(action.amount)
   232|                    if (i < action.repeatCount - 1) {
   233|                        delay(action.repeatDelayMs)
   234|                    }
   235|                }
   236|            }
   237|
   238|            is Action.Delay -> {
   239|                delay(action.durationMs)
   240|            }
   241|
   242|            is Action.MultiTouch -> {
   243|                val pids = action.points.map { pt -> injector.touchDown(pt.x, pt.y) }
   244|                delay(action.holdMs)
   245|                pids.forEach { pid -> injector.touchUp(pid) }
   246|            }
   247|
   248|            is Action.HoldKeyBound -> {
   249|                // This is handled by the runtime engine, not here
   250|                // Just a placeholder for type completeness
   251|            }
   252|        }
   253|    }
   254|
   255|    /**
   256|     * Inject a scroll event.
   257|     * Uses Shizuku if available, otherwise logs.
   258|     */
   259|    private fun injectScroll(amount: Int) {
   260|        // TODO: Use ShizukuHiddenApi.injectInputEvent with MotionEvent for scroll
   261|        // For now, dispatch through the engine's scroll handler
   262|        Log.d(TAG, "Scroll: $amount")
   263|    }
   264|}
   265|