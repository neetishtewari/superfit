import Shared
import SwiftUI

// First iPhone screen: shows numbers worked out by the shared Kotlin code, to prove the app
// links it. Replaced by the real screens in the next steps.
struct ContentView: View {
    var body: some View {
        VStack(spacing: 12) {
            Text("Superfit")
                .font(.largeTitle.bold())
            Text("Shared code says: \(Decimals.shared.one(x: 72.46)) kg")
            Text("Steps: \(LocalFormat.shared.integer(n: 8432))")
        }
        .padding()
    }
}
