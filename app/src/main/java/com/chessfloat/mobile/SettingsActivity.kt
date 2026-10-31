package com.chessfloat.mobile

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private var cfg = Config()
    private var presets: List<Preset> = Presets.defaults
    private var selected = "balanced"
    private var suppress = false

    private lateinit var chipRow: LinearLayout
    private lateinit var msBar: SeekBar
    private lateinit var msLabel: TextView
    private lateinit var thBar: SeekBar
    private lateinit var thLabel: TextView
    private lateinit var hsBar: SeekBar
    private lateinit var hsLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = Prefs.load(this)
        presets = Presets.load(this)
        selected = cfg.strength

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(24))
            setBackgroundColor(Theme.CARD_BG)
        }
        val scroll = ScrollView(this)
        scroll.addView(root)

        root.addView(title("设置"))
        root.addView(hint("改动即时生效，无需重启浮窗"))

        root.addView(section("分析强度"))
        chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(chipRow)
        renderChips()

        root.addView(section("思考时间"))
        msLabel = valueLabel()
        root.addView(msLabel)
        msBar = seekBar(100, 15000) { v ->
            if (!suppress) updatePreset { it.copy(ms = v) }
        }
        root.addView(msBar)

        root.addView(section("引擎线程数"))
        thLabel = valueLabel()
        root.addView(thLabel)
        thBar = seekBar(1, 8) { v ->
            if (!suppress) updatePreset { it.copy(threads = v) }
        }
        root.addView(thBar)

        root.addView(section("Hash"))
        hsLabel = valueLabel()
        root.addView(hsLabel)
        hsBar = seekBar(16, 512) { v ->
            if (!suppress) updatePreset { it.copy(hashMb = v) }
        }
        root.addView(hsBar)

        root.addView(section("走子防抖"))
        val debounceLabel = valueLabel()
        debounceLabel.text = "${cfg.debounceMs} ms"
        root.addView(debounceLabel)
        root.addView(seekBar(0, 1500, cfg.debounceMs) { v ->
            debounceLabel.text = "$v ms"
            cfg = cfg.copy(debounceMs = v)
            persist()
        })

        root.addView(section("棋盘透明度"))
        val opacityLabel = valueLabel()
        opacityLabel.text = "${cfg.opacity} %"
        root.addView(opacityLabel)
        root.addView(seekBar(15, 100, cfg.opacity) { v ->
            opacityLabel.text = "$v %"
            cfg = cfg.copy(opacity = v)
            persist()
        })

        root.addView(section("开关"))
        root.addView(check("自适应：难的局面多算、无脑局面少算", cfg.adaptive) { on ->
            cfg = cfg.copy(adaptive = on); persist()
        })
        root.addView(check("显示棋盘坐标", cfg.showCoords) { on ->
            cfg = cfg.copy(showCoords = on); persist()
        })
        root.addView(check("自动采纳：引擎出建议后自动走子", cfg.autoAccept) { on ->
            cfg = cfg.copy(autoAccept = on); persist()
        })

        root.addView(section("引擎状态"))
        root.addView(TextView(this).apply {
            text = when {
                ChessCore.engineError != null -> "异常：${ChessCore.engineError}"
                ChessCore.state.engineReady -> "${ChessCore.state.engineName} · 就绪"
                else -> "未就绪"
            }
            setTextColor(if (ChessCore.engineError != null) Theme.WARN else Theme.TEXT_DIM)
            textSize = 13f
        })

        root.addView(Button(this).apply {
            text = "关闭"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) })

        setContentView(scroll)
        bindSliders()
    }

    private fun renderChips() {
        chipRow.removeAllViews()
        for (p in presets) {
            val active = p.key == selected
            chipRow.addView(TextView(this).apply {
                text = p.label
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(9), 0, dp(9))
                setTextColor(if (active) Theme.ACCENT else Theme.TEXT_DIM)
                setBackgroundColor(if (active) 0x1A4ADE80 else 0x0FFFFFFF)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginEnd = dp(4) }
                setOnClickListener {
                    selected = p.key
                    cfg = Presets.apply(cfg, p)
                    persist()
                    renderChips()
                    bindSliders()
                }
            })
        }
    }

    private fun updatePreset(edit: (Preset) -> Preset) {
        val list = presets.map { if (it.key == selected) edit(it) else it }
        presets = list
        Presets.save(this, list)
        val current = list.firstOrNull { it.key == selected } ?: return
        cfg = Presets.apply(cfg, current)
        persist()
        bindSliders()
    }

    private fun bindSliders() {
        val p = presets.firstOrNull { it.key == selected } ?: return
        suppress = true
        msBar.progress = p.ms
        thBar.progress = p.threads
        hsBar.progress = p.hashMb
        msLabel.text = "${p.ms} ms"
        thLabel.text = "${p.threads} 线程"
        hsLabel.text = "${p.hashMb} MB"
        suppress = false
    }

    private fun persist() {
        Prefs.save(this, cfg)
        OverlayService.instance?.applyConfig(cfg)
    }

    override fun onStart() {
        super.onStart()
        OverlayService.instance?.setBubbleVisible(false)
    }

    override fun onStop() {
        super.onStop()
        OverlayService.instance?.setBubbleVisible(true)
    }

    private fun seekBar(min: Int, max: Int, value: Int = min, onChange: (Int) -> Unit): SeekBar =
        SeekBar(this).apply {
            this.min = min
            this.max = max
            progress = value
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) onChange(p)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

    private fun check(text: String, checked: Boolean, onChange: (Boolean) -> Unit): CheckBox =
        CheckBox(this).apply {
            this.text = text
            isChecked = checked
            setTextColor(Theme.TEXT)
            textSize = 13f
            setPadding(0, dp(4), 0, dp(4))
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }

    private fun valueLabel() = TextView(this).apply {
        setTextColor(Theme.TEXT)
        textSize = 13f
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun title(t: String) = TextView(this).apply {
        text = t
        setTextColor(Theme.TEXT)
        textSize = 24f
        setPadding(0, 0, 0, dp(4))
    }

    private fun hint(t: String) = TextView(this).apply {
        text = t
        setTextColor(Theme.TEXT_FAINT)
        textSize = 12f
        setPadding(0, 0, 0, dp(16))
    }

    private fun section(t: String) = TextView(this).apply {
        text = t
        setTextColor(Theme.TEXT_DIM)
        textSize = 12f
        setPadding(0, dp(16), 0, dp(4))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
