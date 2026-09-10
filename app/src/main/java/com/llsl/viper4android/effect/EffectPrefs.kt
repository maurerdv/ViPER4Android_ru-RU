package com.llsl.viper4android.effect

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.llsl.viper4android.data.repository.ViperRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

sealed class EffectPref<T>(
    val effectKey: String,
    val paramId: Int,
    val jsonKey: String,
    val defaultValue: T,
    val get: (EffectState) -> T,
    val set: EffectState.(T) -> EffectState,
    prefKeyOverride: String? = null,
) {
    val prefKey: String =
        prefKeyOverride
            ?: if (paramId != -1) {
                paramId.toString()
            } else if (effectKey.isEmpty()) {
                jsonKey
            } else {
                "${effectKey}_$jsonKey"
            }
}

class IntPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: Int,
    get: (EffectState) -> Int,
    set: EffectState.(Int) -> EffectState,
    val range: IntRange? = null,
) : EffectPref<Int>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    fun clamp(value: Int): Int = range?.let { value.coerceIn(it) } ?: value
}

class FloatPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: Float,
    get: (EffectState) -> Float,
    set: EffectState.(Float) -> EffectState,
    val range: ClosedFloatingPointRange<Float>? = null,
) : EffectPref<Float>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    fun clamp(value: Float): Float = range?.let { value.coerceIn(it) } ?: value
}

class BoolPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: Boolean,
    get: (EffectState) -> Boolean,
    set: EffectState.(Boolean) -> EffectState,
    prefKeyOverride: String? = null,
) : EffectPref<Boolean>(effectKey, paramId, jsonKey, defaultValue, get, set, prefKeyOverride)

class StringPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: String,
    get: (EffectState) -> String,
    set: EffectState.(String) -> EffectState,
) : EffectPref<String>(effectKey, paramId, jsonKey, defaultValue, get, set)

class NullableLongPref(
    effectKey: String,
    jsonKey: String,
    get: (EffectState) -> Long?,
    set: EffectState.(Long?) -> EffectState,
) : EffectPref<Long?>(effectKey, -1, jsonKey, null, get, set)

sealed class ListPref<E>(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: List<E>,
    get: (EffectState) -> List<E>,
    set: EffectState.(List<E>) -> EffectState,
) : EffectPref<List<E>>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    abstract val padValue: E
}

class IntListPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: List<Int>,
    get: (EffectState) -> List<Int>,
    set: EffectState.(List<Int>) -> EffectState,
    val range: IntRange? = null,
) : ListPref<Int>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    override val padValue: Int = 0

    fun clampElement(value: Int): Int = range?.let { value.coerceIn(it) } ?: value
}

class FloatListPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: List<Float>,
    get: (EffectState) -> List<Float>,
    set: EffectState.(List<Float>) -> EffectState,
    val range: ClosedFloatingPointRange<Float>? = null,
) : ListPref<Float>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    override val padValue: Float = 0.0f

    fun clampElement(value: Float): Float = range?.let { value.coerceIn(it) } ?: value
}

class BoolListPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: List<Boolean>,
    get: (EffectState) -> List<Boolean>,
    set: EffectState.(List<Boolean>) -> EffectState,
) : ListPref<Boolean>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    override val padValue: Boolean = true
}

class DoubleListPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: List<Double>,
    get: (EffectState) -> List<Double>,
    set: EffectState.(List<Double>) -> EffectState,
    val range: ClosedFloatingPointRange<Double>? = null,
) : EffectPref<List<Double>>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    fun clampElement(value: Double): Double = range?.let { value.coerceIn(it) } ?: value

    fun toFloatArray(value: List<Double>): FloatArray = value.map { it.toFloat() }.toFloatArray()
}

val EFFECT_PREFS: List<EffectPref<*>> =
    listOf(Effects.masterEnable) + EFFECT_GROUPS.flatMap { it.prefs }

val EFFECT_PREFS_BY_PARAM_ID: Map<Int, EffectPref<*>> =
    EFFECT_PREFS
        .filter {
            it.paramId != -1 &&
                it !is IntListPref &&
                it !is FloatListPref &&
                it !is BoolListPref &&
                it !is DoubleListPref
        }.associateBy { it.paramId }

val ENABLE_PREF_BY_EFFECT_KEY: Map<String, BoolPref> =
    EFFECT_GROUPS
        .mapNotNull { group ->
            val enable = group.prefs.firstOrNull { it.jsonKey == "enable" } as? BoolPref
            enable?.let { group.effectKey to it }
        }.toMap()

private fun spJoinInts(list: List<Int>): String = list.joinToString(";")

private fun spJoinFloats(list: List<Float>): String =
    list.joinToString(";") {
        String.format(Locale.US, "%.4f", it)
    }

private fun spSplitInts(
    s: String,
    default: List<Int>,
): List<Int> {
    if (s.isBlank()) return default
    val parts = s.split(";").filter { it.isNotBlank() }
    if (parts.isEmpty()) return default
    return parts.mapNotNull { it.toIntOrNull() }
}

private fun spSplitFloats(
    s: String,
    default: List<Float>,
): List<Float> {
    if (s.isBlank()) return default
    val parts = s.split(";").filter { it.isNotBlank() }
    if (parts.isEmpty()) return default
    return parts.mapNotNull { it.toFloatOrNull() }
}

private fun spJoinBools(list: List<Boolean>): String = list.joinToString(";") { if (it) "1" else "0" }

private fun spSplitBools(
    s: String,
    default: List<Boolean>,
): List<Boolean> {
    if (s.isBlank()) return default
    val parts = s.split(";").filter { it.isNotBlank() }
    if (parts.isEmpty()) return default
    return parts.map { it == "1" }
}

private fun spJoinDoubles(list: List<Double>): String =
    list.joinToString(";") {
        String.format(Locale.US, "%.1f", it)
    }

private fun spSplitDoubles(
    s: String,
    default: List<Double>,
): List<Double> {
    if (s.isBlank()) return default
    val parts = s.split(";").filter { it.isNotBlank() }
    if (parts.isEmpty()) return default
    return parts.mapNotNull { it.toDoubleOrNull() }
}

suspend fun loadEffectPrefs(
    repository: ViperRepository,
    state: EffectState = EffectState(),
): EffectState {
    var s = state
    for (pref in EFFECT_PREFS) {
        s =
            when (pref) {
                is IntPref -> {
                    pref.set(s, repository.getIntPreference(pref.prefKey, pref.defaultValue).first())
                }

                is FloatPref -> {
                    pref.set(s, repository.getFloatPreference(pref.prefKey, pref.defaultValue).first())
                }

                is BoolPref -> {
                    pref.set(s, repository.getBooleanPreference(pref.prefKey, pref.defaultValue).first())
                }

                is StringPref -> {
                    pref.set(s, repository.getStringPreference(pref.prefKey, pref.defaultValue).first())
                }

                is NullableLongPref -> {
                    val raw = repository.getIntPreference(pref.prefKey, -1).first()
                    pref.set(s, if (raw < 0) null else raw.toLong())
                }

                is IntListPref -> {
                    val raw = repository.getStringPreference(pref.prefKey, spJoinInts(pref.defaultValue)).first()
                    pref.set(s, spSplitInts(raw, pref.defaultValue))
                }

                is FloatListPref -> {
                    val raw = repository.getStringPreference(pref.prefKey, spJoinFloats(pref.defaultValue)).first()
                    pref.set(s, spSplitFloats(raw, pref.defaultValue))
                }

                is BoolListPref -> {
                    val raw = repository.getStringPreference(pref.prefKey, spJoinBools(pref.defaultValue)).first()
                    pref.set(s, spSplitBools(raw, pref.defaultValue))
                }

                is DoubleListPref -> {
                    val raw = repository.getStringPreference(pref.prefKey, spJoinDoubles(pref.defaultValue)).first()
                    pref.set(s, spSplitDoubles(raw, pref.defaultValue))
                }
            }
    }
    return s
}

suspend fun saveEffectPrefs(
    repository: ViperRepository,
    state: EffectState,
) {
    repository.editPreferences { prefs ->
        for (pref in EFFECT_PREFS) {
            when (pref) {
                is IntPref -> prefs[intPreferencesKey(pref.prefKey)] = pref.get(state)
                is FloatPref -> prefs[floatPreferencesKey(pref.prefKey)] = pref.get(state)
                is BoolPref -> prefs[booleanPreferencesKey(pref.prefKey)] = pref.get(state)
                is StringPref -> prefs[stringPreferencesKey(pref.prefKey)] = pref.get(state)
                is NullableLongPref -> prefs[intPreferencesKey(pref.prefKey)] = pref.get(state)?.toInt() ?: -1
                is IntListPref -> prefs[stringPreferencesKey(pref.prefKey)] = spJoinInts(pref.get(state))
                is FloatListPref -> prefs[stringPreferencesKey(pref.prefKey)] = spJoinFloats(pref.get(state))
                is BoolListPref -> prefs[stringPreferencesKey(pref.prefKey)] = spJoinBools(pref.get(state))
                is DoubleListPref -> prefs[stringPreferencesKey(pref.prefKey)] = spJoinDoubles(pref.get(state))
            }
        }
    }
}

const val PRESET_SCHEMA_VERSION = 2
private const val KEY_SCHEMA_VERSION = "schemaVersion"
private const val KEY_NAME = "name"
private const val KEY_CREATED_AT = "createdAt"

fun serializeEffectPrefs(state: EffectState): JSONObject = serializeEffectPrefs(state, name = null, createdAt = null)

fun serializeEffectPrefs(
    state: EffectState,
    name: String?,
    createdAt: Long?,
): JSONObject {
    val root = JSONObject()
    root.put(KEY_SCHEMA_VERSION, PRESET_SCHEMA_VERSION)
    if (name != null) root.put(KEY_NAME, name)
    if (createdAt != null) root.put(KEY_CREATED_AT, createdAt)
    for (group in EFFECT_GROUPS) {
        val obj = JSONObject()
        for (pref in group.prefs) {
            putPrefValue(obj, pref, state)
        }
        root.put(group.effectKey, obj)
    }
    return root
}

private fun putPrefValue(
    obj: JSONObject,
    pref: EffectPref<*>,
    state: EffectState,
) {
    when (pref) {
        is IntPref -> {
            obj.put(pref.jsonKey, pref.get(state))
        }

        is FloatPref -> {
            obj.put(pref.jsonKey, pref.get(state).toDouble())
        }

        is BoolPref -> {
            obj.put(pref.jsonKey, pref.get(state))
        }

        is StringPref -> {
            obj.put(pref.jsonKey, pref.get(state))
        }

        is NullableLongPref -> {
            val v = pref.get(state)
            if (v == null) obj.put(pref.jsonKey, JSONObject.NULL) else obj.put(pref.jsonKey, v)
        }

        is IntListPref -> {
            val arr = JSONArray()
            for (v in pref.get(state)) arr.put(v)
            obj.put(pref.jsonKey, arr)
        }

        is FloatListPref -> {
            val arr = JSONArray()
            for (v in pref.get(state)) arr.put(v.toDouble())
            obj.put(pref.jsonKey, arr)
        }

        is BoolListPref -> {
            val arr = JSONArray()
            for (v in pref.get(state)) arr.put(v)
            obj.put(pref.jsonKey, arr)
        }

        is DoubleListPref -> {
            val arr = JSONArray()
            for (v in pref.get(state)) arr.put(v)
            obj.put(pref.jsonKey, arr)
        }
    }
}

fun deserializeEffectPrefs(
    obj: JSONObject,
    state: EffectState,
): EffectState {
    val sourceSchema = obj.optDouble(KEY_SCHEMA_VERSION, 0.0)
    var s = state
    for (group in EFFECT_GROUPS) {
        val sub = obj.optJSONObject(group.effectKey) ?: continue
        for (pref in group.prefs) {
            s = applyPrefFromJson(s, pref, sub)
        }
    }
    return s
}

private fun applyPrefFromJson(
    state: EffectState,
    pref: EffectPref<*>,
    obj: JSONObject,
): EffectState {
    if (!obj.has(pref.jsonKey)) return state
    return when (pref) {
        is IntPref -> {
            pref.set(state, pref.clamp(obj.optInt(pref.jsonKey, pref.get(state))))
        }

        is FloatPref -> {
            pref.set(state, pref.clamp(obj.optDouble(pref.jsonKey, pref.get(state).toDouble()).toFloat()))
        }

        is BoolPref -> {
            pref.set(state, obj.optBoolean(pref.jsonKey, pref.get(state)))
        }

        is StringPref -> {
            pref.set(state, obj.optString(pref.jsonKey, pref.get(state)))
        }

        is NullableLongPref -> {
            val v =
                if (obj.isNull(pref.jsonKey)) {
                    null
                } else {
                    val raw = obj.optInt(pref.jsonKey, -1)
                    if (raw < 0) null else raw.toLong()
                }
            pref.set(state, v)
        }

        is IntListPref -> {
            val arr = obj.optJSONArray(pref.jsonKey) ?: return state
            val list = mutableListOf<Int>()
            for (i in 0 until arr.length()) list.add(pref.clampElement(arr.optInt(i, 0)))
            pref.set(state, list.toList())
        }

        is FloatListPref -> {
            val arr = obj.optJSONArray(pref.jsonKey) ?: return state
            val list = mutableListOf<Float>()
            for (i in 0 until arr.length()) {
                val raw = arr.optDouble(i, 0.0).toFloat()
                list.add(pref.clampElement(arr.optDouble(i, 0.0).toFloat()))
            }
            pref.set(state, list.toList())
        }

        is BoolListPref -> {
            val arr = obj.optJSONArray(pref.jsonKey) ?: return state
            val list = mutableListOf<Boolean>()
            for (i in 0 until arr.length()) list.add(arr.optBoolean(i, false))
            pref.set(state, list.toList())
        }

        is DoubleListPref -> {
            val arr = obj.optJSONArray(pref.jsonKey) ?: return state
            val list = mutableListOf<Double>()
            for (i in 0 until arr.length()) list.add(pref.clampElement(arr.optDouble(i, 0.0)))
            pref.set(state, list.toList())
        }
    }
}
