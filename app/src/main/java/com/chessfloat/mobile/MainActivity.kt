package com.chessfloat.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var grantButton: Button
    private lateinit var toggleButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
            setBackgroundColor(Theme.CARD_BG)
        }

        root.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76))
                .apply { bottomMargin = dp(18) }
        })

        root.addView(TextView(this).apply {
            text = "ChessFloat"
            setTextColor(Theme.TEXT)
            textSize = 26f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "本地 Stockfish 实时分析 · 完全离线 · 不上传任何数据"
            setTextColor(Theme.TEXT_DIM)
            textSize = 13f
            setPadding(0, dp(8), 0, dp(28))
        })

        statusText = TextView(this).apply {
            setTextColor(Theme.TEXT)
            textSize = 14f
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(statusText)

        grantButton = Button(this).apply {
            text = "授予悬浮窗权限"
            setOnClickListener { openOverlaySettings() }
        }
        root.addView(grantButton, lp())

        toggleButton = Button(this).apply {
            text = "启动悬浮棋盘"
            setOnClickListener { toggleService() }
        }
        root.addView(toggleButton, lp())

        root.addView(TextView(this).apply {
            text = "用法：回到任意界面 → 点悬浮气泡展开棋盘 → " +
                "点起点再点落点复刻着法。气泡上的数字是你执的一方当前胜率。"
            setTextColor(Theme.TEXT_FAINT)
            textSize = 12f
            setPadding(0, dp(28), 0, 0)
        })

        setContentView(root)
        requestNotificationPermissionIfNeeded()
    }

    private fun lp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(10) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        OverlayState.running = OverlayService.instance != null
        val canOverlay = Settings.canDrawOverlays(this)
        statusText.text = if (canOverlay) {
            "悬浮窗权限：已授予"
        } else {
            "悬浮窗权限：未授予（必须授予才能显示气泡）"
        }
        statusText.setTextColor(if (canOverlay) Theme.ACCENT else Theme.WARN)
        grantButton.isEnabled = !canOverlay
        toggleButton.isEnabled = canOverlay
        toggleButton.text = if (OverlayState.running) "停止悬浮棋盘" else "启动悬浮棋盘"
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun toggleService() {
        val intent = Intent(this, OverlayService::class.java)
        if (OverlayState.running) {
            stopService(intent)
            OverlayState.running = false
        } else {
            ContextCompat.startForegroundService(this, intent)
            OverlayState.running = true
        }
        refresh()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}

object OverlayState {
    var running = false
}
