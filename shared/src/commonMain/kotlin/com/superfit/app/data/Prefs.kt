package com.superfit.app.data

/**
 * Small settings kept on the phone for the signed-in user. On Android this is the user's
 * SharedPreferences file ("superfit_prefs_<uid>"), so existing settings are read as they are. On
 * iPhone it is a UserDefaults suite of the same name.
 *
 * Read a key with the same type it was written with, as Android requires.
 */
interface Prefs {
    fun getString(key: String, default: String?): String?
    fun getInt(key: String, default: Int): Int
    fun getLong(key: String, default: Long): Long
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getStringSet(key: String): Set<String>
    fun contains(key: String): Boolean

    /** Applies a group of changes together. */
    fun edit(block: PrefsEditor.() -> Unit)

    /** Removes every setting in this file (used when the account is deleted). */
    fun clear()
}

interface PrefsEditor {
    fun putString(key: String, value: String?)
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
    fun putBoolean(key: String, value: Boolean)
    fun putStringSet(key: String, value: Set<String>)
    fun remove(key: String)
}

/** An in-memory [Prefs] for tests and previews. */
class MemoryPrefs : Prefs {
    private val values = mutableMapOf<String, Any>()

    override fun getString(key: String, default: String?) = values[key] as? String ?: default
    override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
    override fun getLong(key: String, default: Long) = values[key] as? Long ?: default
    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String) = (values[key] as? Set<String>).orEmpty()
    override fun contains(key: String) = key in values
    override fun clear() = values.clear()

    override fun edit(block: PrefsEditor.() -> Unit) {
        object : PrefsEditor {
            override fun putString(key: String, value: String?) {
                if (value == null) values.remove(key) else values[key] = value
            }
            override fun putInt(key: String, value: Int) {
                values[key] = value
            }
            override fun putLong(key: String, value: Long) {
                values[key] = value
            }
            override fun putBoolean(key: String, value: Boolean) {
                values[key] = value
            }
            override fun putStringSet(key: String, value: Set<String>) {
                values[key] = value.toSet()
            }
            override fun remove(key: String) {
                values.remove(key)
            }
        }.block()
    }
}
