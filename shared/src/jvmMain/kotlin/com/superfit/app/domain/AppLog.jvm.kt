package com.superfit.app.domain

actual object AppLog {
    actual fun d(tag: String, message: String) {
        println("D/$tag: $message")
    }

    actual fun w(tag: String, message: String, error: Throwable?) {
        println("W/$tag: $message" + (error?.let { " ($it)" } ?: ""))
    }
}
