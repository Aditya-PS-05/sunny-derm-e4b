import Foundation

actor SunnyRepository {
    private let vault: SecureVault
    private var payload: VaultPayload

    init(vault: SecureVault) async throws {
        self.vault = vault
        payload = try await vault.load()
    }

    func snapshot() -> VaultPayload { payload }

    func saveArea(
        imageJPEG: Data,
        bodyPart: BodyPart,
        kind: ScanKind,
        analysis: Analysis,
        modelVersion: String,
        rawOutput: String,
        approximateSizeMM: Double? = nil,
        targetAreaID: UUID? = nil
    ) async throws -> TrackedArea {
        let imageName = try await vault.storeImage(imageJPEG)
        let observation = Observation(
            encryptedImageName: imageName,
            analysis: analysis,
            modelVersion: modelVersion,
            rawOutput: rawOutput,
            approximateSizeMM: approximateSizeMM
        )
        if let targetAreaID, let index = payload.areas.firstIndex(where: { $0.id == targetAreaID }) {
            payload.areas[index].observations.append(observation)
            payload.areas[index].updatedAt = Date()
            try await vault.save(payload)
            return payload.areas[index]
        }
        let area = TrackedArea(
            name: "\(kind.label) – \(bodyPart.label)",
            bodyPart: bodyPart,
            kind: kind,
            observations: [observation]
        )
        payload.areas.append(area)
        try await vault.save(payload)
        return area
    }

    func update(_ area: TrackedArea) async throws {
        guard let index = payload.areas.firstIndex(where: { $0.id == area.id }) else { return }
        payload.areas[index] = area
        try await vault.save(payload)
    }

    func deleteArea(id: UUID) async throws {
        guard let area = payload.areas.first(where: { $0.id == id }) else { return }
        for observation in area.observations {
            await vault.deleteImage(named: observation.encryptedImageName)
        }
        payload.areas.removeAll(where: { $0.id == id })
        try await vault.save(payload)
    }

    func image(named name: String) async throws -> Data { try await vault.image(named: name) }

    func setReminders(_ reminders: [ReminderItem]) async throws {
        payload.reminders = reminders
        try await vault.save(payload)
    }
}
