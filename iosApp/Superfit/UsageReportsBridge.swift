import FirebaseAnalytics
import FirebaseCrashlytics
import Foundation
import Shared

/// Anonymous usage counts (Firebase Analytics) and crash reports (Crashlytics) on iPhone, the same
/// events and the same Settings › Privacy switch as Android. Both start switched off (Info.plist)
/// and only turn on once this has checked the switch; Debug builds (the iPhone's dev app) never
/// report. Events carry only how something was done or which screen was opened, never food,
/// numbers, health data, names or email (see UsageEvents.kt).
final class UsageReportsBridge: NSObject, IosUsageReports {
    static let shared = UsageReportsBridge()
    private static let enabledKey = "usage_reports_enabled"

    override private init() {
        super.init()
        apply()
    }

    func isEnabled() -> Bool {
        UserDefaults.standard.object(forKey: Self.enabledKey) as? Bool ?? true
    }

    func setEnabled(on: Bool) {
        UserDefaults.standard.set(on, forKey: Self.enabledKey)
        apply()
    }

    private var active: Bool {
        #if DEBUG
            return false
        #else
            return isEnabled()
        #endif
    }

    private func apply() {
        Analytics.setAnalyticsCollectionEnabled(active)
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(active)
    }

    func log(event: String, params: [String: String]) {
        guard active else { return }
        Analytics.logEvent(event, parameters: params)
    }

    func recordError(name: String, message: String, stack: [String]) {
        guard active else { return }
        let model = ExceptionModel(name: name, reason: message)
        model.stackTrace = stack.map { StackFrame(symbol: $0, file: "", line: 0) }
        Crashlytics.crashlytics().record(exceptionModel: model)
    }

    func setKey(name: String, value: String) {
        guard active else { return }
        Crashlytics.crashlytics().setCustomValue(value, forKey: name)
    }
}
