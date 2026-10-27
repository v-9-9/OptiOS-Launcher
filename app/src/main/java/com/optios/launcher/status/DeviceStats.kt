package com.optios.launcher.status

import java.io.File

/**
 * Lightweight device stats for the USAGE widget (no root, no extra deps).
 * CPU load is a delta over /proc/stat; temps come from thermal zones.
 */
object DeviceStats {

    data class Snapshot(
        val ramUsedPct: Int,
        val cpuLoadPct: Int,
        val cpuTempC: Int,
        val gpuTempC: Int,
    )

    private var lastCpuIdle = 0L
    private var lastCpuTotal = 0L

    /** Rolling window to keep the displayed CPU load stable, not jumpy. */
    private val loadHistory = FloatArray(4) { 0f }
    private var loadIdx = 0

    fun snapshot(): Snapshot {
        val memTotal = readLongField("/proc/meminfo", "MemTotal")
        val memAvail = readLongField("/proc/meminfo", "MemAvailable")
        val ramPct = if (memTotal > 0) ((memTotal - memAvail) * 100 / memTotal).toInt() else 0

        loadHistory[loadIdx] = readCpuLoadPct().toFloat()
        loadIdx = (loadIdx + 1) % loadHistory.size
        val cpuLoad = loadHistory.average().toInt()

        val cpuTemp = readThermal(0)
        val gpuTemp = readThermal(1)

        return Snapshot(ramPct, cpuLoad, cpuTemp, gpuTemp)
    }

    private fun readLongField(path: String, field: String): Long = try {
        File(path).readLines().firstOrNull { it.startsWith(field) }
            ?.substringAfter(":")?.trim()?.split(" ")?.firstOrNull()?.toLong() ?: 0L
    } catch (_: Exception) { 0L }

    /** One-shot /proc/stat delta since the previous call. */
    private fun readCpuLoadPct(): Int {
        return try {
            val line = File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") } ?: return 0
            val p = line.split("\\s+".toRegex()).drop(1).map { it.toLong() }
            // user nice system idle iowait irq softirq steal ...
            val idle = p[3] + p[4]
            val total = p.sum()
            val dTotal = total - lastCpuTotal
            val dIdle = idle - lastCpuIdle
            lastCpuTotal = total
            lastCpuIdle = idle
            if (dTotal > 0) (((dTotal - dIdle) * 100) / dTotal).toInt().coerceIn(0, 100) else 0
        } catch (_: Exception) { 0 }
    }

    private fun readThermal(zone: Int): Int = try {
        (File("/sys/class/thermal/thermal_zone$zone/temp").readText().trim().toInt() / 1000)
            .coerceIn(0, 125)
    } catch (_: Exception) { 0 }
}
