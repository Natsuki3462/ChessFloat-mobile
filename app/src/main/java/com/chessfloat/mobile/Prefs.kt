package com.chessfloat.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Preset(val key: String, val label: String, val ms: Int, val threads: Int, val hashMb: Int)

data class Config(
    val strength: String = "balanced",
    val adaptive: Boolean = true,
    val analysisMs: Int = 2000,
    val threads: Int = 2,
    val hashMb: Int = 128,
    val debounceMs: Int = 300,
    val opacity: Int = 94,
    val showCoords: Boolean = true,
    val autoAccept: Boolean = false
)

object Presets {
    val defaults = listOf(
        Preset("economy", "省电", 800, 2, 64),
        Preset("balanced", "均衡", 2000, 2, 128),
        Preset("deep", "深度", 3500, 3, 256),
        Preset("max", "极限", 6000, 4, 512)
    )

    fun load(ctx: Context): List<Preset> {
        val raw = ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getString("presets", null) ?: return defaults
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Preset>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val key = o.getString("k")
                val base = defaults.firstOrNull { it.key == key } ?: continue
                out.add(
                    base.copy(
                        ms = o.getInt("ms").coerceIn(100, 15000),
                        threads = o.getInt("t").coerceIn(1, 8),
                        hashMb = o.getInt("h").coerceIn(16, 512)
                    )
                )
            }
            if (out.size == defaults.size) out else defaults
        } catch (_: Throwable) {
            defaults
        }
    }

    fun save(ctx: Context, list: List<Preset>) {
        val arr = JSONArray()
        for (p in list) {
            arr.put(JSONObject().apply {
                put("k", p.key)
                put("ms", p.ms)
                put("t", p.threads)
                put("h", p.hashMb)
            })
        }
        ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit().putString("presets", arr.toString()).apply()
    }

    fun apply(cfg: Config, p: Preset): Config =
        cfg.copy(strength = p.key, analysisMs = p.ms, threads = p.threads, hashMb = p.hashMb)
}

object Prefs {
    const val NAME = "chessfloat"

    fun load(ctx: Context): Config {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val d = Config()
        return Config(
            strength = sp.getString("strength", d.strength) ?: d.strength,
            adaptive = sp.getBoolean("adaptive", d.adaptive),
            analysisMs = sp.getInt("analysisMs", d.analysisMs),
            threads = sp.getInt("threads", d.threads),
            hashMb = sp.getInt("hashMb", d.hashMb),
            debounceMs = sp.getInt("debounceMs", d.debounceMs),
            opacity = sp.getInt("opacity", d.opacity),
            showCoords = sp.getBoolean("showCoords", d.showCoords),
            autoAccept = sp.getBoolean("autoAccept", d.autoAccept)
        )
    }

    fun save(ctx: Context, cfg: Config) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().apply {
            putString("strength", cfg.strength)
            putBoolean("adaptive", cfg.adaptive)
            putInt("analysisMs", cfg.analysisMs)
            putInt("threads", cfg.threads)
            putInt("hashMb", cfg.hashMb)
            putInt("debounceMs", cfg.debounceMs)
            putInt("opacity", cfg.opacity)
            putBoolean("showCoords", cfg.showCoords)
            putBoolean("autoAccept", cfg.autoAccept)
            apply()
        }
    }
}
