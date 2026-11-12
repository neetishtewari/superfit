import FirebaseAppCheck
import FirebaseCore
import GoogleSignIn
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
                // The widget's links (superfit://) first; Google's sign-in sheet comes back to the
                // app through its own link.
                .onOpenURL { url in
                    if WidgetBridge.shared.handle(url) { return }
                    GIDSignIn.sharedInstance.handle(url)
                }
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
