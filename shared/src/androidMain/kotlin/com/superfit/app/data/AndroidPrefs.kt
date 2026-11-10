package com.superfit.app.data

import android.content.SharedPreferences

/** [Prefs] over an Android SharedPreferences file; changes are written in the background. */
class AndroidPrefs(val shared: SharedPreferences) : Prefs {
    override fun getString(key: String, default: String?): String? = shared.getString(key, default)
    override fun getInt(key: String, default: Int): Int = shared.getInt(key, default)
    override fun getLong(key: String, default: Long): Long = shared.getLong(key, default)
    override fun getBoolean(key: String, default: Boolean): Boolean = shared.getBoolean(key, default)
    override fun getStringSet(key: String): Set<String> = shared.getStringSet(key, emptySet()).orEmpty().toSet()
    override fun contains(key: String): Boolean = shared.contains(key)
    override fun clear() = shared.edit().clear().apply()

    override fun edit(block: PrefsEditor.() -> Unit) {
        val editor = shared.edit()
        object : PrefsEditor {
            override fun putString(key: String, value: String?) {
                editor.putString(key, value)
            }
            override fun putInt(key: String, value: Int) {
                editor.putInt(key, value)
            }
            override fun putLong(key: String, value: Long) {
                editor.putLong(key, value)
            }
            override fun putBoolean(key: String, value: Boolean) {
                editor.putBoolean(key, value)
            }
            override fun putStringSet(key: String, value: Set<String>) {
                editor.putStringSet(key, value.toSet())
            }
            override fun remove(key: String) {
                editor.remove(key)
            }
        }.block()
        editor.apply()
    }
}
