package org.renpy.android

import android.os.BatteryManager

object BatteryUtils {

    data class BatteryState(
        val percentage: Int,
        val isCharging: Boolean,
        val iconRes: Int,
        val isLow: Boolean
    )

    fun calculateBatteryState(level: Int, scale: Int, status: Int): BatteryState {
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val percentage = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt().coerceIn(0, 100)
        } else {
            -1
        }

        val isLow = percentage in 0..15 && !isCharging

        val iconRes = when {
            isCharging -> R.drawable.ic_battery_charging
            percentage <= 15 -> R.drawable.ic_battery_alert
            percentage <= 35 -> R.drawable.ic_battery_20
            percentage <= 65 -> R.drawable.ic_battery_half
            percentage <= 85 -> R.drawable.ic_battery_60
            else -> R.drawable.ic_battery_full
        }

        return BatteryState(
            percentage = percentage,
            isCharging = isCharging,
            iconRes = iconRes,
            isLow = isLow
        )
    }
}
