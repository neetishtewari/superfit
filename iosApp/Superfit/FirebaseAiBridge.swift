import FirebaseAI
import Foundation
import Shared

/// One Gemini call through Firebase AI Logic on the iPhone, billed to Superfit's Firebase project
/// and protected by App Check, like Android. Shared Kotlin code picks the model and retries.
final class FirebaseAiBridge: NSObject, IosAi {
    private let ai = FirebaseAI.firebaseAI(backend: .googleAI())

    func generate(model: String, prompt: String, systemInstruction: String?, isJson: Bool, jpeg: Data?,
                  done: @escaping (String?, String?) -> Void) {
        let generative = makeModel(model, systemInstruction: systemInstruction, isJson: isJson)
        Task {
            do {
                let response: GenerateContentResponse
                if let jpeg {
                    response = try await generative.generateContent(InlineDataPart(data: jpeg, mimeType: "image/jpeg"), prompt)
                } else {
                    response = try await generative.generateContent(prompt)
                }
                await MainActor.run { done(response.text, nil) }
            } catch {
                await MainActor.run { done(nil, String(describing: error)) }
            }
        }
    }

    func chat(model: String, systemInstruction: String, historyRoles: [String], historyTexts: [String], message: String,
              done: @escaping (String?, String?) -> Void) {
        let generative = makeModel(model, systemInstruction: systemInstruction, isJson: false)
        let history = zip(historyRoles, historyTexts).map { role, text in ModelContent(role: role, parts: text) }
        Task {
            do {
                let response = try await generative.startChat(history: history).sendMessage(message)
                await MainActor.run { done(response.text, nil) }
            } catch {
                await MainActor.run { done(nil, String(describing: error)) }
            }
        }
    }

    private func makeModel(_ name: String, systemInstruction: String?, isJson: Bool) -> GenerativeModel {
        ai.generativeModel(
            modelName: name,
            generationConfig: isJson ? GenerationConfig(responseMIMEType: "application/json") : nil,
            systemInstruction: systemInstruction.map { ModelContent(role: "system", parts: $0) }
        )
    }
}
