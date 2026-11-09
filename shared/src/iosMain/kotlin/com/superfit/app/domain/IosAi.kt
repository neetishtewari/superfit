package com.superfit.app.domain

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * One Gemini call through Firebase AI Logic, made by the iPhone's Swift code (Firebase's iPhone
 * SDK, protected by App Check like Android). Retries and model failover are shared (AiFailover),
 * so Swift only makes the single call and reports the text, or the error's description.
 */
interface IosAi {
    fun generate(model: String, prompt: String, systemInstruction: String?, isJson: Boolean, jpeg: NSData?, done: (String?, String?) -> Unit)

    /** [historyRoles] is "user" or "model" for each of [historyTexts]. */
    fun chat(
        model: String,
        systemInstruction: String,
        historyRoles: List<String>,
        historyTexts: List<String>,
        message: String,
        done: (String?, String?) -> Unit
    )
}

/** A Gemini error as Swift described it, for AiFailover to sort into busy, rate limit and so on. */
class IosAiException(message: String) : Exception(message)

/** Plugs the iPhone's Swift AI into shared code (Ai), the way Android's app does at start-up. */
fun installIosAi(ios: IosAi) {
    Ai.text = AiText { prompt, system, isJson ->
        AiFailover.run { model -> call { ios.generate(model, prompt, system, isJson, null, it) } }
    }
    Ai.photoText = AiPhotoText { photo, prompt, system, isJson ->
        val jpeg = photo.jpeg.toNSData()
        AiFailover.run { model -> call { ios.generate(model, prompt, system, isJson, jpeg, it) } }
    }
    Ai.chatText = AiChat { system, history, message ->
        val roles = history.map { if (it.fromUser) "user" else "model" }
        val texts = history.map { it.text }
        AiFailover.run { model -> call { ios.chat(model, system, roles, texts, message, it) } }
    }
    Ai.friendlyError = { AiFailover.sanitizeError(it) }
}

private suspend fun call(start: ((String?, String?) -> Unit) -> Unit): String? = suspendCancellableCoroutine { c ->
    start { text, error -> if (error != null) c.resumeWithException(IosAiException(error)) else c.resume(text) }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
