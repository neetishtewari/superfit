package com.superfit.app.ui.report

/**
 * Where people get Superfit, named on share cards, in the shared text and in the report's footer:
 * e.g. "Get it on Google Play". A blank [url] shows the label without a link.
 */
data class StoreLink(val label: String, val url: String) {
    /** The words sent with a shared picture or report. */
    val shareText: String get() = if (url.isBlank()) "Tracked with Superfit" else "Tracked with Superfit $url"

    companion object {
        val GOOGLE_PLAY = StoreLink("Get it on Google Play", "https://play.google.com/store/apps/details?id=com.superfit.aifitness")
    }
}

/** This phone's store: Google Play on Android, the App Store on iPhone. */
expect val ThisAppStore: StoreLink
