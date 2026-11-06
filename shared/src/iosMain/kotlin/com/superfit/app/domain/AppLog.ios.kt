package com.superfit.app.domain

// The Xcode console. Debug text only: never meals, weights or other personal data.
actual object AppLog {
    actual fun d(tag: String, message: String) {
        println("D/$tag: $message")
    }

    actual fun w(tag: String, message: String, error: Throwable?) {
        println("W/$tag: $message" + (error?.let { " ($it)" } ?: ""))
    }
}
