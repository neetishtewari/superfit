import PhotosUI
import Shared
import UIKit

/// Share cards on iPhone: Apple's photo picker for the card's background photo, and Apple's share
/// sheet for the finished picture. The picker hands over only the photo picked, so no photo-library
/// access is asked. The picture is made on the phone, written to the app's own temporary folder
/// (replaced each time, protected while the phone is locked) and only leaves where the person sends it.
final class ShareBridge: NSObject, IosShareCards {
    private var picked: ((Data?) -> Void)?

    func pickPhoto(maxEdge: Int32, done: @escaping (Data?) -> Void) {
        guard let top = Self.topViewController() else { return }
        var config = PHPickerConfiguration()
        config.filter = .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = self
        picked = { data in done(data) }
        self.maxEdge = CGFloat(maxEdge)
        top.present(picker, animated: true)
    }

    private var maxEdge: CGFloat = 2560

    func sharePicture(png: Data, text: String) {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("share-cards", isDirectory: true)
        try? FileManager.default.removeItem(at: dir)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let file = dir.appendingPathComponent("superfit-\(Int(Date().timeIntervalSince1970 * 1000)).png")
        do {
            try png.write(to: file, options: .completeFileProtection)
        } catch {
            notice(message: "Couldn't make the picture. Please try again.")
            return
        }
        let sheet = UIActivityViewController(activityItems: [file, text], applicationActivities: nil)
        guard let top = Self.topViewController() else { return }
        // iPad shows the sheet as a popover; it needs somewhere to point.
        sheet.popoverPresentationController?.sourceView = top.view
        sheet.popoverPresentationController?.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY, width: 0, height: 0)
        top.present(sheet, animated: true)
    }

    func notice(message: String) {
        guard let top = Self.topViewController() else { return }
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        top.present(alert, animated: true)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) { [weak alert] in
            alert?.dismiss(animated: true)
        }
    }

    fileprivate func deliver(_ image: UIImage?) {
        let done = picked
        picked = nil
        guard let image else {
            done?(nil)
            return
        }
        done?(Self.shrunk(image, maxEdge: maxEdge).jpegData(compressionQuality: 0.9))
    }

    /// Upright and at most [maxEdge] on its longest side, so it stays sharp when zoomed on the card.
    private static func shrunk(_ image: UIImage, maxEdge: CGFloat) -> UIImage {
        let longest = max(image.size.width, image.size.height)
        let scale = longest > maxEdge ? maxEdge / longest : 1
        let size = CGSize(width: (image.size.width * scale).rounded(), height: (image.size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
    }

    /// The app's own window, not a Compose dialog's (see CameraBridge).
    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
        let window = scene?.windows.first { $0.windowLevel == .normal && !$0.isHidden } ?? scene?.windows.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController, !presented.isBeingDismissed { top = presented }
        return top
    }
}

extension ShareBridge: PHPickerViewControllerDelegate {
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        // Cancelled: no answer, the card stays as it was.
        guard let provider = results.first?.itemProvider else {
            picked = nil
            return
        }
        guard provider.canLoadObject(ofClass: UIImage.self) else { return deliver(nil) }
        provider.loadObject(ofClass: UIImage.self) { [weak self] object, _ in
            DispatchQueue.main.async { self?.deliver(object as? UIImage) }
        }
    }
}
