import FirebaseRemoteConfig
import Foundation
import Shared

/// The Gemini models to try first come from Firebase Remote Config (parameter "ai_models"), so a
/// retired model can be swapped in the Firebase console without waiting for App Review. Same
/// parameter and rules as Android; shared AiFailover keeps the built-in list behind it.
enum RemoteConfigBridge {
    private static let modelsKey = "ai_models"

    static func loadAiModels() {
        let config = RemoteConfig.remoteConfig()
        let settings = RemoteConfigSettings()
        settings.minimumFetchInterval = 6 * 60 * 60
        config.configSettings = settings
        // The last fetched list applies at once; a fresh one applies when the fetch finishes.
        apply(config)
        config.fetchAndActivate { _, _ in apply(config) }
    }

    private static func apply(_ config: RemoteConfig) {
        AiFailover.shared.setRemoteModels(raw: config.configValue(forKey: modelsKey).stringValue)
    }
}
