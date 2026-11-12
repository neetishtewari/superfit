import Shared
import SwiftUI
import UIKit

// Hosts Superfit's shared Compose screens (Compose Multiplatform) inside the iPhone app.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            auth: FirebaseAuthBridge(), cloudStore: FirestoreCloudStore(), ai: FirebaseAiBridge(), health: HealthKitBridge(),
            speech: SpeechBridge(), camera: CameraBridge(), widget: WidgetBridge.shared, reminders: ReminderBridge.shared, report: ReportBridge()
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
