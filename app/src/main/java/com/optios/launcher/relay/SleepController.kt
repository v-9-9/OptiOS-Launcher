package com.optios.launcher.relay

/** Sleep = pure OptiOS overlay; no root, no system changes. */
object SleepController {
    @Volatile var isAsleep: Boolean = false
    @Volatile var sleepListener: ((Boolean) -> Unit)? = null

    fun setSleep(asleep: Boolean) {
        isAsleep = asleep
        sleepListener?.invoke(asleep)
    }
}
