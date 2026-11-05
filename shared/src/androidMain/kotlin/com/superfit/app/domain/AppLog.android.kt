package com.superfit.app.domain

import android.util.Log

actual object AppLog {
    actual fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    actual fun w(tag: String, message: String, error: Throwable?) {
        Log.w(tag, message, error)
    }
}
