import AuthenticationServices
import CryptoKit
import FirebaseAuth
import FirebaseCore
import GoogleSignIn
import Shared
import UIKit

/// Superfit's sign-in on Firebase's iPhone SDK: email, Google (Google's own sheet) and Sign in
/// with Apple. Same Firebase accounts as the Android app. Errors go to shared code under the same
/// names Android uses, so both apps show the same plain messages.
final class FirebaseAuthBridge: NSObject, IosAuth {
    private var appleSignIn: AppleSignIn?

    func currentUid() -> String? { Auth.auth().currentUser?.uid }

    func currentEmail() -> String? { Auth.auth().currentUser?.email }

    func currentName() -> String? { Auth.auth().currentUser?.displayName }

    func signInMethod() -> String {
        let providers = Auth.auth().currentUser?.providerData.map(\.providerID) ?? []
        if providers.contains("apple.com") { return "Sign in with Apple" }
        if providers.contains("google.com") { return "Google sign-in" }
        return "Email and password"
    }

    /// Deletes the Firebase account (its data is deleted first by shared code). A Sign in with
    /// Apple account confirms with Apple once more, so Apple's link to Superfit is revoked too, as
    /// Apple asks of apps that offer account deletion.
    func deleteAccount(done: @escaping (String) -> Void) {
        guard let user = Auth.auth().currentUser else { return done("DELETED") }
        let finish: (Error?) -> Void = { error in
            guard let error = error as NSError? else { return done("DELETED") }
            done(AuthErrorCode(rawValue: error.code) == .requiresRecentLogin ? "NEEDS_RECENT_SIGN_IN" : "FAILED")
        }
        guard user.providerData.contains(where: { $0.providerID == "apple.com" }) else {
            return user.delete(completion: finish)
        }
        let flow = AppleSignIn(finish: { [weak self] error in
            self?.appleSignIn = nil
            if let error { done(error.code == "APPLE_CANCELLED" ? "CANCELLED" : "FAILED") }
        })
        flow.confirmed = { [weak self] credential, code in
            self?.appleSignIn = nil
            user.reauthenticate(with: credential) { _, error in
                if let error { return finish(error) }
                guard let code else { return user.delete(completion: finish) }
                Auth.auth().revokeToken(withAuthorizationCode: code) { _ in
                    // The account goes even if Apple's link couldn't be revoked; the person can
                    // still remove it in the iPhone's Settings › Apple ID › Sign in with Apple.
                    user.delete(completion: finish)
                }
            }
        }
        appleSignIn = flow
        flow.start()
    }

    func signIn(email: String, password: String, done: @escaping (IosAuthError?) -> Void) {
        Auth.auth().signIn(withEmail: email, password: password) { _, error in done(Self.authError(error)) }
    }

    func createAccount(email: String, password: String, done: @escaping (IosAuthError?) -> Void) {
        Auth.auth().createUser(withEmail: email, password: password) { _, error in done(Self.authError(error)) }
    }

    func sendPasswordReset(email: String, done: @escaping (IosAuthError?) -> Void) {
        Auth.auth().sendPasswordReset(withEmail: email) { error in done(Self.authError(error)) }
    }

    func signInWithGoogle(done: @escaping (IosAuthError?) -> Void) {
        guard let clientID = FirebaseApp.app()?.options.clientID, let presenter = Self.topViewController() else {
            return done(IosAuthError(code: "NOT_SET_UP", message: nil, isNetwork: false))
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let error {
                let cancelled = (error as? GIDSignInError)?.code == .canceled
                let network = (error as NSError).domain == NSURLErrorDomain
                return done(IosAuthError(code: cancelled ? "GOOGLE_CANCELLED" : "GOOGLE_FAILED", message: error.localizedDescription, isNetwork: network))
            }
            guard let user = result?.user, let idToken = user.idToken?.tokenString else {
                return done(IosAuthError(code: "GOOGLE_FAILED", message: nil, isNetwork: false))
            }
            let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: user.accessToken.tokenString)
            Auth.auth().signIn(with: credential) { _, error in done(Self.authError(error)) }
        }
    }

    func signInWithApple(done: @escaping (IosAuthError?) -> Void) {
        let flow = AppleSignIn { [weak self] error in
            self?.appleSignIn = nil
            done(error)
        }
        appleSignIn = flow
        flow.start()
    }

    func signOut() {
        GIDSignIn.sharedInstance.signOut()
        try? Auth.auth().signOut()
    }

    /// Firebase's iPhone error codes, renamed to the names Android's Firebase uses.
    static func authError(_ error: Error?) -> IosAuthError? {
        guard let error = error as NSError? else { return nil }
        let code = AuthErrorCode(rawValue: error.code)
        let name: String?
        switch code {
        case .invalidEmail: name = "ERROR_INVALID_EMAIL"
        case .wrongPassword: name = "ERROR_WRONG_PASSWORD"
        case .invalidCredential: name = "ERROR_INVALID_CREDENTIAL"
        case .userNotFound: name = "ERROR_USER_NOT_FOUND"
        case .emailAlreadyInUse: name = "ERROR_EMAIL_ALREADY_IN_USE"
        case .weakPassword: name = "ERROR_WEAK_PASSWORD"
        case .tooManyRequests: name = "ERROR_TOO_MANY_REQUESTS"
        case .userDisabled: name = "ERROR_USER_DISABLED"
        case .accountExistsWithDifferentCredential: name = "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL"
        default: name = nil
        }
        let network = code == .networkError || error.domain == NSURLErrorDomain
        return IosAuthError(code: name, message: error.localizedDescription, isNetwork: network)
    }

    static func topViewController() -> UIViewController? {
        var top = keyWindow()?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }

    static func keyWindow() -> UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
    }
}

/// One Sign in with Apple attempt. A fresh random nonce goes to Apple hashed and to Firebase in
/// full, so a stolen Apple token can't be replayed to sign in.
private final class AppleSignIn: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private let rawNonce = AppleSignIn.randomNonce()
    private let finish: (IosAuthError?) -> Void

    /// Set when the person is confirming who they are (deleting the account) rather than signing
    /// in: gets Apple's credential and its one-time authorization code instead.
    var confirmed: ((OAuthCredential, String?) -> Void)?

    init(finish: @escaping (IosAuthError?) -> Void) {
        self.finish = finish
    }

    func start() {
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = AppleSignIn.sha256(rawNonce)
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let apple = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = apple.identityToken,
              let token = String(data: tokenData, encoding: .utf8)
        else {
            return finish(IosAuthError(code: "APPLE_FAILED", message: nil, isNetwork: false))
        }
        let credential = OAuthProvider.appleCredential(withIDToken: token, rawNonce: rawNonce, fullName: apple.fullName)
        if let confirmed {
            return confirmed(credential, apple.authorizationCode.flatMap { String(data: $0, encoding: .utf8) })
        }
        Auth.auth().signIn(with: credential) { [finish] _, error in finish(FirebaseAuthBridge.authError(error)) }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        let cancelled = (error as? ASAuthorizationError)?.code == .canceled
        finish(IosAuthError(code: cancelled ? "APPLE_CANCELLED" : "APPLE_FAILED", message: error.localizedDescription, isNetwork: false))
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        FirebaseAuthBridge.keyWindow() ?? ASPresentationAnchor()
    }

    private static func randomNonce(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var bytes = [UInt8](repeating: 0, count: length)
        let status = SecRandomCopyBytes(kSecRandomDefault, length, &bytes)
        precondition(status == errSecSuccess, "No secure random numbers for the sign-in nonce")
        return String(bytes.map { charset[Int($0) % charset.count] })
    }

    private static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}
