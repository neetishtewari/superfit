import Shared
import UIKit

/// "Share my report" on iPhone: draws the shared report layout (CoachReportPdf) as an A4 PDF on the
/// phone, in Manrope with the app icon, and opens Apple's share sheet with it. The file is made in
/// the app's own cache, replaced each time, and only leaves the phone where the person sends it.
final class ReportBridge: NSObject, IosReport {
    func share(report: CoachExport) {
        let layout = CoachReportPdf.shared
        let page = CGRect(x: 0, y: 0, width: Int(layout.PAGE_W), height: Int(layout.PAGE_H))
        let format = UIGraphicsPDFRendererFormat()
        format.documentInfo = [kCGPDFContextTitle as String: "My Superfit report", kCGPDFContextCreator as String: "Superfit"]
        let data = UIGraphicsPDFRenderer(bounds: page, format: format).pdfData { context in
            layout.draw(report: report, surface: PdfDrawing(context: context), store: layout.store)
        }
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("shares", isDirectory: true)
        try? FileManager.default.removeItem(at: dir)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let file = dir.appendingPathComponent(layout.fileName(report: report))
        do {
            try data.write(to: file, options: .completeFileProtection)
        } catch {
            return
        }
        let sheet = UIActivityViewController(activityItems: [file], applicationActivities: nil)
        sheet.setValue("My Superfit report, \(layout.range(report: report))", forKey: "subject")
        guard let top = Self.topViewController() else { return }
        // iPad shows the sheet as a popover; it needs somewhere to point.
        sheet.popoverPresentationController?.sourceView = top.view
        sheet.popoverPresentationController?.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY, width: 0, height: 0)
        top.present(sheet, animated: true)
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

/// The shared layout's drawing calls on the iPhone's PDF renderer. Positions are points from the top
/// left, as in the layout; text is placed by its baseline, like Android's.
private final class PdfDrawing: NSObject, PdfSurface {
    private let context: UIGraphicsPDFRendererContext
    private let logo = UIImage(named: "ReportLogo")

    init(context: UIGraphicsPDFRendererContext) {
        self.context = context
    }

    func startPage(number _: Int32) {
        context.beginPage()
    }

    func finishPage() {}

    func fillRect(left: Float, top: Float, right: Float, bottom: Float, color: Int32, radius: Float) {
        let box = CGRect(x: CGFloat(left), y: CGFloat(top), width: CGFloat(right - left), height: CGFloat(bottom - top))
        Self.color(color).setFill()
        if radius > 0 {
            UIBezierPath(roundedRect: box, cornerRadius: CGFloat(radius)).fill()
        } else {
            UIBezierPath(rect: box).fill()
        }
    }

    func drawText(text: String, x: Float, baseline: Float, size: Float, font: PdfFont, color: Int32, letterSpacing: Float) {
        let face = Self.font(font, size: size)
        let words = NSAttributedString(string: text, attributes: attributes(face, color: color, size: size, spacing: letterSpacing))
        // UIKit draws from the top of the line; the layout gives the baseline.
        words.draw(at: CGPoint(x: CGFloat(x), y: CGFloat(baseline) - face.ascender))
    }

    func measureText(text: String, size: Float, font: PdfFont, letterSpacing: Float) -> Float {
        let face = Self.font(font, size: size)
        let words = NSAttributedString(string: text, attributes: attributes(face, color: 0, size: size, spacing: letterSpacing))
        return Float(words.size().width)
    }

    func drawLogo(left: Float, top: Float, size: Float) {
        let box = CGRect(x: CGFloat(left), y: CGFloat(top), width: CGFloat(size), height: CGFloat(size))
        guard let logo else {
            UIColor.white.setFill()
            UIBezierPath(roundedRect: box, cornerRadius: box.width * 0.28).fill()
            return
        }
        // The app icon with rounded corners, as it looks on a phone.
        let cg = context.cgContext
        cg.saveGState()
        UIBezierPath(roundedRect: box, cornerRadius: box.width * 0.18).addClip()
        logo.draw(in: box)
        cg.restoreGState()
    }

    func link(url: String, left: Float, top: Float, right: Float, bottom: Float) {
        guard let address = URL(string: url) else { return }
        let box = CGRect(x: CGFloat(left), y: CGFloat(top), width: CGFloat(right - left), height: CGFloat(bottom - top))
        context.setURL(address, for: box)
    }

    private func attributes(_ face: UIFont, color: Int32, size: Float, spacing: Float) -> [NSAttributedString.Key: Any] {
        [.font: face, .foregroundColor: Self.color(color), .kern: CGFloat(spacing * size)]
    }

    /// Manrope, bundled with the app; the system font if it can't be loaded.
    private static func font(_ font: PdfFont, size: Float) -> UIFont {
        let points = CGFloat(size)
        switch font {
        case .medium: return UIFont(name: "Manrope-Medium", size: points) ?? .systemFont(ofSize: points, weight: .medium)
        case .bold: return UIFont(name: "Manrope-Bold", size: points) ?? .systemFont(ofSize: points, weight: .bold)
        case .extraBold: return UIFont(name: "Manrope-ExtraBold", size: points) ?? .systemFont(ofSize: points, weight: .heavy)
        default: return UIFont(name: "Manrope-Regular", size: points) ?? .systemFont(ofSize: points)
        }
    }

    /// The layout's ARGB colour.
    private static func color(_ argb: Int32) -> UIColor {
        let v = UInt32(bitPattern: argb)
        return UIColor(
            red: CGFloat((v >> 16) & 0xFF) / 255,
            green: CGFloat((v >> 8) & 0xFF) / 255,
            blue: CGFloat(v & 0xFF) / 255,
            alpha: CGFloat((v >> 24) & 0xFF) / 255
        )
    }
}
