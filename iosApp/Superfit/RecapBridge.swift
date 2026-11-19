import AVFoundation
import Shared
import Speech
import UIKit
import WidgetKit

/// The monthly recap's music and video on iPhone. The music is Superfit's own original track for the
/// month (Music/recap_music_MM.m4a), played quietly with the "ambient" audio setting so the silent
/// switch keeps it quiet and it never stops someone's own music. The video is made on the phone with
/// Apple's encoder, written to the app's own temporary folder (replaced each time, protected while
/// the phone is locked) and only leaves where the person shares it.
final class RecapBridge: NSObject, IosRecapMedia {
    private var player: AVAudioPlayer?
    private var playerMonth: Int32 = 0

    // MARK: Music

    func playMusic(month: Int32) {
        if player == nil || playerMonth != month {
            player?.stop()
            player = nil
            guard let url = Self.track(month) else { return }
            try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default)
            try? AVAudioSession.sharedInstance().setActive(true)
            player = try? AVAudioPlayer(contentsOf: url)
            player?.numberOfLoops = -1
            player?.volume = 0
            playerMonth = month
        }
        guard let player, !player.isPlaying else { return }
        player.play()
        player.setVolume(0.45, fadeDuration: 1.2)
    }

    func pauseMusic() {
        player?.pause()
        player?.volume = 0
    }

    func stopMusic() {
        player?.stop()
        player = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private static func track(_ month: Int32) -> URL? {
        Bundle.main.url(forResource: String(format: "recap_music_%02d", month), withExtension: "m4a")
    }

    // MARK: Video

    private var writer: AVAssetWriter?
    private var input: AVAssetWriterInput?
    private var adaptor: AVAssetWriterInputPixelBufferAdaptor?
    private var videoURL: URL?
    private var fps: Int32 = 24
    private var width = 0
    private var height = 0

    private static var folder: URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("recap-video", isDirectory: true)
    }

    func videoStart(width: Int32, height: Int32, fps: Int32) -> Bool {
        videoCancel()
        try? FileManager.default.removeItem(at: Self.folder)
        try? FileManager.default.createDirectory(at: Self.folder, withIntermediateDirectories: true)
        let url = Self.folder.appendingPathComponent("frames.mp4")
        guard let writer = try? AVAssetWriter(outputURL: url, fileType: .mp4) else { return false }
        let settings: [String: Any] = [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: Int(width),
            AVVideoHeightKey: Int(height),
            AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: 4_000_000, AVVideoMaxKeyFrameIntervalKey: Int(fps)]
        ]
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: settings)
        input.expectsMediaDataInRealTime = false
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(
            assetWriterInput: input,
            sourcePixelBufferAttributes: [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
                kCVPixelBufferWidthKey as String: Int(width),
                kCVPixelBufferHeightKey as String: Int(height)
            ]
        )
        guard writer.canAdd(input) else { return false }
        writer.add(input)
        guard writer.startWriting() else { return false }
        writer.startSession(atSourceTime: .zero)
        self.writer = writer
        self.input = input
        self.adaptor = adaptor
        self.videoURL = url
        self.fps = fps
        self.width = Int(width)
        self.height = Int(height)
        return true
    }

    func videoAppend(bgra: Data, frame: Int32) -> Bool {
        guard let writer, let input, let adaptor, writer.status == .writing else { return false }
        while !input.isReadyForMoreMediaData {
            if writer.status != .writing { return false }
            usleep(2_000)
        }
        var buffer: CVPixelBuffer?
        if let pool = adaptor.pixelBufferPool {
            CVPixelBufferPoolCreatePixelBuffer(nil, pool, &buffer)
        } else {
            CVPixelBufferCreate(nil, width, height, kCVPixelFormatType_32BGRA, nil, &buffer)
        }
        guard let buffer else { return false }
        CVPixelBufferLockBaseAddress(buffer, [])
        defer { CVPixelBufferUnlockBaseAddress(buffer, []) }
        guard let base = CVPixelBufferGetBaseAddress(buffer) else { return false }
        let destRow = CVPixelBufferGetBytesPerRow(buffer)
        let srcRow = width * 4
        guard bgra.count >= srcRow * height else { return false }
        bgra.withUnsafeBytes { raw in
            guard let src = raw.baseAddress else { return }
            for row in 0..<height {
                memcpy(base + row * destRow, src + row * srcRow, srcRow)
            }
        }
        return adaptor.append(buffer, withPresentationTime: CMTime(value: CMTimeValue(frame), timescale: fps))
    }

    func videoFinish(month: Int32, durationMs: Int64, fileName: String, done: @escaping (KotlinBoolean) -> Void) {
        guard let writer, let input, let videoURL else {
            done(KotlinBoolean(value: false))
            return
        }
        input.markAsFinished()
        writer.finishWriting { [weak self] in
            guard writer.status == .completed else {
                DispatchQueue.main.async { done(KotlinBoolean(value: false)) }
                return
            }
            self?.writer = nil
            self?.input = nil
            self?.adaptor = nil
            Task {
                let out = Self.folder.appendingPathComponent(fileName)
                let ok = await Self.addMusic(video: videoURL, month: month, durationMs: durationMs, to: out)
                await MainActor.run {
                    guard ok else { return done(KotlinBoolean(value: false)) }
                    let sheet = UIActivityViewController(activityItems: [out], applicationActivities: nil)
                    let close = OverlayPresenter.present(sheet)
                    sheet.completionWithItemsHandler = { _, _, _, _ in close() }
                    done(KotlinBoolean(value: true))
                }
            }
        }
    }

    func videoCancel() {
        input?.markAsFinished()
        writer?.cancelWriting()
        writer = nil
        input = nil
        adaptor = nil
    }

    /// Lays the month's track under the video: looped to fit, faded in and out. Without the track
    /// the video still goes out, silent.
    private static func addMusic(video: URL, month: Int32, durationMs: Int64, to out: URL) async -> Bool {
        let composition = AVMutableComposition()
        let total = CMTime(value: durationMs, timescale: 1000)
        do {
            let videoAsset = AVURLAsset(url: video)
            guard let source = try await videoAsset.loadTracks(withMediaType: .video).first,
                  let videoTrack = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid) else { return false }
            let length = try await videoAsset.load(.duration)
            try videoTrack.insertTimeRange(CMTimeRange(start: .zero, duration: CMTimeMinimum(length, total)), of: source, at: .zero)

            var mix: AVAudioMix?
            if let url = track(month) {
                let musicAsset = AVURLAsset(url: url)
                if let music = try await musicAsset.loadTracks(withMediaType: .audio).first,
                   let audio = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) {
                    let loop = try await musicAsset.load(.duration)
                    var at = CMTime.zero
                    while at < total, loop.seconds > 0.5 {
                        let piece = CMTimeMinimum(loop, total - at)
                        try audio.insertTimeRange(CMTimeRange(start: .zero, duration: piece), of: music, at: at)
                        at = at + piece
                    }
                    let params = AVMutableAudioMixInputParameters(track: audio)
                    let fadeIn = CMTime(seconds: 1, preferredTimescale: 600)
                    let fadeOut = CMTime(seconds: 2, preferredTimescale: 600)
                    params.setVolumeRamp(fromStartVolume: 0, toEndVolume: 0.8, timeRange: CMTimeRange(start: .zero, duration: fadeIn))
                    params.setVolumeRamp(fromStartVolume: 0.8, toEndVolume: 0, timeRange: CMTimeRange(start: total - fadeOut, duration: fadeOut))
                    let audioMix = AVMutableAudioMix()
                    audioMix.inputParameters = [params]
                    mix = audioMix
                }
            }

            try? FileManager.default.removeItem(at: out)
            guard let export = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetHighestQuality) else { return false }
            export.outputURL = out
            export.outputFileType = .mp4
            export.audioMix = mix
            await withCheckedContinuation { (finished: CheckedContinuation<Void, Never>) in
                export.exportAsynchronously { finished.resume() }
            }
            guard export.status == .completed else { return false }
            try? FileManager.default.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: out.path)
            try? FileManager.default.removeItem(at: video)
            return true
        } catch {
            return false
        }
    }

    // MARK: Setup checks

    func micAllowed() -> Bool {
        AVAudioApplication.shared.recordPermission == .granted && SFSpeechRecognizer.authorizationStatus() == .authorized
    }

    func requestMic(done: @escaping (KotlinBoolean) -> Void) {
        let refused = AVAudioApplication.shared.recordPermission == .denied || SFSpeechRecognizer.authorizationStatus() == .denied
        if refused {
            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
            done(KotlinBoolean(value: false))
            return
        }
        AVAudioApplication.requestRecordPermission { mic in
            SFSpeechRecognizer.requestAuthorization { speech in
                DispatchQueue.main.async { done(KotlinBoolean(value: mic && speech == .authorized)) }
            }
        }
    }

    func widgetPlaced(done: @escaping (KotlinBoolean) -> Void) {
        WidgetCenter.shared.getCurrentConfigurations { result in
            let placed = (try? result.get())?.isEmpty == false
            DispatchQueue.main.async { done(KotlinBoolean(value: placed)) }
        }
    }
}
