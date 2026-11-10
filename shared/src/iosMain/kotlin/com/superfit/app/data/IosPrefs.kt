package com.superfit.app.data

import platform.Foundation.NSUserDefaults

/**
 * [Prefs] over a UserDefaults suite, one per account ("superfit_prefs_<uid>"), kept inside the
 * app's own container. Numbers are stored as NSNumber, so getInt and getLong read either.
 */
class IosPrefs(private val suiteName: String) : Prefs {
    private val defaults = NSUserDefaults(suiteName = suiteName)

    override fun getString(key: String, default: String?): String? = defaults.stringForKey(key) ?: default
    override fun getInt(key: String, default: Int): Int = if (contains(key)) defaults.integerForKey(key).toInt() else default
    override fun getLong(key: String, default: Long): Long = if (contains(key)) defaults.integerForKey(key) else default
    override fun getBoolean(key: String, default: Boolean): Boolean = if (contains(key)) defaults.boolForKey(key) else default

    override fun getStringSet(key: String): Set<String> =
        defaults.stringArrayForKey(key)?.filterIsInstance<String>()?.toSet().orEmpty()

    override fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    override fun clear() = defaults.removePersistentDomainForName(suiteName)

    override fun edit(block: PrefsEditor.() -> Unit) {
        object : PrefsEditor {
            override fun putString(key: String, value: String?) {
                if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
            }
            override fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), forKey = key)
            override fun putLong(key: String, value: Long) = defaults.setInteger(value, forKey = key)
            override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, forKey = key)
            override fun putStringSet(key: String, value: Set<String>) = defaults.setObject(value.sorted(), forKey = key)
            override fun remove(key: String) = defaults.removeObjectForKey(key)
        }.block()
    }

    companion object {
        /** The signed-in user's settings, named like Android's file. */
        fun forUser(uid: String) = IosPrefs("superfit_prefs_$uid")
    }
}
