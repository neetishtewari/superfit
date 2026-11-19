import SwiftUI
import WidgetKit

/// Superfit's home-screen and lock-screen widget: today's calories left, like Android's widget,
/// with mic and camera buttons (medium size) that open Superfit ready to log a meal.
@main
struct SuperfitWidgetBundle: WidgetBundle {
    var body: some Widget {
        CaloriesWidget()
    }
}

struct CaloriesWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "SuperfitCalories", provider: CaloriesProvider()) { entry in
            CaloriesWidgetView(entry: entry)
        }
        .configurationDisplayName("Calories left")
        .description("Today's calories left, with quick buttons to log a meal by voice or camera.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryInline])
    }
}

struct CaloriesEntry: TimelineEntry {
    let date: Date
    let text: WidgetText
    /// The key for the mic and camera links; nil until Superfit has been opened once.
    let linkKey: String?
}

/// "1,240 kcal left" / "of 2,100", the same words as Android's widget. A day other than today
/// means nothing eaten yet today.
struct WidgetText {
    let headline: String
    let subline: String?

    static let placeholder = WidgetText(headline: "1,240 kcal left", subline: "of 2,100")

    static func from(store: UserDefaults?, on day: Date) -> WidgetText {
        guard let store, let date = store.string(forKey: WidgetKeys.date) else {
            return WidgetText(headline: "Open Superfit to start", subline: nil)
        }
        let target = store.integer(forKey: WidgetKeys.target)
        guard target > 0 else { return WidgetText(headline: "Open Superfit to start", subline: nil) }
        let eaten = date == dayString(day) ? store.integer(forKey: WidgetKeys.eaten) : 0
        let left = target - eaten
        let headline = left >= 0 ? "\(number(left)) kcal left" : "\(number(-left)) kcal over"
        return WidgetText(headline: headline, subline: "of \(number(target))")
    }

    private static func number(_ value: Int) -> String {
        let format = NumberFormatter()
        format.numberStyle = .decimal
        format.locale = Locale(identifier: "en_US")
        return format.string(from: NSNumber(value: value)) ?? "\(value)"
    }

    /// The phone's date as yyyy-MM-dd, the way the app saves it.
    private static func dayString(_ day: Date) -> String {
        let format = DateFormatter()
        format.calendar = Calendar(identifier: .gregorian)
        format.locale = Locale(identifier: "en_US_POSIX")
        format.timeZone = .current
        format.dateFormat = "yyyy-MM-dd"
        return format.string(from: day)
    }
}

struct CaloriesProvider: TimelineProvider {
    func placeholder(in context: Context) -> CaloriesEntry {
        CaloriesEntry(date: .now, text: .placeholder, linkKey: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (CaloriesEntry) -> Void) {
        completion(context.isPreview ? placeholder(in: context) : entry(at: .now))
    }

    /// Now, then again at midnight (a new day starts at 0 eaten). The app asks for a new timeline
    /// whenever the numbers change.
    func getTimeline(in context: Context, completion: @escaping (Timeline<CaloriesEntry>) -> Void) {
        let now = Date.now
        let midnight = Calendar.current.startOfDay(for: now.addingTimeInterval(24 * 60 * 60))
        completion(Timeline(entries: [entry(at: now), entry(at: midnight)], policy: .after(midnight)))
    }

    private func entry(at date: Date) -> CaloriesEntry {
        let store = UserDefaults(suiteName: WidgetKeys.group)
        return CaloriesEntry(
            date: date,
            text: WidgetText.from(store: store, on: date),
            linkKey: store?.string(forKey: WidgetKeys.linkKey)
        )
    }
}

private let cardColor = Color(red: 0x1C / 255, green: 0x1C / 255, blue: 0x2B / 255)
private let brandGreen = Color(red: 0x0E / 255, green: 0x9F / 255, blue: 0x75 / 255)
private let secondaryText = Color(red: 0xB4 / 255, green: 0xB4 / 255, blue: 0xC8 / 255)

struct CaloriesWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: CaloriesEntry

    var body: some View {
        switch family {
        case .accessoryInline:
            Text(entry.text.headline)
                .containerBackground(.clear, for: .widget)
        case .accessoryRectangular:
            VStack(alignment: .leading) {
                Text("Superfit").font(.caption2).foregroundStyle(.secondary)
                Text(entry.text.headline).font(.headline).widgetAccentable()
                if let subline = entry.text.subline { Text(subline).font(.caption) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .containerBackground(.clear, for: .widget)
        case .systemMedium:
            HStack(spacing: 12) {
                numbers
                Spacer(minLength: 0)
                LogButton(symbol: "mic.fill", label: "Log by voice", url: link("voice"))
                LogButton(symbol: "camera.fill", label: "Log with camera", url: link("camera"))
            }
            .padding(.horizontal, 4)
            .containerBackground(cardColor, for: .widget)
            .widgetURL(link(nil))
        default:
            VStack(alignment: .leading, spacing: 6) {
                numbers
                Spacer(minLength: 0)
                Label("Tap to log", systemImage: "plus.circle.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(brandGreen)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .containerBackground(cardColor, for: .widget)
            .widgetURL(link(nil))
        }
    }

    @ViewBuilder private var numbers: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let subline = entry.text.subline {
                Text("Today").font(.caption).foregroundStyle(secondaryText)
                // "1,240" large, "kcal left" under it, so it fits the small size too.
                let parts = entry.text.headline.split(separator: " ", maxSplits: 1).map(String.init)
                Text(parts.first ?? "").font(.title.weight(.bold)).foregroundStyle(.white)
                    .minimumScaleFactor(0.6).lineLimit(1)
                Text(parts.count > 1 ? parts[1] : "").font(.caption).foregroundStyle(secondaryText)
                Text(subline).font(.caption2).foregroundStyle(secondaryText)
            } else {
                Text(entry.text.headline).font(.headline).foregroundStyle(.white).lineLimit(3)
            }
        }
    }

    /// superfit://voice?k=… for the mic, superfit://camera?k=… for the camera, superfit://open for
    /// the rest of the widget (just opens the app).
    private func link(_ action: String?) -> URL {
        guard let action, let key = entry.linkKey else { return URL(string: "\(WidgetKeys.scheme)://open")! }
        var parts = URLComponents()
        parts.scheme = WidgetKeys.scheme
        parts.host = action
        parts.queryItems = [URLQueryItem(name: "k", value: key)]
        return parts.url ?? URL(string: "\(WidgetKeys.scheme)://open")!
    }
}

private struct LogButton: View {
    let symbol: String
    let label: String
    let url: URL

    var body: some View {
        Link(destination: url) {
            Image(systemName: symbol)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 48, height: 48)
                .background(Circle().fill(brandGreen))
        }
        .accessibilityLabel(label)
    }
}
