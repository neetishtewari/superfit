import UIKit

/// Shows Apple's screens (photo picker, share sheet, short notices) on top of everything, including
/// Compose's own sheets and dialogs. Compose draws those above the app window's screens, so a picker
/// presented from the app window opens hidden behind the sheet. Each presentation gets its own
/// see-through window above the app, removed again when the screen closes.
enum OverlayPresenter {
    private static var windows: [UIWindow] = []

    /// Presents [screen] in a fresh top window. Call the returned closure once the screen has closed.
    @discardableResult
    static func present(_ screen: UIViewController) -> () -> Void {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        guard let scene = scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first else { return {} }
        let window = UIWindow(windowScene: scene)
        window.windowLevel = .alert + 1
        window.backgroundColor = .clear
        let host = UIViewController()
        host.view.backgroundColor = .clear
        window.rootViewController = host
        window.isHidden = false
        windows.append(window)
        // iPad shows share sheets as popovers; they need somewhere to point.
        screen.popoverPresentationController?.sourceView = host.view
        screen.popoverPresentationController?.sourceRect = CGRect(x: host.view.bounds.midX, y: host.view.bounds.midY, width: 0, height: 0)
        host.present(screen, animated: true)
        var closed = false
        return { [weak window] in
            guard !closed, let window else { return }
            closed = true
            window.isHidden = true
            windows.removeAll { $0 === window }
        }
    }
}
