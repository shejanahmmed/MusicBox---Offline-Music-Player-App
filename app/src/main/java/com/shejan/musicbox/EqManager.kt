/*
 * Copyright (C) 2026 Shejan
 *
 * This file is part of MusicBox.
 *
 * MusicBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MusicBox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MusicBox.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.shejan.musicbox

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.core.content.edit

/**
 * Singleton that manages the built-in Android [Equalizer] audio effect.
 *
 * Presents exactly 5 logical bands mapped from the device's native bands:
 *   0 = Sub-Bass, 1 = Bass, 2 = Mid, 3 = High-Mid, 4 = Treble
 *
 * Gains are stored in milli-bels (100 mB = 1 dB). The UI maps −1500 … +1500 mB (−15 … +15 dB).
 */
object EqManager {

    private const val TAG = "EqManager"
    private const val PREFS_NAME = "MusicBoxEqPrefs"
    private const val KEY_ENABLED = "eq_enabled"
    private const val KEY_BAND_PREFIX = "eq_band_"
    private const val KEY_PRESET = "eq_preset"

    /** Number of logical bands exposed to the UI */
    const val BAND_COUNT = 5
    val BAND_LABELS = arrayOf("Sub-Bass", "Bass", "Mid", "High-Mid", "Treble")

    // ─── Preset gain tables (in milli-bels, 100 mB = 1 dB) ───────────────────
    data class Preset(val name: String, val gains: IntArray)

    val PRESETS = listOf(
        Preset("Flat",       intArrayOf(    0,    0,    0,    0,    0)),
        Preset("Rock",       intArrayOf(  400,  200,    0,  200,  400)),
        Preset("Pop",        intArrayOf( -200,  300,  500,  300, -100)),
        Preset("Jazz",       intArrayOf(  300,  200,    0,  200,  300)),
        Preset("Classical",  intArrayOf(  500,  300, -200,    0,  200)),
        Preset("Bass Boost", intArrayOf(  800,  600,  100, -100, -200))
    )

    // ─── State ────────────────────────────────────────────────────────────────
    private var equalizer: Equalizer? = null

    /** Logical band gains in milli-bels, indexed 0..BAND_COUNT-1 */
    private val bandGains = IntArray(BAND_COUNT) { 0 }

    var isEnabled: Boolean = false
        private set

    /** Maps logical band index [0..4] → native band index */
    private var nativeBandMap = IntArray(BAND_COUNT) { it }

    // ─── Attach / Detach ──────────────────────────────────────────────────────

    /**
     * Attaches (or re-attaches) the Equalizer to the given [audioSessionId].
     * Safe to call on every track change.
     */
    fun attach(context: Context, audioSessionId: Int) {
        release() // Always release old instance first

        if (audioSessionId == 0) {
            Log.w(TAG, "Invalid audio session id (0), skipping EQ attach")
            return
        }

        try {
            equalizer = Equalizer(0, audioSessionId).also { eq ->
                eq.enabled = false // We will enable after restoring bands

                // Build native band mapping
                buildBandMap(eq)

                // Restore persisted settings
                loadSettings(context)

                // Apply restored gains
                applyAllGains(eq)
                eq.enabled = isEnabled

                Log.d(TAG, "EQ attached to session $audioSessionId, enabled=$isEnabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach Equalizer: ${e.message}")
            equalizer = null
        }
    }

    /** Releases the Equalizer effect. Called when MusicService is destroyed. */
    fun release() {
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null
    }

    // ─── Band Mapping ─────────────────────────────────────────────────────────

    /**
     * Selects 5 native bands to represent Sub-Bass, Bass, Mid, High-Mid, Treble
     * by evenly sampling the device's native band center frequencies.
     */
    private fun buildBandMap(eq: Equalizer) {
        val nativeCount = eq.numberOfBands.toInt()
        if (nativeCount <= 0) return

        when {
            nativeCount >= BAND_COUNT -> {
                // Evenly sample nativeCount bands into 5 logical slots
                for (i in 0 until BAND_COUNT) {
                    nativeBandMap[i] = (i.toFloat() / (BAND_COUNT - 1) * (nativeCount - 1)).toInt()
                }
            }
            else -> {
                // Fewer than 5 native bands — map as many as we can, rest repeat last
                for (i in 0 until BAND_COUNT) {
                    nativeBandMap[i] = minOf(i, nativeCount - 1)
                }
            }
        }

        Log.d(TAG, "Native band count: $nativeCount, map: ${nativeBandMap.toList()}")
    }

    // ─── Gain Control ─────────────────────────────────────────────────────────

    /** Returns the gain for logical [band] in milli-bels. */
    fun getBandGainMb(band: Int): Int {
        return bandGains.getOrElse(band) { 0 }
    }

    /** Sets the gain for logical [band] in milli-bels and applies it immediately. */
    fun setBandGainMb(band: Int, gainMb: Int) {
        if (band !in 0 until BAND_COUNT) return
        val eq = equalizer ?: run {
            bandGains[band] = gainMb
            return
        }
        try {
            val nativeBand = nativeBandMap[band].toShort()
            val clampedGain = gainMb.toShort()
            eq.setBandLevel(nativeBand, clampedGain)
            bandGains[band] = gainMb
        } catch (e: Exception) {
            Log.e(TAG, "setBandLevel failed: ${e.message}")
        }
    }

    private fun applyAllGains(eq: Equalizer) {
        for (i in 0 until BAND_COUNT) {
            try {
                eq.setBandLevel(nativeBandMap[i].toShort(), bandGains[i].toShort())
            } catch (e: Exception) {
                Log.e(TAG, "applyAllGains band $i failed: ${e.message}")
            }
        }
    }

    // ─── Enable / Disable ─────────────────────────────────────────────────────

    fun setEnabled(context: Context, enabled: Boolean) {
        isEnabled = enabled
        try {
            equalizer?.enabled = enabled
        } catch (e: Exception) {
            Log.e(TAG, "setEnabled failed: ${e.message}")
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ENABLED, enabled)
        }
    }

    // ─── Presets ──────────────────────────────────────────────────────────────

    /** Applies a preset by name (no-op if not found). */
    fun applyPreset(context: Context, presetName: String) {
        val preset = PRESETS.find { it.name == presetName } ?: return
        for (i in 0 until BAND_COUNT) {
            setBandGainMb(i, preset.gains[i])
            bandGains[i] = preset.gains[i]
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_PRESET, presetName)
        }
        saveSettings(context)
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    fun saveSettings(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ENABLED, isEnabled)
            for (i in 0 until BAND_COUNT) {
                putInt("$KEY_BAND_PREFIX$i", bandGains[i])
            }
        }
    }

    private fun loadSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isEnabled = prefs.getBoolean(KEY_ENABLED, false)
        for (i in 0 until BAND_COUNT) {
            bandGains[i] = prefs.getInt("$KEY_BAND_PREFIX$i", 0)
        }
    }

    /** Returns the name of the preset currently matching the band gains, or null if custom. */
    fun getMatchingPresetName(): String? {
        for (preset in PRESETS) {
            var match = true
            for (i in 0 until BAND_COUNT) {
                if (getBandGainMb(i) != preset.gains[i]) {
                    match = false
                    break
                }
            }
            if (match) return preset.name
        }
        return null
    }

    // ─── Limits ───────────────────────────────────────────────────────────────

    /** Minimum gain in milli-bels from the device (or −1500 fallback) */
    fun getMinGainMb(): Int {
        return try {
            equalizer?.bandLevelRange?.get(0)?.toInt() ?: -1500
        } catch (_: Exception) { -1500 }
    }

    /** Maximum gain in milli-bels from the device (or +1500 fallback) */
    fun getMaxGainMb(): Int {
        return try {
            equalizer?.bandLevelRange?.get(1)?.toInt() ?: 1500
        } catch (_: Exception) { 1500 }
    }
}
