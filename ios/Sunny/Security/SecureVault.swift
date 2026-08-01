import CryptoKit
import Foundation
import Security

actor SecureVault {
    private let keychain = KeychainStore(service: "com.sunny.skin.vault")
    private let keyAccount = "vault-key-v1"
    private let root: URL
    private let imageDirectory: URL
    private let metadataURL: URL

    init(fileManager: FileManager = .default) throws {
        let applicationSupport = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        root = applicationSupport.appending(path: "SunnyVault", directoryHint: .isDirectory)
        imageDirectory = root.appending(path: "Images", directoryHint: .isDirectory)
        metadataURL = root.appending(path: "vault.bin")
        try fileManager.createDirectory(at: imageDirectory, withIntermediateDirectories: true)
        try? fileManager.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: root.path)
    }

    func load() throws -> VaultPayload {
        guard FileManager.default.fileExists(atPath: metadataURL.path) else { return VaultPayload() }
        let encrypted = try Data(contentsOf: metadataURL)
        let clear = try open(encrypted)
        return try JSONDecoder.sunny.decode(VaultPayload.self, from: clear)
    }

    func save(_ payload: VaultPayload) throws {
        let clear = try JSONEncoder.sunny.encode(payload)
        let encrypted = try seal(clear)
        try encrypted.write(to: metadataURL, options: [.atomic, .completeFileProtection])
    }

    func storeImage(_ jpeg: Data) throws -> String {
        let name = "\(UUID().uuidString.lowercased()).sunny"
        let url = imageDirectory.appending(path: name)
        try seal(jpeg).write(to: url, options: [.atomic, .completeFileProtection])
        return name
    }

    func image(named name: String) throws -> Data {
        let safeName = URL(filePath: name).lastPathComponent
        guard safeName == name else { throw CocoaError(.fileReadInvalidFileName) }
        return try open(Data(contentsOf: imageDirectory.appending(path: safeName)))
    }

    func deleteImage(named name: String) {
        let safeName = URL(filePath: name).lastPathComponent
        guard safeName == name else { return }
        try? FileManager.default.removeItem(at: imageDirectory.appending(path: safeName))
    }

    private func symmetricKey() throws -> SymmetricKey {
        if let existing = try keychain.data(for: keyAccount), existing.count == 32 {
            return SymmetricKey(data: existing)
        }
        var bytes = Data(count: 32)
        let status = bytes.withUnsafeMutableBytes { buffer in
            SecRandomCopyBytes(kSecRandomDefault, buffer.count, buffer.baseAddress!)
        }
        guard status == errSecSuccess else { throw KeychainError.unexpectedStatus(status) }
        try keychain.set(bytes, for: keyAccount)
        return SymmetricKey(data: bytes)
    }

    private func seal(_ data: Data) throws -> Data {
        guard let combined = try AES.GCM.seal(data, using: symmetricKey()).combined else {
            throw CocoaError(.fileWriteUnknown)
        }
        return combined
    }

    private func open(_ data: Data) throws -> Data {
        try AES.GCM.open(AES.GCM.SealedBox(combined: data), using: symmetricKey())
    }
}

private extension JSONEncoder {
    static var sunny: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .millisecondsSince1970
        encoder.outputFormatting = [.sortedKeys]
        return encoder
    }
}

private extension JSONDecoder {
    static var sunny: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .millisecondsSince1970
        return decoder
    }
}
