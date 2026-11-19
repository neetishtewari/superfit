import AVFoundation
import UIKit

/// Superfit's own camera screen for a meal or a label: a live view and one round button. The photo
/// goes straight on to be read, with no "Use Photo" step in between (Android has none either).
/// Nothing is saved to the phone's photos. [onPhoto] is called once with the picture, or with nil
/// when the person closes the screen; the screen has closed by then.
final class SnapCameraViewController: UIViewController, AVCapturePhotoCaptureDelegate {
    private let session = AVCaptureSession()
    private let output = AVCapturePhotoOutput()
    private let queue = DispatchQueue(label: "superfit.snapcamera")
    private let preview = AVCaptureVideoPreviewLayer()
    private var onPhoto: ((UIImage?) -> Void)?
    private var finished = false
    private let shutter = UIButton(type: .custom)

    init(onPhoto: @escaping (UIImage?) -> Void) {
        self.onPhoto = onPhoto
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .fullScreen
    }

    required init?(coder: NSCoder) { nil }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        preview.session = session
        preview.videoGravity = .resizeAspectFill
        view.layer.addSublayer(preview)

        shutter.backgroundColor = .white
        shutter.layer.cornerRadius = 36
        shutter.layer.borderWidth = 4
        shutter.layer.borderColor = UIColor.black.withAlphaComponent(0.35).cgColor
        shutter.accessibilityLabel = "Take photo"
        shutter.addTarget(self, action: #selector(snap), for: .touchUpInside)
        view.addSubview(shutter)

        let close = UIButton(type: .system)
        close.setTitle("Cancel", for: .normal)
        close.setTitleColor(.white, for: .normal)
        close.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        close.addTarget(self, action: #selector(cancel), for: .touchUpInside)
        close.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(close)
        NSLayoutConstraint.activate([
            close.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 20),
            close.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -34),
            close.heightAnchor.constraint(equalToConstant: 44)
        ])

        queue.async { [weak self] in self?.setUpSession() }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview.frame = view.bounds
        shutter.frame = CGRect(x: view.bounds.midX - 36, y: view.bounds.maxY - view.safeAreaInsets.bottom - 110, width: 72, height: 72)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        queue.async { [weak self] in
            guard let self, !self.session.isRunning else { return }
            self.session.startRunning()
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        queue.async { [weak self] in
            guard let self, self.session.isRunning else { return }
            self.session.stopRunning()
        }
    }

    override var prefersStatusBarHidden: Bool { true }

    private func setUpSession() {
        session.beginConfiguration()
        session.sessionPreset = .photo
        if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
           let input = try? AVCaptureDeviceInput(device: device), session.canAddInput(input) {
            session.addInput(input)
        }
        if session.canAddOutput(output) { session.addOutput(output) }
        session.commitConfiguration()
    }

    @objc private func snap() {
        guard !finished, session.isRunning else { return }
        shutter.isEnabled = false
        let settings = AVCapturePhotoSettings()
        output.capturePhoto(with: settings, delegate: self)
    }

    @objc private func cancel() { finish(nil) }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        let image = photo.fileDataRepresentation().flatMap { UIImage(data: $0) }
        DispatchQueue.main.async { [weak self] in self?.finish(image) }
    }

    private func finish(_ image: UIImage?) {
        guard !finished else { return }
        finished = true
        let callback = onPhoto
        onPhoto = nil
        dismiss(animated: true) { callback?(image) }
    }
}
