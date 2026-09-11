package com.llsl.viper4android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.llsl.viper4android.data.model.DsPreset
import com.llsl.viper4android.data.model.EqPreset
import com.llsl.viper4android.effect.BuiltinPresets

@Composable
fun resolvePresetName(preset: EqPreset): String {
    val resId = preset.nameKey?.let { BuiltinPresets.EQ_PRESET_NAME_RES[it] }
    return if (resId != null) stringResource(resId) else preset.name
}

@Composable
fun resolvePresetName(preset: DsPreset): String {
    val resId = preset.nameKey?.let { BuiltinPresets.DS_PRESET_NAME_RES[it] }
    return if (resId != null) stringResource(resId) else preset.name
}
