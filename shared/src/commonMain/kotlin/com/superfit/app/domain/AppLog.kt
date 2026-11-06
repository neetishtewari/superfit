package com.superfit.app.domain

/** Debug logging from shared code: Logcat on Android, the console elsewhere. */
expect object AppLog {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String, error: Throwable? = null)
}
