package com.chessfloat.mobile

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Python 内核的 Kotlin 封装。
 * 所有 Python 调用都在单线程池里串行执行（引擎分析是阻塞的），
 * 结果通过回调抛回主线程。
 */
object ChessCore {

    const val EMA_ALPHA = 0.35f
    private const val AUTO_ACCEPT_DELAY_MS = 320L

    private var module: PyObject? = null
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "chess-core").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    var cfg = Config()
    var state = GameState()
        private set
    var suggestion: Pair<Int, Int>? = null
        private set
    var winPct: Int? = null
        private set
    var evalText: String = "—"
        private set
    var depth: Int = 0
        private set
    var engineError: String? = null
        private set

    var onState: ((GameState) -> Unit)? = null
    var onAnalysis: ((evalText: String, winPct: Int?, depth: Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onSelectionChanged: ((Int?, Set<Int>) -> Unit)? = null
    var onPromotionNeeded: ((Int, Int) -> Unit)? = null

    private var selected: Int? = null
    private var legalTargets: Set<Int> = emptySet()
    private var winEma: Float? = null
    private var pendingAnalysis: Runnable? = null
    private var myWhite = true
    private var stateSeq = 0

    fun start(context: Context, enginePath: String, config: Config, onReady: (String) -> Unit) {
        cfg = config
        worker.execute {
            try {
                if (!Python.isStarted()) Python.start(AndroidPlatform(context))
                val m = Python.getInstance().getModule("bridge")
                module = m
                m.callAttr("configure", cfg.threads, cfg.hashMb)
                val res = JSONObject(m.callAttr("init_engine", enginePath).toString())
                val ok = res.optBoolean("ok")
                val name = res.optString("name")
                val err = res.optString("error")
                main.post {
                    if (ok) {
                        engineError = null
                        state = state.copy(engineReady = true, engineName = name)
                        onReady(name)
                    } else {
                        engineError = err
                        onError?.invoke(err)
                    }
                    pushState()
                }
            } catch (t: Throwable) {
                val msg = "${t.javaClass.simpleName}: ${t.message}"
                main.post {
                    engineError = msg
                    onError?.invoke(msg)
                }
            }
        }
    }

    fun applyConfig(config: Config) {
        val engineChanged = config.threads != cfg.threads || config.hashMb != cfg.hashMb
        cfg = config
        if (engineChanged) {
            worker.execute {
                try {
                    module?.callAttr("configure", cfg.threads, cfg.hashMb)
                } catch (_: Throwable) {
                }
            }
        }
        scheduleAnalysis()
    }

    fun shutdown() {
        worker.execute {
            try {
                module?.callAttr("shutdown")
            } catch (_: Throwable) {
            }
        }
        winEma = null
        selected = null
        legalTargets = emptySet()
        state = GameState()
    }

    fun newGame(isWhite: Boolean) = call("reset", isWhite) { json ->
        myWhite = isWhite
        resetAnalysis(clearPct = true)
        clearSelection()
        parseState(json)
        scheduleAnalysis()
    }

    fun flip() = call("flip") { json -> parseState(json) }

    fun undo() = call("undo") { json ->
        resetAnalysis()
        clearSelection()
        parseState(json)
        scheduleAnalysis()
    }

    fun redo() = call("redo") { json ->
        resetAnalysis()
        clearSelection()
        parseState(json)
        scheduleAnalysis()
    }

    fun jumpTo(ply: Int) = call("jump_to", ply) { json ->
        resetAnalysis()
        clearSelection()
        parseState(json)
        scheduleAnalysis()
    }

    fun tapSquare(square: Int) {
        val sel = selected
        if (sel == null) {
            if (canSelect(square)) select(square)
            return
        }
        if (square == sel) {
            clearSelection()
            return
        }
        if (legalTargets.contains(square)) {
            if (needsPromotion(sel, square)) {
                onPromotionNeeded?.invoke(sel, square)
                return
            }
            playMove(sel, square, 0)
            return
        }
        if (canSelect(square)) select(square) else clearSelection()
    }

    /** 采纳当前建议着法（对应电脑版的「采纳」按钮）。 */
    fun acceptSuggestion(): Boolean {
        val s = suggestion ?: return false
        if (!state.isMyTurn || state.isOver) return false
        playMove(s.first, s.second, 0)
        return true
    }

    fun playMove(from: Int, to: Int, promotion: Int) {        call("play", from, to, promotion) { json ->
            val obj = JSONObject(json)
            if (obj.optBoolean("ok")) {
                resetAnalysis()
                clearSelection()
                parseState(obj.getJSONObject("state").toString())
                scheduleAnalysis()
            } else {
                clearSelection()
            }
        }
    }

    private fun needsPromotion(from: Int, to: Int): Boolean {
        val piece = state.pieces[from] ?: return false
        if (piece.type != Pieces.PAWN) return false
        val rank = to / 8
        return rank == 0 || rank == 7
    }

    private fun canSelect(square: Int): Boolean {
        val piece = state.pieces[square] ?: return false
        return piece.isWhite == state.turnWhite
    }

    private fun select(square: Int) {
        selected = square
        legalTargets = emptySet()
        main.post { onSelectionChanged?.invoke(square, emptySet()) }
        worker.execute {
            try {
                val arr = JSONArray(module!!.callAttr("legal_targets", square).toString())
                val set = HashSet<Int>()
                for (i in 0 until arr.length()) set.add(arr.getInt(i))
                main.post {
                    if (selected == square) {
                        legalTargets = set
                        onSelectionChanged?.invoke(square, set)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun clearSelection() {
        selected = null
        legalTargets = emptySet()
        main.post { onSelectionChanged?.invoke(null, emptySet()) }
    }

    private fun scheduleAnalysis() {
        pendingAnalysis?.let { main.removeCallbacks(it) }
        val runnable = Runnable { runAnalysis() }
        pendingAnalysis = runnable
        main.postDelayed(runnable, cfg.debounceMs.toLong())
    }

    private fun runAnalysis() {
        if (!state.isMyTurn || state.isOver || !state.engineReady) return
        val seq = stateSeq
        val baseMs = cfg.analysisMs
        val adaptive = cfg.adaptive
        worker.execute {
            try {
                val json = module!!.callAttr("analyse", baseMs, adaptive).toString()
                val obj = JSONObject(json)
                if (!obj.optBoolean("ok")) return@execute
                val uci = obj.optString("uci").ifEmpty { null }
                val evalVal = obj.optString("evalText")
                val depthVal = obj.optInt("depth")
                val wdlArr = obj.optJSONArray("wdl")

                var pct: Int? = null
                if (wdlArr != null && wdlArr.length() == 3) {
                    val wins = wdlArr.getInt(0)
                    val draws = wdlArr.getInt(1)
                    val losses = wdlArr.getInt(2)
                    val raw = if (myWhite) {
                        (wins + draws / 2.0) / 10.0
                    } else {
                        (losses + draws / 2.0) / 10.0
                    }.toFloat()
                    val prev = winEma
                    winEma = if (prev == null) raw else EMA_ALPHA * raw + (1 - EMA_ALPHA) * prev
                    pct = Math.round(winEma!!)
                }

                if (uci != null && uci.length >= 4) {
                    suggestion = squareOf(uci.substring(0, 2))?.let { f ->
                        squareOf(uci.substring(2, 4))?.let { t -> Pair(f, t) }
                    }
                }
                if (pct != null) winPct = pct
                depth = depthVal
                evalText = evalVal

                main.post {
                    onAnalysis?.invoke(evalVal, pct, depthVal)
                    if (cfg.autoAccept && suggestion != null && state.isMyTurn) {
                        maybeAutoAccept(seq)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun maybeAutoAccept(seq: Int) {
        val move = suggestion ?: return
        main.postDelayed({
            if (cfg.autoAccept && seq == stateSeq && state.isMyTurn && !state.isOver) {
                playMove(move.first, move.second, 0)
            }
        }, AUTO_ACCEPT_DELAY_MS)
    }

    private fun squareOf(text: String): Int? {
        if (text.length < 2) return null
        val file = text[0] - 'a'
        val rank = text[1] - '1'
        if (file !in 0..7 || rank !in 0..7) return null
        return rank * 8 + file
    }

    private fun resetAnalysis(clearPct: Boolean = false) {
        winEma = null
        suggestion = null
        depth = 0
        evalText = "—"
        if (clearPct) winPct = null
    }

    private fun parseState(json: String) {
        try {
            val o = JSONObject(json)
            val pieces = HashMap<Int, Piece>()
            val arr = o.getJSONArray("pieces")
            for (i in 0 until arr.length()) {
                val row = arr.getJSONArray(i)
                pieces[row.getInt(0)] = Piece(row.getInt(1), row.getInt(2) == 1)
            }
            val lastArr = o.optJSONArray("last")
            val sans = ArrayList<String>()
            val sansArr = o.optJSONArray("sans")
            if (sansArr != null) for (i in 0 until sansArr.length()) sans.add(sansArr.getString(i))

            val err = o.optString("engineError").ifEmpty { null }
            if (err != null) engineError = err

            stateSeq++
            state = GameState(
                pieces = pieces,
                last = if (lastArr != null) lastArr.getInt(0) to lastArr.getInt(1) else null,
                flipped = o.optBoolean("flipped"),
                isMyTurn = o.optBoolean("isMyTurn"),
                isCheck = o.optBoolean("isCheck"),
                kingSquare = if (o.isNull("kingSquare")) null else o.optInt("kingSquare"),
                isOver = o.optBoolean("isOver"),
                engineReady = o.optBoolean("engineReady"),
                engineName = o.optString("engine"),
                ply = o.optInt("ply"),
                sans = sans,
                turnWhite = o.optString("turn") == "w",
                myWhite = o.optString("myColor") == "w"
            )
            pushState()
        } catch (_: Throwable) {
        }
    }

    private fun pushState() {
        main.post { onState?.invoke(state) }
    }

    private fun call(name: String, vararg args: Any, after: (String) -> Unit) {
        worker.execute {
            try {
                val res = module!!.callAttr(name, *args).toString()
                main.post { after(res) }
            } catch (t: Throwable) {
                main.post { onError?.invoke("${t.javaClass.simpleName}: ${t.message}") }
            }
        }
    }
}
