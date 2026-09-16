package org.renpy.android

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryUtilsTest {

    @Test
    fun testFullBattery() {
        val state = BatteryUtils.calculateBatteryState(100, 100, BatteryManager.BATTERY_STATUS_DISCHARGING)
        assertEquals(100, state.percentage)
        assertFalse(state.isCharging)
        assertFalse(state.isLow)
        assertEquals(R.drawable.ic_battery_full, state.iconRes)
    }

    @Test
    fun testBattery80Percent() {
        val state = BatteryUtils.calculateBatteryState(80, 100, BatteryManager.BATTERY_STATUS_DISCHARGING)
        assertEquals(80, state.percentage)
        assertFalse(state.isCharging)
        assertFalse(state.isLow)
        assertEquals(R.drawable.ic_battery_60, state.iconRes)
    }

    @Test
    fun testBattery50Percent() {
        val state = BatteryUtils.calculateBatteryState(50, 100, BatteryManager.BATTERY_STATUS_DISCHARGING)
        assertEquals(50, state.percentage)
        assertFalse(state.isCharging)
        assertFalse(state.isLow)
        assertEquals(R.drawable.ic_battery_half, state.iconRes)
    }

    @Test
    fun testBattery25Percent() {
        val state = BatteryUtils.calculateBatteryState(25, 100, BatteryManager.BATTERY_STATUS_NOT_CHARGING)
        assertEquals(25, state.percentage)
        assertFalse(state.isCharging)
        assertFalse(state.isLow)
        assertEquals(R.drawable.ic_battery_20, state.iconRes)
    }

    @Test
    fun testLowBatteryAlert() {
        val state = BatteryUtils.calculateBatteryState(10, 100, BatteryManager.BATTERY_STATUS_DISCHARGING)
        assertEquals(10, state.percentage)
        assertFalse(state.isCharging)
        assertTrue(state.isLow)
        assertEquals(R.drawable.ic_battery_alert, state.iconRes)
    }

    @Test
    fun testChargingStateOverridesLow() {
        val state = BatteryUtils.calculateBatteryState(5, 100, BatteryManager.BATTERY_STATUS_CHARGING)
        assertEquals(5, state.percentage)
        assertTrue(state.isCharging)
        assertFalse(state.isLow)
        assertEquals(R.drawable.ic_battery_charging, state.iconRes)
    }

    @Test
    fun testInvalidBatteryData() {
        val state = BatteryUtils.calculateBatteryState(-1, 100, BatteryManager.BATTERY_STATUS_UNKNOWN)
        assertEquals(-1, state.percentage)

        val stateZeroScale = BatteryUtils.calculateBatteryState(50, 0, BatteryManager.BATTERY_STATUS_UNKNOWN)
        assertEquals(-1, stateZeroScale.percentage)
    }
}
