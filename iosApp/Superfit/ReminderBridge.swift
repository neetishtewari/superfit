import Foundation
import Shared
import UserNotifications

/// Superfit's evening reminder on iPhone: Apple's local notifications, written ahead by shared
/// Kotlin code (ReminderPlan) because the iPhone doesn't let apps run at a set time. Nothing leaves
/// the phone. Tapping a reminder, or its "Say what you ate" button, opens Today listening, like
/// Android's reminder.
final class ReminderBridge: NSObject, IosReminders, UNUserNotificationCenterDelegate {
    static let shared = ReminderBridge()

    private static let prefix = "reminder-"
    private static let category = "SUPERFIT_REMINDER"
    private static let sayMeal = "SAY_MEAL"
    private let center = UNUserNotificationCenter.current()

    override private init() {
        super.init()
        center.delegate = self
        let say = UNNotificationAction(identifier: Self.sayMeal, title: "Say what you ate", options: [.foreground])
        center.setNotificationCategories([UNNotificationCategory(identifier: Self.category, actions: [say], intentIdentifiers: [])])
    }

    func ask(done: @escaping (KotlinBoolean) -> Void) {
        center.requestAuthorization(options: [.alert, .sound, .badge]) { [center] _, _ in
            center.getNotificationSettings { settings in
                let allowed = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
                DispatchQueue.main.async { done(KotlinBoolean(value: allowed)) }
            }
        }
    }

    func schedule(notes: [ReminderNote]) {
        removeScheduled { [center] in
            for note in notes {
                let content = UNMutableNotificationContent()
                content.title = note.title
                content.body = note.body
                content.sound = .default
                content.categoryIdentifier = Self.category
                let date = Date(timeIntervalSince1970: TimeInterval(note.atEpochSeconds))
                let when = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: date)
                let trigger = UNCalendarNotificationTrigger(dateMatching: when, repeats: false)
                center.add(UNNotificationRequest(identifier: note.id, content: content, trigger: trigger))
            }
        }
    }

    func cancelAll() {
        removeScheduled {}
    }

    /// Takes away every Superfit reminder still waiting, then carries on.
    private func removeScheduled(then next: @escaping () -> Void) {
        center.getPendingNotificationRequests { [center] pending in
            let mine = pending.map(\.identifier).filter { $0.hasPrefix(Self.prefix) }
            center.removePendingNotificationRequests(withIdentifiers: mine)
            next()
        }
    }

    // Shown even while Superfit is open.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if response.notification.request.content.categoryIdentifier == Self.category,
           response.actionIdentifier == UNNotificationDefaultActionIdentifier || response.actionIdentifier == Self.sayMeal {
            DispatchQueue.main.async { IosLaunch.shared.voice() }
        }
        completionHandler()
    }
}
