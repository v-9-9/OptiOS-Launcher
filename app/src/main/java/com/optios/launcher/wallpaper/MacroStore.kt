package com.optios.launcher.wallpaper

import android.content.Context
import org.json.JSONObject

/**
 * Key → action bindings recorded in the Control Center.
 * Storage: prefs JSON {"24":"youtube","25":"switch_apps" ...}
 */
object MacroStore {

    private const val KEY = "macros"

    const val ACT_YOUTUBE = "youtube"
    const val ACT_CINEMANA = "cinemana"
    const val ACT_SWITCH = "switch_apps"
    const val ACT_SLEEP = "sleep"

    val ACTIONS: Map<String, String> = mapOf(
        ACT_YOUTUBE to "Open YouTube",
        ACT_CINEMANA to "Open Cinemana",
        ACT_SWITCH to "Switch YouTube / Cinemana",
        ACT_SLEEP to "Sleep / Wake",
    )

    fun load(ctx: Context): MutableMap<Int, String> {
        val json = ctx.getSharedPreferences("optios", Context.MODE_PRIVATE).getString(KEY, null) ?: return mutableMapOf()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<Int, String>()
            obj.keys().forEach { k -> map[k.toInt()] = obj.getString(k) }
            map
        } catch (_: Exception) { mutableMapOf() }
    }

    fun save(ctx: Context, macros: Map<Int, String>) {
        val obj = JSONObject()
        macros.forEach { (k, v) -> obj.put(k.toString(), v) }
        ctx.getSharedPreferences("optios", Context.MODE_PRIVATE).edit().putString(KEY, obj.toString()).apply()
    }
}
