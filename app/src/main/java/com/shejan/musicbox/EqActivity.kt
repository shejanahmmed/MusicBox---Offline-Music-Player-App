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

import android.os.Bundle
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Locale

class EqActivity : AppCompatActivity() {

    private lateinit var switchEqPower: SwitchMaterial
    private lateinit var tvEqStatus: TextView
    private lateinit var btnPresetCustom: AppCompatButton

    private val seekBars = ArrayList<SeekBar>()
    private val dbLabels = ArrayList<TextView>()

    private val presetButtons = HashMap<String, AppCompatButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_eq)

        // Apply WindowInsets for edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }

        // Header and Status
        val btnBack = findViewById<ImageButton>(R.id.btn_eq_back)
        btnBack.setOnClickListener { finish() }

        switchEqPower = findViewById(R.id.switch_eq_power)
        tvEqStatus = findViewById(R.id.tv_eq_status)

        // Find SeekBars and Labels
        seekBars.add(findViewById(R.id.sb_band0))
        seekBars.add(findViewById(R.id.sb_band1))
        seekBars.add(findViewById(R.id.sb_band2))
        seekBars.add(findViewById(R.id.sb_band3))
        seekBars.add(findViewById(R.id.sb_band4))

        dbLabels.add(findViewById(R.id.tv_db0))
        dbLabels.add(findViewById(R.id.tv_db1))
        dbLabels.add(findViewById(R.id.tv_db2))
        dbLabels.add(findViewById(R.id.tv_db3))
        dbLabels.add(findViewById(R.id.tv_db4))

        // Find Preset Buttons
        presetButtons["Flat"] = findViewById(R.id.btn_preset_flat)
        presetButtons["Rock"] = findViewById(R.id.btn_preset_rock)
        presetButtons["Pop"] = findViewById(R.id.btn_preset_pop)
        presetButtons["Jazz"] = findViewById(R.id.btn_preset_jazz)
        presetButtons["Classical"] = findViewById(R.id.btn_preset_classical)
        presetButtons["Bass Boost"] = findViewById(R.id.btn_preset_bass_boost)
        btnPresetCustom = findViewById(R.id.btn_preset_custom)

        // Setup power switch listener
        switchEqPower.isChecked = EqManager.isEnabled
        updateUIState(EqManager.isEnabled)

        switchEqPower.setOnCheckedChangeListener { _, isChecked ->
            EqManager.setEnabled(this, isChecked)
            updateUIState(isChecked)
            MusicUtils.performHapticFeedback(this)
        }

        // Setup SeekBars listeners
        setupSeekBarListeners()

        // Setup Preset Buttons listeners
        setupPresetListeners()
        btnPresetCustom.setOnClickListener {
            MusicUtils.performHapticFeedback(this)
        }

        // Load current EQ settings onto UI
        updateSlidersFromManager()
        highlightActivePreset()
    }

    private fun setupSeekBarListeners() {
        for (i in 0 until EqManager.BAND_COUNT) {
            seekBars[i].setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        // progress ranges 0..300. Map to -1500..+1500 mB
                        val gainMb = (progress - 150) * 10
                        EqManager.setBandGainMb(i, gainMb)
                        updateDbLabel(i, gainMb)
                        highlightActivePreset()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    EqManager.saveSettings(this@EqActivity)
                    MusicUtils.performHapticFeedback(this@EqActivity)
                }
            })
        }
    }

    private fun setupPresetListeners() {
        for ((presetName, button) in presetButtons) {
            button.setOnClickListener {
                EqManager.applyPreset(this, presetName)
                updateSlidersFromManager()
                highlightActivePreset()
                MusicUtils.performHapticFeedback(this)
            }
        }
    }

    private fun updateUIState(enabled: Boolean) {
        if (enabled) {
            tvEqStatus.setText(R.string.eq_status_on)
            tvEqStatus.setTextColor(ContextCompat.getColor(this, R.color.primary_red))
        } else {
            tvEqStatus.setText(R.string.eq_status_off)
            tvEqStatus.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary))
        }

        for (seekBar in seekBars) {
            seekBar.isEnabled = enabled
            seekBar.alpha = if (enabled) 1.0f else 0.4f
        }

        for (button in presetButtons.values) {
            button.isEnabled = enabled
            button.alpha = if (enabled) 1.0f else 0.4f
        }
        btnPresetCustom.isEnabled = enabled
        btnPresetCustom.alpha = if (enabled) 1.0f else 0.4f
    }

    private fun updateSlidersFromManager() {
        for (i in 0 until EqManager.BAND_COUNT) {
            val gainMb = EqManager.getBandGainMb(i)
            val progress = ((gainMb.coerceIn(-1500, 1500)) / 10) + 150
            seekBars[i].progress = progress
            updateDbLabel(i, gainMb)
        }
    }

    private fun updateDbLabel(bandIndex: Int, gainMb: Int) {
        val gainDb = gainMb / 100.0
        val formatted = when {
            gainDb > 0 -> String.format(Locale.US, "+%.1f dB", gainDb)
            gainDb < 0 -> String.format(Locale.US, "%.1f dB", gainDb)
            else -> "0.0 dB"
        }
        dbLabels[bandIndex].text = formatted
    }

    private fun highlightActivePreset() {
        val activePresetName = EqManager.getMatchingPresetName()
        for ((name, button) in presetButtons) {
            if (name == activePresetName) {
                button.setBackgroundResource(R.drawable.bg_button_primary)
                button.setTextColor(ContextCompat.getColor(this, R.color.white))
            } else {
                button.setBackgroundResource(R.drawable.bg_button_secondary)
                button.setTextColor(ContextCompat.getColor(this, R.color.colorTextPrimary))
            }
        }

        // Highlight Custom button if no preset matches
        if (activePresetName == null) {
            btnPresetCustom.setBackgroundResource(R.drawable.bg_button_primary)
            btnPresetCustom.setTextColor(ContextCompat.getColor(this, R.color.white))
        } else {
            btnPresetCustom.setBackgroundResource(R.drawable.bg_button_secondary)
            btnPresetCustom.setTextColor(ContextCompat.getColor(this, R.color.colorTextPrimary))
        }
    }
}
