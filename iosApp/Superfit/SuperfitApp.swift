import FirebaseAppCheck
import FirebaseCore
import SwiftUI

@main
struct SuperfitApp: App {
    init() {
        // App Check must be set before Firebase starts, so every Firebase call carries proof that
        // it comes from the real app. Same project and rules as the Android app.
        AppCheck.setAppCheckProviderFactory(SuperfitAppCheckFactory())
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// App Attest on real iPhones; the debug provider in Debug builds and the simulator, which can't
/// attest. A debug build prints its token in the Xcode console once; it is added in the Firebase
/// console (App Check > Apps > Superfit iPhone > Manage debug tokens), never in the repo.
final class SuperfitAppCheckFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        #if DEBUG || targetEnvironment(simulator)
            return AppCheckDebugProvider(app: app)
        #else
            return AppAttestProvider(app: app)
        #endif
    }
}
