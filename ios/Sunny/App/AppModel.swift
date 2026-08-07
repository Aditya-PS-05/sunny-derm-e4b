import Foundation
import UIKit
import Combine

enum AnalysisProgress: Equatable {
    case idle
    case preparing
    case cloud
    case onDevice
    case ready
    case failed(String)
}

struct CaptureDraft {
    var image: UIImage
    var bodyPart: BodyPart = .shoulder
    var kind: ScanKind = .single
    var targetAreaID: UUID?
    var result: AnalysisResult?
    var approximateSizeMM: Double?
}

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var areas: [TrackedArea] = []
    @Published var selectedTab = 0
    @Published var captureDraft: CaptureDraft?
    @Published private(set) var analysisProgress: AnalysisProgress = .idle
    @Published var presentedError: String?
    @Published var generatedReports: [URL] = []
    @Published private(set) var isLoaded = false

    let configuration: AppConfiguration
    let subscription: SubscriptionManager
    let modelDownload: ModelDownloadManager
    let pin: PINManager

    private let vault: SecureVault
    private var repository: SunnyRepository?
    private var analysisTask: Task<Void, Never>?
    private let defaults = UserDefaults.standard

    init(configuration: AppConfiguration = .current) {
        self.configuration = configuration
        subscription = SubscriptionManager(configuration: configuration)
        modelDownload = .shared
        pin = PINManager()
        do {
            vault = try SecureVault()
        } catch {
            fatalError("Sunny could not create its protected vault: \(error)")
        }
        Task { await load() }
    }

    var analysisSource: AnalysisSource {
        get {
            guard let raw = defaults.string(forKey: "sunny.analysisSource"),
                  let value = AnalysisSource(rawValue: raw) else { return .cloud }
            return value
        }
        set {
            defaults.set(newValue.rawValue, forKey: "sunny.analysisSource")
            objectWillChange.send()
        }
    }

    var hasSeenOnboarding: Bool {
        get { defaults.bool(forKey: "sunny.seenOnboarding.v2") }
        set { defaults.set(newValue, forKey: "sunny.seenOnboarding.v2"); objectWillChange.send() }
    }

    var improveSunny: Bool {
        get { defaults.bool(forKey: "sunny.improveSunny.v1") }
        set { defaults.set(newValue, forKey: "sunny.improveSunny.v1"); objectWillChange.send() }
    }

    var photoCount: Int { areas.reduce(0) { $0 + $1.observations.count } }
    var updateCount: Int { areas.filter { $0.observations.count > 1 }.count }

    func beginCapture(image: UIImage, targetAreaID: UUID? = nil) {
        let area = targetAreaID.flatMap { id in areas.first(where: { $0.id == id }) }
        captureDraft = CaptureDraft(
            image: image,
            bodyPart: area?.bodyPart ?? .shoulder,
            kind: area == nil ? .single : .tracked,
            targetAreaID: targetAreaID
        )
        analysisProgress = .idle
    }

    func analyzeDraft() {
        analysisTask?.cancel()
        guard let draft = captureDraft else { return }
        analysisTask = Task {
            analysisProgress = .preparing
            do {
                let jpeg = try ImagePreparation.serverJPEG(from: draft.image)
                try Task.checkCancellation()
                let client: any InferenceClient
                switch analysisSource {
                case .cloud:
                    analysisProgress = .cloud
                    guard let url = configuration.inferenceBaseURL else { throw AnalysisError.serviceUnavailable }
                    client = CloudInferenceClient(baseURL: url, bearerToken: CredentialStore().inferenceToken)
                case .onDevice:
                    analysisProgress = .onDevice
                    guard subscription.hasProAccess, modelDownload.state == .ready else {
                        throw AnalysisError.serviceUnavailable
                    }
                    client = try NativeSunnyMoeEngine(
                        textModelURL: modelDownload.textModelURL,
                        projectorURL: modelDownload.projectorURL,
                        grammarURL: modelDownload.grammarURL
                    )
                }
                let result = try await AnalysisCoordinator(client: client).analyze(jpeg: jpeg)
                try Task.checkCancellation()
                captureDraft?.result = result
                analysisProgress = .ready
            } catch is CancellationError {
                analysisProgress = .idle
            } catch {
                analysisProgress = .failed(error.localizedDescription)
            }
        }
    }

    func cancelAnalysis() {
        analysisTask?.cancel()
        analysisTask = nil
        analysisProgress = .idle
    }

    func saveDraft() async -> UUID? {
        guard let repository, let draft = captureDraft, let result = draft.result else { return nil }
        do {
            let jpeg = try ImagePreparation.serverJPEG(from: draft.image)
            let area = try await repository.saveArea(
                imageJPEG: jpeg,
                bodyPart: draft.bodyPart,
                kind: draft.kind,
                analysis: result.analysis,
                modelVersion: result.modelVersion,
                rawOutput: result.rawOutput,
                approximateSizeMM: draft.approximateSizeMM,
                targetAreaID: draft.targetAreaID
            )
            captureDraft = nil
            analysisProgress = .idle
            await reload()
            return area.id
        } catch {
            presentedError = error.localizedDescription
            return nil
        }
    }

    func update(_ area: TrackedArea) async {
        do {
            try await repository?.update(area)
            await reload()
        } catch { presentedError = error.localizedDescription }
    }

    func deleteArea(id: UUID) async {
        do {
            try await repository?.deleteArea(id: id)
            await reload()
        } catch { presentedError = error.localizedDescription }
    }

    func image(named name: String) async -> UIImage? {
        guard let data = try? await repository?.image(named: name) else { return nil }
        return UIImage(data: data)
    }

    func generateReport(areaIDs: Set<UUID>) async -> URL? {
        let selected = areaIDs.isEmpty ? areas : areas.filter { areaIDs.contains($0.id) }
        guard !selected.isEmpty else { return nil }
        do {
            let url = try await ReportGenerator.create(areas: selected) { [weak self] name in
                guard let data = try await self?.repository?.image(named: name) else {
                    throw CocoaError(.fileReadNoSuchFile)
                }
                return data
            }
            generatedReports.insert(url, at: 0)
            return url
        } catch {
            presentedError = error.localizedDescription
            return nil
        }
    }

    func deleteReport(at offsets: IndexSet) {
        for index in offsets {
            guard generatedReports.indices.contains(index) else { continue }
            try? FileManager.default.removeItem(at: generatedReports[index])
        }
        for index in offsets.sorted(by: >) where generatedReports.indices.contains(index) {
            generatedReports.remove(at: index)
        }
    }

    private func load() async {
        do {
            repository = try await SunnyRepository(vault: vault)
            await reload()
            reloadGeneratedReports()
            isLoaded = true
        } catch {
            presentedError = error.localizedDescription
        }
    }

    private func reload() async {
        guard let snapshot = await repository?.snapshot() else { return }
        areas = snapshot.areas.sorted(by: { $0.updatedAt > $1.updatedAt })
    }

    private func reloadGeneratedReports() {
        let directory = FileManager.default.temporaryDirectory.appending(path: "SunnyReports", directoryHint: .isDirectory)
        generatedReports = ((try? FileManager.default.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: [.skipsHiddenFiles]
        )) ?? [])
            .filter { $0.pathExtension.lowercased() == "pdf" }
            .sorted {
                let lhs = (try? $0.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
                let rhs = (try? $1.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
                return lhs > rhs
            }
    }
}
