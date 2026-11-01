package com.chessfloat.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "chessfloat_overlay"
        private const val NOTIF_ID = 41
        const val ACTION_STOP = "com.chessfloat.mobile.STOP"
        var instance: OverlayService? = null
            private set
    }

    private lateinit var wm: WindowManager
    private lateinit var bubble: BubbleView
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var cfg = Config()

    private var panel: LinearLayout? = null
    private var board: BoardView? = null
    private var statusText: TextView? = null
    private var winText: TextView? = null
    private var evalText: TextView? = null
    private var autoBtn: TextView? = null
    private var acceptBtn: TextView? = null
    private var historyRow: LinearLayout? = null
    private var historyScroll: HorizontalScrollView? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var picker: LinearLayout? = null
    private var pendingPromotion: Pair<Int, Int>? = null

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        cfg = Prefs.load(this)
        startForegroundCompat()
        addBubble()

        ChessCore.onState = { st ->
            board?.state = st
            refreshHistory(st)
            refreshStatus()
        }
        ChessCore.onSelectionChanged = { sel, targets ->
            board?.selected = sel
            board?.legalTargets = targets
        }
        ChessCore.onAnalysis = { _, pct, _ ->
            board?.let {
                it.suggestion = ChessCore.suggestion
                it.suggestionStale = false
            }
            if (pct != null) bubble.winPct = pct
            refreshStatus()
        }
        ChessCore.onPromotionNeeded = { from, to ->
            pendingPromotion = Pair(from, to)
            showPromotionPicker(from, to)
        }
        ChessCore.onError = { msg -> android.util.Log.e("ChessFloat", msg); refreshStatus() }

        val engine = applicationInfo.nativeLibraryDir + "/libstockfish.so"
        ChessCore.start(this, engine, cfg) { refreshStatus() }

        showSidePicker(cancellable = false)
    }

    private fun showSidePicker(cancellable: Boolean = true) {
        pendingPromotion = null
        showPicker(
            listOf(
                "执白" to { ChessCore.newGame(true) },
                "执黑" to { ChessCore.newGame(false) }
            ),
            cancellable = cancellable
        )
    }

    private fun exitApp() {
        Toast.makeText(this, "已退出，棋局未保留", Toast.LENGTH_SHORT).show()
        stopSelf()
    }

    fun applyConfig(newCfg: Config) {
        cfg = newCfg
        ChessCore.applyConfig(cfg)
        board?.showCoords = cfg.showCoords
        panel?.setBackgroundColor(panelColor())
        refreshStatus()
    }

    private fun panelColor(): Int {
        val alpha = cfg.opacity.coerceIn(15, 100) * 255 / 100
        return Color.argb(alpha, 17, 19, 24)
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "棋盘浮窗", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChessFloat 运行中")
            .setContentText("点悬浮气泡展开棋盘")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }

    private fun addBubble() {
        bubble = BubbleView(this)
        val size = dp(56)
        bubbleParams = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - dp(12)
            y = resources.displayMetrics.heightPixels / 3
        }
        bubble.onTap = { togglePanel() }
        bubble.onLongPress = { exitApp() }
        bubble.onDragBy = { dx, dy ->
            bubbleParams.x += dx.toInt()
            bubbleParams.y += dy.toInt()
            wm.updateViewLayout(bubble, bubbleParams)
        }
        bubble.onDragEnd = { snapBubble() }
        bubble.winPct = ChessCore.winPct
        wm.addView(bubble, bubbleParams)
    }

    private fun snapBubble() {
        val screenW = resources.displayMetrics.widthPixels
        val margin = dp(8)
        bubbleParams.x = if (bubbleParams.x + bubbleParams.width / 2 > screenW / 2) {
            screenW - bubbleParams.width - margin
        } else margin
        bubbleParams.y = bubbleParams.y.coerceIn(
            margin, resources.displayMetrics.heightPixels - bubbleParams.height - margin
        )
        wm.updateViewLayout(bubble, bubbleParams)
        if (panel != null) layoutPanel()
    }

    private fun togglePanel() {
        if (panel != null) hidePanel() else showPanel()
    }

    private fun rounded(color: Int, radius: Int = 6): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(color)
        }

    private fun toolBtn(glyph: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = glyph
            setTextColor(Theme.TEXT_DIM)
            textSize = 15f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(26))
            setOnClickListener { onClick() }
        }

    private fun showPanel() {
        if (panel != null) return
        val side = minOf((resources.displayMetrics.widthPixels * 0.92f).toInt(), dp(340))

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panelColor())
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val status = TextView(this).apply {
            setTextColor(Theme.TEXT_DIM)
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(4), 0, 0, 0)
        }
        val win = TextView(this).apply {
            setTextColor(Theme.TEXT)
            textSize = 12f
            typeface = Typeface.create("monospace", Typeface.BOLD)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            text = "—"
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(26))
        }
        val ev = TextView(this).apply {
            setTextColor(Theme.TEXT_DIM)
            textSize = 12f
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            text = "—"
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(26))
        }
        val auto = toolBtn("A") { toggleAuto() }
        val settings = toolBtn("⚙") { openSettings() }

        statusText = status
        winText = win
        evalText = ev
        autoBtn = auto

        header.addView(status, LinearLayout.LayoutParams(0, dp(26), 1f))
        header.addView(win)
        header.addView(ev)
        header.addView(auto)
        header.addView(settings)
        container.addView(header)

        val bv = BoardView(this).apply {
            state = ChessCore.state
            suggestion = ChessCore.suggestion
            selected = ChessCore.currentSelection
            legalTargets = ChessCore.currentTargets
            showCoords = cfg.showCoords
            onSquareTap = { sq ->

                if (picker == null) ChessCore.tapSquare(sq)
            }
        }
        container.addView(bv, LinearLayout.LayoutParams(side - dp(12), side - dp(12)))

        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroll.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(24)
        ))
        historyScroll = scroll
        historyRow = row
        container.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(24)
        ))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val accept = TextView(this).apply {
            text = "采纳"
            setTextColor(0xFFD8FFE4.toInt())
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            background = rounded(0x334ADE80)
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(28))
                .apply { marginEnd = dp(4) }
            setOnClickListener {
                pendingPromotion = null
                hidePicker()
                ChessCore.acceptSuggestion()
            }
        }
        acceptBtn = accept

        footer.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        footer.addView(accept)
        footer.addView(toolBtn("↩") { pendingPromotion = null; hidePicker(); ChessCore.undo() })
        footer.addView(toolBtn("↪") { pendingPromotion = null; hidePicker(); ChessCore.redo() })
        footer.addView(toolBtn("⟳") { showSidePicker() })
        container.addView(footer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(32)
        ))

        val params = WindowManager.LayoutParams(

            side, side + dp(78),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        panel = container
        board = bv
        panelParams = params
        wm.addView(container, params)
        layoutPanel()
        refreshHistory(ChessCore.state)
        refreshStatus()

        wm.removeView(bubble)
        wm.addView(bubble, bubbleParams)

        restorePromotion()
    }

    private fun toggleAuto() {
        cfg = cfg.copy(autoAccept = !cfg.autoAccept)
        Prefs.save(this, cfg)
        ChessCore.applyConfig(cfg)
        refreshStatus()
    }

    private fun refreshStatus() {
        val st = ChessCore.state
        val err = ChessCore.engineError
        statusText?.let { t ->
            t.text = when {
                err != null -> "引擎异常"
                !st.engineReady -> "引擎启动中…"
                st.isOver -> "对局结束"
                !st.isMyTurn -> "复刻对手"
                ChessCore.suggestion != null ->
                    if (ChessCore.depth > 0) "轮你走 · 深度 ${ChessCore.depth}" else "轮你走"
                else -> "轮你走 · 分析中…"
            }
            t.setTextColor(if (err != null) Theme.WARN else Theme.TEXT_DIM)
        }
        winText?.text = ChessCore.winPct?.let { "$it%" } ?: "—"
        evalText?.text = ChessCore.evalText
        evalText?.setTextColor(if (err != null) Theme.WARN else Theme.TEXT_DIM)
        autoBtn?.setTextColor(if (cfg.autoAccept) Theme.ACCENT else Theme.TEXT_DIM)
        autoBtn?.background = if (cfg.autoAccept) rounded(0x1A4ADE80) else null
        acceptBtn?.alpha = if (ChessCore.suggestion != null && st.isMyTurn) 1f else 0.4f
        bubble.winPct = ChessCore.winPct
    }

    private fun refreshHistory(st: GameState) {
        val row = historyRow ?: return
        row.removeAllViews()
        if (st.sans.isEmpty()) {
            row.addView(TextView(this).apply {
                text = "尚无着法"
                setTextColor(Theme.TEXT_FAINT)
                textSize = 11f
                setPadding(dp(4), 0, dp(4), 0)
            })
            return
        }
        st.sans.forEachIndexed { i, san ->
            val label = if (i % 2 == 0) "${i / 2 + 1}.$san" else san
            val current = i == st.ply - 1
            row.addView(TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(if (i < st.ply) Theme.TEXT else Theme.TEXT_FAINT)
                setPadding(dp(5), dp(3), dp(5), dp(3))
                if (current) background = rounded(0x2E4ADE80, 4)
                setOnClickListener { ChessCore.jumpTo(i + 1) }
            })
        }
        historyScroll?.post { historyScroll?.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun openSettings() {
        hidePanel()
        startActivity(Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun setBubbleVisible(visible: Boolean) {
        if (!::bubble.isInitialized) return
        try {
            if (visible) {
                if (bubble.parent == null) wm.addView(bubble, bubbleParams)
            } else {
                if (bubble.parent != null) wm.removeView(bubble)
            }
        } catch (_: Throwable) {
        }
    }

    private fun showPicker(options: List<Pair<String, () -> Unit>>, cancellable: Boolean = true) {
        hidePicker()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Theme.CARD_BG)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            gravity = Gravity.CENTER
        }
        for ((label, action) in options) {
            card.addView(TextView(this).apply {
                text = label
                setTextColor(Theme.TEXT)
                textSize = 17f
                gravity = Gravity.CENTER
                minWidth = dp(60)
                minHeight = dp(60)
                setPadding(dp(14), dp(16), dp(14), dp(16))
                setOnClickListener {
                    hidePicker()
                    action()
                }
            })
        }
        if (cancellable) {
            card.addView(TextView(this).apply {
                text = "取消"
                setTextColor(Theme.TEXT_DIM)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setOnClickListener { hidePicker() }
            })
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        picker = card
        wm.addView(card, params)

        if (::bubble.isInitialized && bubble.parent != null) {
            wm.removeView(bubble)
            wm.addView(bubble, bubbleParams)
        }
    }

    private fun hidePicker() {
        picker?.let {
            try {
                wm.removeView(it)
            } catch (_: Throwable) {
            }
        }
        picker = null
    }

    private fun showPromotionPicker(from: Int, to: Int) {
        showPicker(listOf(
            "后" to { pickPromotion(from, to, 5) },
            "车" to { pickPromotion(from, to, 4) },
            "象" to { pickPromotion(from, to, 3) },
            "马" to { pickPromotion(from, to, 2) }
        ), cancellable = false)
    }

    private fun pickPromotion(from: Int, to: Int, piece: Int) {
        pendingPromotion = null
        ChessCore.playMove(from, to, piece)
    }

    private fun restorePromotion() {
        val pending = pendingPromotion ?: return
        val (from, to) = pending
        if (ChessCore.currentSelection != from || !ChessCore.currentTargets.contains(to)) {
            pendingPromotion = null
            return
        }
        showPromotionPicker(from, to)
    }

    private fun layoutPanel() {
        val p = panel ?: return
        val params = panelParams ?: return
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val margin = dp(8)
        val w = params.width
        val h = params.height
        var x = bubbleParams.x + bubbleParams.width / 2 - w / 2
        x = x.coerceIn(margin, maxOf(margin, screenW - w - margin))
        var y = bubbleParams.y + bubbleParams.height + dp(8)
        if (y + h > screenH - margin) y = bubbleParams.y - h - dp(8)
        y = y.coerceIn(margin, maxOf(margin, screenH - h - margin))
        params.x = x
        params.y = y
        wm.updateViewLayout(p, params)
    }

    private fun hidePanel() {
        hidePicker()
        panel?.let { wm.removeView(it) }
        panel = null
        board = null
        statusText = null
        winText = null
        evalText = null
        autoBtn = null
        acceptBtn = null
        historyRow = null
        historyScroll = null
        panelParams = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        hidePanel()
        try {
            if (::bubble.isInitialized) wm.removeView(bubble)
        } catch (_: Throwable) {
        }
        ChessCore.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
