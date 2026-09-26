package com.superfit.app.ui.onboarding

internal fun speakUtterance(tts: android.speech.tts.TextToSpeech, text: String) {
    val voices = tts.voices
    if (!voices.isNullOrEmpty()) {
        val englishVoices = voices.filter { v -> v.locale.language.equals("en", ignoreCase = true) }

        // Filter out all known female voice identifiers in Google TTS, Samsung TTS, and other engines
        // Note: in Google TTS, "sfg", "iob", "tpc", "tpf", "iog", "fis" are female voices
        val femaleIdentifiers = listOf(
            "female", "woman", "sfg", "iob", "tpc", "tpf", "iog", "fis",
            "samantha", "victoria", "zira", "karen", "hazel", "aria", "jenny", "eva", "cora",
            "google us english" // Default Google TTS Voice 1 is female
        )

        val nonFemaleVoices = englishVoices.filter { v ->
            val name = v.name.lowercase()
            femaleIdentifiers.none { fid -> name.contains(fid) }
        }

        // Deep male trainer voice priorities:
        // Google TTS: "iom" (deep baritone male), "iol" (natural male), "tpd" (energetic male), "rjs" (UK male), "gbb" (UK male), "afh" (AU male)
        // General / Samsung: "male", "guy", "david", "mark", "alex", "daniel", "aaron"
        val selectedVoice = nonFemaleVoices.find { v ->
            val name = v.name.lowercase()
            name.contains("iom") && v.locale.country.equals("US", ignoreCase = true)
        } ?: nonFemaleVoices.find { v ->
            val name = v.name.lowercase()
            name.contains("iol") && v.locale.country.equals("US", ignoreCase = true)
        } ?: nonFemaleVoices.find { v ->
            val name = v.name.lowercase()
            name.contains("tpd") && v.locale.country.equals("US", ignoreCase = true)
        } ?: nonFemaleVoices.find { v ->
            val name = v.name.lowercase()
            (name.contains("male") || name.contains("guy")) && v.locale.country.equals("US", ignoreCase = true)
        } ?: nonFemaleVoices.find { v ->
            val name = v.name.lowercase()
            listOf("iom", "iol", "tpd", "rjs", "gbb", "afh", "male", "guy", "david", "alex", "daniel").any { kw -> name.contains(kw) }
        } ?: nonFemaleVoices.firstOrNull { v -> v.locale.country.equals("US", ignoreCase = true) }
        ?: nonFemaleVoices.firstOrNull()

        if (selectedVoice != null) {
            try {
                tts.voice = selectedVoice
                android.util.Log.d("SUPERFIT_TTS", "Selected male trainer voice: ${selectedVoice.name} (locale=${selectedVoice.locale})")
            } catch (e: Exception) {
                android.util.Log.e("SUPERFIT_TTS", "Failed to set voice", e)
            }
        } else {
            android.util.Log.w("SUPERFIT_TTS", "No male voice matched. Available voices: ${englishVoices.map { it.name }}")
        }
    }
    // Deeper pitch (0.88f) gives an authentic, rich baritone resonance typical of a fitness coach
    tts.setPitch(0.88f)
    // Confident, energetic conversational rate (0.98f)
    tts.setSpeechRate(0.98f)
    tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_ADD, null, "onboarding_chunk_${System.currentTimeMillis()}")
}
