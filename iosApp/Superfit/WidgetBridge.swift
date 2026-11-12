import Foundation
import Shared
import WidgetKit

/// The home-screen widget's side of the app: keeps today's calorie numbers where the widget can
/// read them (the app group's shared settings, private to Superfit's own app and widget), and
/// checks that a mic or camera link really came from Superfit's widget before acting on it.
final class WidgetBridge: NSObject, IosWidget {
    static let shared = WidgetBridge()

    private let store = UserDefaults(suiteName: WidgetKeys.group)

    override private init() {
        super.init()
        // A random key, made once, that the widget puts in its mic and camera links. Another app
        // or a web page can open Superfit, but without this key it can't make it start listening
        // or open the camera.
        if store?.string(forKey: WidgetKeys.linkKey) == nil {
            store?.set(UUID().uuidString, forKey: WidgetKeys.linkKey)
        }
    }

    func publish(date: String, targetKcal: Int32, eatenKcal: Int32) {
        guard let store else { return }
        let unchanged = store.string(forKey: WidgetKeys.date) == date &&
            store.integer(forKey: WidgetKeys.target) == Int(targetKcal) &&
            store.integer(forKey: WidgetKeys.eaten) == Int(eatenKcal)
        if unchanged { return }
        store.set(date, forKey: WidgetKeys.date)
        store.set(Int(targetKcal), forKey: WidgetKeys.target)
        store.set(Int(eatenKcal), forKey: WidgetKeys.eaten)
        WidgetCenter.shared.reloadAllTimelines()
    }

    func clear() {
        guard let store, store.string(forKey: WidgetKeys.date) != nil else { return }
        store.removeObject(forKey: WidgetKeys.date)
        store.removeObject(forKey: WidgetKeys.target)
        store.removeObject(forKey: WidgetKeys.eaten)
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// Handles superfit:// links. The widget's mic and camera (with the right key) ask Today to
    /// listen or open the camera; any other Superfit link just opens the app. Returns false for
    /// links that aren't Superfit's (Google sign-in's), so they go on to their own handler.
    func handle(_ url: URL) -> Bool {
        guard url.scheme == WidgetKeys.scheme else { return false }
        let parts = URLComponents(url: url, resolvingAgainstBaseURL: false)
        let key = parts?.queryItems?.first { $0.name == "k" }?.value
        guard let key, let expected = store?.string(forKey: WidgetKeys.linkKey), key == expected else { return true }
        switch url.host {
        case "voice": IosLaunch.shared.voice()
        case "camera": IosLaunch.shared.camera()
        default: break
        }
        return true
    }
}
