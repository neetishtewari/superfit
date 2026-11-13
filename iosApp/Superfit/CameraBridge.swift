import AVFoundation
import Foundation
import PhotosUI
import Shared
import UIKit
import VisionKit

/// The iPhone's camera screens for shared Kotlin code (PhotoLog): Apple's camera for a meal or a
/// nutrition label, and Apple's live barcode scanner for packets. Photos are shrunk and handed
/// over as JPEG; nothing is saved to the phone's photos or anywhere else. Answers come back on the
/// main thread, once each.
final class CameraBridge: NSObject, IosCamera {
    private static let longestSide: CGFloat = 1280
    private static let userAgent = "Superfit iPhone - https://github.com/neetishtewari/superfit"

    private var done: ((Data?, String?, String?) -> Void)?
    private var scannedOnce = false

    func isAvailable() -> Bool {
        // The simulator has no camera; it picks a photo from its library instead, for testing.
        UIImagePickerController.isSourceTypeAvailable(.camera) || Self.isSimulator
    }

    func take(kind: String, done: @escaping (Data?, String?, String?) -> Void) {
        self.done = done
        askCamera { [weak self] allowed in
            guard let self else { return }
            guard allowed else {
                self.answer(nil, nil, "The camera is off for Superfit. Turn it on in Settings › Superfit, or type your meal instead.")
                return
            }
            // Answers always arrive on the main thread (see askCamera).
            if kind == "barcode" { MainActor.assumeIsolated { self.scanBarcode() } } else { self.snapPhoto() }
        }
    }

    func fetch(url: String, done: @escaping (String?, String?) -> Void) {
        guard let address = URL(string: url) else { return done(nil, "Bad address") }
        var request = URLRequest(url: address, timeoutInterval: 8)
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        URLSession.shared.dataTask(with: request) { data, response, error in
            DispatchQueue.main.async {
                if let error { return done(nil, error.localizedDescription) }
                let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                switch status {
                case 200: done(String(data: data ?? Data(), encoding: .utf8) ?? "", nil)
                case 404: done(nil, nil)
                default: done(nil, "The product database returned HTTP \(status)")
                }
            }
        }.resume()
    }

    // MARK: - Camera permission

    private func askCamera(_ allowed: @escaping (Bool) -> Void) {
        if Self.isSimulator { return allowed(true) }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: allowed(true)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async { allowed(granted) }
            }
        default: allowed(false)
        }
    }

    // MARK: - Photos (meal, label)

    private func snapPhoto() {
        guard let top = Self.topViewController() else { return answer(nil, nil, nil) }
        // The simulator says it has a camera but its capture fails, so it picks a library photo.
        if UIImagePickerController.isSourceTypeAvailable(.camera), !Self.isSimulator {
            // Our own camera screen: the photo goes straight on, with no "Use Photo" step.
            let camera = SnapCameraViewController { [weak self] image in self?.deliver(image) }
            top.present(camera, animated: true)
        } else {
            var config = PHPickerConfiguration()
            config.filter = .images
            config.selectionLimit = 1
            let picker = PHPickerViewController(configuration: config)
            picker.delegate = self
            picker.presentationController?.delegate = self
            top.present(picker, animated: true)
        }
    }

    fileprivate func deliver(_ image: UIImage?) {
        guard let image else { return answer(nil, nil, nil) }
        guard let jpeg = Self.shrunk(image).jpegData(compressionQuality: 0.8) else {
            return answer(nil, nil, "Couldn't read the photo. Please try again.")
        }
        answer(jpeg, nil, nil)
    }

    private static func shrunk(_ image: UIImage) -> UIImage {
        let side = max(image.size.width, image.size.height)
        guard side > longestSide else { return image }
        let scale = longestSide / side
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
    }

    // MARK: - Barcode

    @MainActor
    private func scanBarcode() {
        guard DataScannerViewController.isSupported, DataScannerViewController.isAvailable else {
            return answer(nil, nil, "Barcode scanning isn't available on this phone. Snap the nutrition label instead.")
        }
        guard let top = Self.topViewController() else { return answer(nil, nil, nil) }
        let scanner = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.ean13, .ean8, .upce, .code128])],
            qualityLevel: .balanced,
            recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: false,
            isHighlightingEnabled: true
        )
        scanner.delegate = self
        scanner.navigationItem.title = "Scan the barcode"
        scanner.navigationItem.leftBarButtonItem = UIBarButtonItem(
            systemItem: .cancel,
            primaryAction: UIAction { [weak self, weak scanner] _ in
                scanner?.stopScanning()
                scanner?.dismiss(animated: true)
                self?.answer(nil, nil, nil)
            }
        )
        let navigation = UINavigationController(rootViewController: scanner)
        navigation.modalPresentationStyle = .fullScreen
        scannedOnce = false
        top.present(navigation, animated: true) {
            try? scanner.startScanning()
        }
    }

    @MainActor
    fileprivate func scanned(_ code: String, from scanner: DataScannerViewController) {
        // The scanner reports the same code many times a second; only the first one counts.
        guard !scannedOnce else { return }
        scannedOnce = true
        scanner.stopScanning()
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        scanner.dismiss(animated: true) { [weak self] in self?.answer(nil, code, nil) }
    }

    // MARK: - Helpers

    private func answer(_ jpeg: Data?, _ barcode: String?, _ error: String?) {
        let done = self.done
        self.done = nil
        done?(jpeg, barcode, error)
    }

    private static var isSimulator: Bool {
        #if targetEnvironment(simulator)
            true
        #else
            false
        #endif
    }

    /// The app's own window, never a sheet's or dialog's window: Compose shows each sheet in a
    /// window of its own, and anything presented on it disappears when the sheet closes.
    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
        let window = scene?.windows.first { $0.windowLevel == .normal && !$0.isHidden } ?? scene?.windows.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController, !presented.isBeingDismissed { top = presented }
        return top
    }
}

extension CameraBridge: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        let image = info[.originalImage] as? UIImage
        picker.dismiss(animated: true) { [weak self] in self?.deliver(image) }
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true) { [weak self] in self?.deliver(nil) }
    }
}

extension CameraBridge: UIAdaptivePresentationControllerDelegate {
    // Swiped down or closed by the system without an answer: count it as cancelled.
    func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
        answer(nil, nil, nil)
    }
}

extension CameraBridge: PHPickerViewControllerDelegate {
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard let provider = results.first?.itemProvider, provider.canLoadObject(ofClass: UIImage.self) else {
            return deliver(nil)
        }
        provider.loadObject(ofClass: UIImage.self) { [weak self] object, _ in
            DispatchQueue.main.async { self?.deliver(object as? UIImage) }
        }
    }
}

extension CameraBridge: DataScannerViewControllerDelegate {
    func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
        for item in addedItems {
            if case let .barcode(barcode) = item, let code = barcode.payloadStringValue {
                MainActor.assumeIsolated { scanned(code, from: dataScanner) }
                return
            }
        }
    }

    func dataScanner(_ dataScanner: DataScannerViewController, didTapOn item: RecognizedItem) {
        if case let .barcode(barcode) = item, let code = barcode.payloadStringValue {
            MainActor.assumeIsolated { scanned(code, from: dataScanner) }
        }
    }
}
