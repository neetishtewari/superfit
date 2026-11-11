import AVFoundation
import Foundation
import Shared
import Speech

/// The iPhone's speech recognition for shared Kotlin code (VoiceLog, voice typing), like Android's
/// SpeechRecognizerHelper: listens through pauses, sends live words, and finishes by itself two
/// seconds after the last word or after a minute. Stopping waits up to 1.5 seconds for Apple's
/// corrected words. Nothing is recorded or kept: the sound goes to Apple's recogniser and only the
/// words come back. Every callback comes on the main thread.
final class SpeechBridge: NSObject, VoiceRecognizer {
    // The same values as VoiceRecognizer.MIC_OFF and VoiceRecognizer.NOTHING_HEARD in shared code.
    private static let micOff = "mic_off"
    private static let nothingHeard = "nothing_heard"

    private static let finishAfterSilence: TimeInterval = 2
    private static let longest: TimeInterval = 60
    private static let waitForFinal: TimeInterval = 1.5
    /// Versions passed on to the AI (SpokenInput.MAX_VERSIONS).
    private static let versions = 5

    private let audio = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var timers: [Timer] = []
    private var silence: Timer?
    private var session = 0
    private var active = false
    private var finishing = false
    private var guesses: [String] = []
    private var onLiveText: (String) -> Void = { _ in }
    private var onChecking: () -> Void = {}
    private var onHeard: ([[String]]) -> Void = { _ in }
    private var onError: (String) -> Void = { _ in }

    /// The phone's language and region, so an Indian English phone hears Indian English.
    private let recognizer = SFSpeechRecognizer(locale: Locale.current) ?? SFSpeechRecognizer()

    func isAvailable() -> Bool {
        recognizer != nil
    }

    func start(
        onLiveText: @escaping (String) -> Void,
        onChecking: @escaping () -> Void,
        onHeard: @escaping ([[String]]) -> Void,
        onError: @escaping (String) -> Void
    ) {
        cancel()
        session += 1
        let id = session
        self.onLiveText = onLiveText
        self.onChecking = onChecking
        self.onHeard = onHeard
        self.onError = onError
        active = true
        finishing = false
        guesses = []
        askPermission { [weak self] allowed in
            guard let self, id == self.session, self.active else { return }
            if allowed {
                self.listen(id)
            } else {
                self.end { $0.onError(Self.micOff) }
            }
        }
    }

    func stop() {
        guard active else { return }
        finish()
    }

    func cancel() {
        guard active else { return }
        session += 1
        active = false
        tearDown()
        task?.cancel()
        task = nil
    }

    // Speech recognition first, then the microphone; each asks only the first time.
    private func askPermission(_ done: @escaping (Bool) -> Void) {
        SFSpeechRecognizer.requestAuthorization { status in
            DispatchQueue.main.async {
                guard status == .authorized else { return done(false) }
                AVAudioApplication.requestRecordPermission { granted in
                    DispatchQueue.main.async { done(granted) }
                }
            }
        }
    }

    private func listen(_ id: Int) {
        guard let recognizer, recognizer.isAvailable else {
            end { $0.onError("Speech recognition isn't available right now. Check your internet and try again.") }
            return
        }
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        request.taskHint = .dictation
        request.addsPunctuation = false
        self.request = request
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.record, mode: .measurement, options: .duckOthers)
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
            let input = audio.inputNode
            input.installTap(onBus: 0, bufferSize: 1024, format: input.outputFormat(forBus: 0)) { buffer, _ in
                request.append(buffer)
            }
            audio.prepare()
            try audio.start()
        } catch {
            end { $0.onError(error.localizedDescription) }
            return
        }
        task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            DispatchQueue.main.async { self?.handle(id, result, error) }
        }
        timers.append(Timer.scheduledTimer(withTimeInterval: Self.longest, repeats: false) { [weak self] _ in
            guard let self, id == self.session else { return }
            self.finish()
        })
    }

    private func handle(_ id: Int, _ result: SFSpeechRecognitionResult?, _ error: Error?) {
        guard id == session, active else { return }
        if let result {
            let heard = result.transcriptions.prefix(Self.versions).map(\.formattedString)
                .map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
            if !heard.isEmpty { guesses = heard }
            if result.isFinal {
                deliver()
                return
            }
            if let best = guesses.first, !finishing {
                onLiveText(best)
                restartSilence(id)
            }
        }
        if let error {
            // What was heard before the error still counts; otherwise say what happened.
            if !guesses.isEmpty || finishing {
                deliver()
            } else if (error as NSError).code == 1110 {
                end { $0.onError(Self.nothingHeard) }
            } else {
                end { $0.onError(error.localizedDescription) }
            }
        }
    }

    private func restartSilence(_ id: Int) {
        silence?.invalidate()
        silence = Timer.scheduledTimer(withTimeInterval: Self.finishAfterSilence, repeats: false) { [weak self] _ in
            guard let self, id == self.session else { return }
            self.finish()
        }
    }

    // Stops the microphone and gives Apple a moment to send its final, corrected words.
    private func finish() {
        guard active, !finishing else { return }
        finishing = true
        onChecking()
        stopAudio()
        request?.endAudio()
        let id = session
        timers.append(Timer.scheduledTimer(withTimeInterval: Self.waitForFinal, repeats: false) { [weak self] _ in
            guard let self, id == self.session else { return }
            self.deliver()
        })
    }

    private func deliver() {
        let heard = guesses
        end { bridge in
            if heard.isEmpty { bridge.onError(Self.nothingHeard) } else { bridge.onHeard([heard]) }
        }
    }

    // Ends this session once, then gives shared code its one answer.
    private func end(_ answer: (SpeechBridge) -> Void) {
        guard active else { return }
        active = false
        tearDown()
        task?.finish()
        task = nil
        answer(self)
    }

    private func stopAudio() {
        if audio.isRunning { audio.stop() }
        audio.inputNode.removeTap(onBus: 0)
    }

    private func tearDown() {
        silence?.invalidate()
        silence = nil
        timers.forEach { $0.invalidate() }
        timers = []
        stopAudio()
        request?.endAudio()
        request = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
