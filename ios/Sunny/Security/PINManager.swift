import Foundation
import Combine
import Security

@MainActor
final class PINManager: ObservableObject {
    @Published private(set) var isEnabled = false
    @Published private(set) var isUnlocked = true
    @Published private(set) var lockoutRemaining: TimeInterval = 0

    private let keychain = KeychainStore(service: "com.sunny.skin.pin")
    private let defaults = UserDefaults.standard
    private var timer: Timer?

    init() {
        isEnabled = (try? keychain.data(for: "verifier")) != nil
        isUnlocked = !isEnabled
        refreshLockout()
    }

    func setPIN(_ pin: String) throws {
        guard pin.count == 4, pin.allSatisfy(\.isNumber) else { throw PINError.invalidFormat }
        let salt = randomData(count: 16)
        let verifier = PBKDF2.derive(password: Data(pin.utf8), salt: salt, iterations: 210_000, count: 32)
        try keychain.set(salt, for: "salt")
        try keychain.set(verifier, for: "verifier")
        defaults.set(0, forKey: "sunny.pin.failures")
        isEnabled = true
        isUnlocked = true
    }

    func clear() {
        try? keychain.remove("salt")
        try? keychain.remove("verifier")
        defaults.removeObject(forKey: "sunny.pin.failures")
        defaults.removeObject(forKey: "sunny.pin.lockoutUntil")
        isEnabled = false
        isUnlocked = true
    }

    func lock() {
        if isEnabled { isUnlocked = false }
    }

    func verify(_ pin: String) async -> Bool {
        refreshLockout()
        guard lockoutRemaining <= 0,
              let salt = try? keychain.data(for: "salt"),
              let expected = try? keychain.data(for: "verifier") else { return false }
        let password = Data(pin.utf8)
        let actual = await Task.detached(priority: .userInitiated) {
            PBKDF2.derive(password: password, salt: salt, iterations: 210_000, count: 32)
        }.value
        if constantTimeEqual(actual, expected) {
            defaults.set(0, forKey: "sunny.pin.failures")
            defaults.removeObject(forKey: "sunny.pin.lockoutUntil")
            isUnlocked = true
            return true
        }
        noteFailure()
        return false
    }

    private func noteFailure() {
        let failures = defaults.integer(forKey: "sunny.pin.failures") + 1
        defaults.set(failures, forKey: "sunny.pin.failures")
        guard failures >= 5 else { return }
        let seconds = min(30 * pow(2, Double(min(failures - 5, 5))), 15 * 60)
        defaults.set(Date().addingTimeInterval(seconds).timeIntervalSince1970, forKey: "sunny.pin.lockoutUntil")
        refreshLockout()
    }

    private func refreshLockout() {
        let until = Date(timeIntervalSince1970: defaults.double(forKey: "sunny.pin.lockoutUntil"))
        lockoutRemaining = max(0, until.timeIntervalSinceNow)
        timer?.invalidate()
        if lockoutRemaining > 0 {
            timer = .scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
                Task { @MainActor in self?.refreshLockout() }
            }
        }
    }

    private func randomData(count: Int) -> Data {
        var data = Data(count: count)
        _ = data.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, count, $0.baseAddress!) }
        return data
    }

    private func constantTimeEqual(_ lhs: Data, _ rhs: Data) -> Bool {
        guard lhs.count == rhs.count else { return false }
        return zip(lhs, rhs).reduce(UInt8(0)) { $0 | ($1.0 ^ $1.1) } == 0
    }
}

enum PINError: LocalizedError {
    case invalidFormat
    var errorDescription: String? { "Enter exactly four digits." }
}

private enum PBKDF2 {
    static func derive(password: Data, salt: Data, iterations: Int, count: Int) -> Data {
        var output = Data(count: count)
        let status = password.withUnsafeBytes { passwordBytes in
            salt.withUnsafeBytes { saltBytes in
                output.withUnsafeMutableBytes { outputBytes in
                    sunny_pbkdf2_sha256(
                        passwordBytes.bindMemory(to: UInt8.self).baseAddress,
                        password.count,
                        saltBytes.bindMemory(to: UInt8.self).baseAddress,
                        salt.count,
                        UInt32(iterations),
                        outputBytes.bindMemory(to: UInt8.self).baseAddress,
                        count
                    )
                }
            }
        }
        precondition(status == 0, "PBKDF2 derivation failed")
        return output
    }
}
