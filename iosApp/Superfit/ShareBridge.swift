import PhotosUI
import Shared
import UIKit

/// Share cards on iPhone: Apple's photo picker for the card's background photo, and Apple's share
/// sheet for the finished picture. The picker hands over only the photo picked, so no photo-library
/// access is asked. The picture is made on the phone, written to the app's own temporary folder
/// (replaced each time, protected while the phone is locked) and only leaves where the person sends it.
final class ShareBridge: NSObject, IosShareCards {
    private var picked: ((Data?) -> Void)?
    private var closePicker: (() -> Void)?

    func pickPhoto(maxEdge: Int32, done: @escaping (Data?) -> Void) {
        var config = PHPickerConfiguration()
        config.filter = .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = self
        picked = { data in done(data) }
        self.maxEdge = CGFloat(maxEdge)
        closePicker = OverlayPresenter.present(picker)
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
        let close = OverlayPresenter.present(sheet)
        sheet.completionWithItemsHandler = { _, _, _, _ in close() }
    }

    func notice(message: String) {
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        let close = OverlayPresenter.present(alert)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) { [weak alert] in
            guard let alert else { return close() }
            alert.dismiss(animated: true, completion: close)
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
}

extension ShareBridge: PHPickerViewControllerDelegate {
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        let close = closePicker
        closePicker = nil
        picker.dismiss(animated: true) { close?() }
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
