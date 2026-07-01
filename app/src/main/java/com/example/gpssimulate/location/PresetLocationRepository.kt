package com.example.gpssimulate.location

import android.content.Context

class PresetLocationRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<PresetLocation> {
        val custom = prefs.getStringSet(KEY_CUSTOM_PRESETS, emptySet())
            .orEmpty()
            .mapNotNull { PresetLocation.fromStorageString(it) }
            .sortedBy { it.name }
        return DEFAULT_PRESETS + custom.filter { customPreset ->
            DEFAULT_PRESETS.none { it.name == customPreset.name }
        }
    }

    fun add(preset: PresetLocation) {
        val current = prefs.getStringSet(KEY_CUSTOM_PRESETS, emptySet()).orEmpty().toMutableSet()
        current.removeAll { stored ->
            PresetLocation.fromStorageString(stored)?.name == preset.name
        }
        current.add(preset.toStorageString())
        prefs.edit().putStringSet(KEY_CUSTOM_PRESETS, current).apply()
    }

    companion object {
        private const val PREFS_NAME = "preset_locations"
        private const val KEY_CUSTOM_PRESETS = "custom_presets"

        private val DEFAULT_PRESETS = listOf(
            PresetLocation(
                name = "苏州",
                latitude = 31.3167,
                longitude = 120.6167,
            ),
        )
    }
}
