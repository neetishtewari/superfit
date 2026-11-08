import Shared
import SwiftUI
import UIKit

// Hosts Superfit's shared Compose screens (Compose Multiplatform) inside the iPhone app.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(auth: FirebaseAuthBridge())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
