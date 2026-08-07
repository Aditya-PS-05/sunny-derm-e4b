import CryptoKit
import Foundation
import Combine

struct ModelAsset: Identifiable, Sendable {
    let fileName: String
    let byteCount: Int64
    let sha256: String
    var id: String { fileName }
}

enum SunnyModelCatalog {
    static let version = "sunny-pad-smolvlm-500m-v1-gguf"
    static let assets = [
        ModelAsset(
            fileName: "sunny-pad-smolvlm-500m-Q4_K_M.gguf",
            byteCount: 303_250_432,
            sha256: "fb64c371d4044c7556966bf5dbf12d8aa5fb3b628a8be9ba0841189dbffe4d64"
        ),
        ModelAsset(
            fileName: "sunny-pad-smolvlm-500m-mmproj-Q8_0.gguf",
            byteCount: 108_782_144,
            sha256: "ac585ec2ee776eab23c4502f1a71d9d90a4057752057c05ed2487d47dc31798f"
        ),
        ModelAsset(
            fileName: "derm.gbnf",
            byteCount: 304,
            sha256: "bb7668aafa0c3b87cb5b10ecf9bd01037c6ad3ed8fdf01bc53e7b267538c2f09"
        ),
        ModelAsset(
            fileName: "THIRD_PARTY_NOTICES.txt",
            byteCount: 2_088,
            sha256: "ded7a876f2e6501c7a263e3fafaef98684165ae325eac5c81fcb8b5aeb352866"
        ),
        ModelAsset(
            fileName: "Apache-2.0.txt",
            byteCount: 11_357,
            sha256: "84829002701217076a39a84808ec52e45088ddbf9f6623896e5550becd8e09be"
        ),
        ModelAsset(
            fileName: "manifest.json",
            byteCount: 3_230,
            sha256: "972c9d0544bddebed21506616f4bbce56ef18c78e4b7681fa870e297f5111712"
        ),
    ]
    static let totalBytes = assets.reduce(Int64(0)) { $0 + $1.byteCount }
}

enum ModelInstallState: Equatable {
    case unavailable
    case idle
    case downloading(bytes: Int64, total: Int64, bytesPerSecond: Int64, eta: TimeInterval?)
    case verifying
    case ready
    case failed(String)
}

final class ModelDownloadManager: NSObject, ObservableObject, URLSessionDownloadDelegate, @unchecked Sendable {
    @Published private(set) var state: ModelInstallState = .idle
    @Published var allowsCellular = true

    private let configuration: AppConfiguration
    private let credentialStore = CredentialStore()
    let installDirectory: URL
    private var session: URLSession!
    private var downloadedBeforeCurrent: Int64 = 0
    private var sampleBytes: Int64 = 0
    private var sampleDate = Date()
    private let lock = NSLock()

    static let shared = ModelDownloadManager(configuration: .current)

    init(configuration: AppConfiguration) {
        self.configuration = configuration
        let base = (try? FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )) ?? FileManager.default.temporaryDirectory
        installDirectory = base.appending(path: "SunnyModels/\(SunnyModelCatalog.version)", directoryHint: .isDirectory)
        super.init()
        let legacy = base.appending(path: "SunnyModels/sunny-moe-2.2b-v4-gguf", directoryHint: .isDirectory)
        try? FileManager.default.removeItem(at: legacy)
        reconnectBackgroundSession()
        refresh()
    }

    var textModelURL: URL { installDirectory.appending(path: SunnyModelCatalog.assets[0].fileName) }
    var projectorURL: URL { installDirectory.appending(path: SunnyModelCatalog.assets[1].fileName) }
    var grammarURL: URL { installDirectory.appending(path: SunnyModelCatalog.assets[2].fileName) }

    func refresh() {
        DispatchQueue.global(qos: .utility).async { [weak self] in
            guard let self else { return }
            let ready = SunnyModelCatalog.assets.allSatisfy { asset in
                let url = self.installDirectory.appending(path: asset.fileName)
                return FileManager.default.fileExists(atPath: url.path) &&
                    (try? Self.sha256(url: url)) == asset.sha256
            }
            DispatchQueue.main.async {
                if case .downloading = self.state { return }
                self.state = ready ? .ready : (self.configuration.modelBaseURL == nil ? .unavailable : .idle)
            }
        }
    }

    func start(hasProAccess: Bool) {
        precondition(Thread.isMainThread)
        guard hasProAccess else {
            state = .failed("Sunny Offline requires an active Pro subscription.")
            return
        }
        guard configuration.modelBaseURL != nil else {
            state = .unavailable
            return
        }
        guard Self.availableCapacity() >= SunnyModelCatalog.totalBytes + 750_000_000 else {
            state = .failed("Free at least 1.2 GB of storage before downloading Sunny Offline.")
            return
        }
        try? FileManager.default.createDirectory(at: installDirectory, withIntermediateDirectories: true)
        downloadedBeforeCurrent = installedByteCount()
        sampleBytes = downloadedBeforeCurrent
        sampleDate = Date()
        state = .downloading(
            bytes: downloadedBeforeCurrent,
            total: SunnyModelCatalog.totalBytes,
            bytesPerSecond: 0,
            eta: nil
        )
        startNextMissingAsset()
    }

    func cancel() {
        session.getAllTasks { tasks in tasks.forEach { $0.cancel() } }
        DispatchQueue.main.async { self.state = .idle }
    }

    func removeInstalledModel() {
        cancel()
        try? FileManager.default.removeItem(at: installDirectory)
        state = configuration.modelBaseURL == nil ? .unavailable : .idle
    }

    private func reconnectBackgroundSession() {
        let config = URLSessionConfiguration.background(withIdentifier: "com.sunny.skin.model-download")
        config.sessionSendsLaunchEvents = true
        config.isDiscretionary = false
        session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
        session.getAllTasks { [weak self] tasks in
            guard let self, !tasks.isEmpty else { return }
            DispatchQueue.main.async {
                self.state = .downloading(
                    bytes: self.installedByteCount(),
                    total: SunnyModelCatalog.totalBytes,
                    bytesPerSecond: 0,
                    eta: nil
                )
            }
        }
    }

    private func startNextMissingAsset() {
        guard let baseURL = configuration.modelBaseURL else {
            state = .unavailable
            return
        }
        let next = SunnyModelCatalog.assets.first { asset in
            let url = installDirectory.appending(path: asset.fileName)
            return !FileManager.default.fileExists(atPath: url.path) ||
                (try? Self.sha256(url: url)) != asset.sha256
        }
        guard let next else {
            state = .ready
            return
        }
        var request = URLRequest(url: baseURL.appending(path: SunnyModelCatalog.version).appending(path: next.fileName))
        request.allowsCellularAccess = allowsCellular
        request.allowsExpensiveNetworkAccess = allowsCellular
        request.allowsConstrainedNetworkAccess = allowsCellular
        let token = credentialStore.inferenceToken
        if !token.isEmpty { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        let task = session.downloadTask(with: request)
        task.taskDescription = next.fileName
        task.resume()
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        lock.lock()
        let done = downloadedBeforeCurrent + totalBytesWritten
        let now = Date()
        let elapsed = max(now.timeIntervalSince(sampleDate), 0.25)
        let rate = Int64(Double(max(0, done - sampleBytes)) / elapsed)
        if elapsed >= 1 {
            sampleBytes = done
            sampleDate = now
        }
        lock.unlock()
        let remaining = max(0, SunnyModelCatalog.totalBytes - done)
        let eta = rate > 0 ? Double(remaining) / Double(rate) : nil
        DispatchQueue.main.async {
            self.state = .downloading(
                bytes: done,
                total: SunnyModelCatalog.totalBytes,
                bytesPerSecond: rate,
                eta: eta
            )
        }
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        guard let name = downloadTask.taskDescription,
              let asset = SunnyModelCatalog.assets.first(where: { $0.fileName == name }) else { return }
        DispatchQueue.main.async { self.state = .verifying }
        do {
            guard try Self.sha256(url: location) == asset.sha256 else {
                throw CocoaError(.fileReadCorruptFile)
            }
            let destination = installDirectory.appending(path: asset.fileName)
            try? FileManager.default.removeItem(at: destination)
            try FileManager.default.moveItem(at: location, to: destination)
            try FileManager.default.setAttributes(
                [.protectionKey: FileProtectionType.complete],
                ofItemAtPath: destination.path
            )
            lock.lock()
            downloadedBeforeCurrent = installedByteCount()
            sampleBytes = downloadedBeforeCurrent
            sampleDate = Date()
            lock.unlock()
            DispatchQueue.main.async { self.startNextMissingAsset() }
        } catch {
            DispatchQueue.main.async { self.state = .failed("Model verification failed. Please retry.") }
        }
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        guard let error = error as? URLError, error.code != .cancelled else { return }
        DispatchQueue.main.async { self.state = .failed(error.localizedDescription) }
    }

    private func installedByteCount() -> Int64 {
        SunnyModelCatalog.assets.reduce(Int64(0)) { result, asset in
            let url = installDirectory.appending(path: asset.fileName)
            let size = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize).map(Int64.init) ?? 0
            return result + min(size, asset.byteCount)
        }
    }

    private static func availableCapacity() -> Int64 {
        let home = URL(fileURLWithPath: NSHomeDirectory())
        return (try? home.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey])
            .volumeAvailableCapacityForImportantUsage) ?? 0
    }

    private static func sha256(url: URL) throws -> String {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var digest = SHA256()
        while true {
            let chunk = try handle.read(upToCount: 4 * 1_024 * 1_024) ?? Data()
            if chunk.isEmpty { break }
            digest.update(data: chunk)
        }
        return digest.finalize().map { String(format: "%02x", $0) }.joined()
    }
}
