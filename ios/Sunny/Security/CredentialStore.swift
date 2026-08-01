import Foundation

struct CredentialStore: Sendable {
    private let keychain = KeychainStore(service: "com.sunny.skin.credentials")

    var inferenceToken: String {
        get { (try? keychain.data(for: "inference-token")).flatMap { String(data: $0, encoding: .utf8) } ?? "" }
        nonmutating set {
            if newValue.isEmpty { try? keychain.remove("inference-token") }
            else { try? keychain.set(Data(newValue.utf8), for: "inference-token") }
        }
    }
}

