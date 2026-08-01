import Foundation

enum BodyRegion: String, Codable, CaseIterable, Identifiable {
    case head, arms, torso, legs

    var id: Self { self }
    var label: String { rawValue.capitalized }
}

enum BodySide: String, Codable, CaseIterable, Identifiable {
    case front, back

    var id: Self { self }
    var label: String { rawValue.capitalized }
}

enum BodyPart: String, Codable, CaseIterable, Identifiable {
    case scalp, face, neck, chest, abdomen, upperBack, lowerBack, shoulder
    case leftArm, rightArm, leftHand, rightHand, leftLeg, rightLeg, middleToe, foot

    var id: Self { self }

    var label: String {
        switch self {
        case .upperBack: "Upper Back"
        case .lowerBack: "Lower Back"
        case .leftArm: "Left Arm"
        case .rightArm: "Right Arm"
        case .leftHand: "Left Hand"
        case .rightHand: "Right Hand"
        case .leftLeg: "Left Leg"
        case .rightLeg: "Right Leg"
        case .middleToe: "Middle Toe"
        default: rawValue.capitalized
        }
    }

    var region: BodyRegion {
        switch self {
        case .scalp, .face, .neck: .head
        case .leftArm, .rightArm, .leftHand, .rightHand: .arms
        case .chest, .abdomen, .upperBack, .lowerBack, .shoulder: .torso
        case .leftLeg, .rightLeg, .middleToe, .foot: .legs
        }
    }

    var side: BodySide {
        switch self {
        case .scalp, .upperBack, .lowerBack: .back
        default: .front
        }
    }

    var locationLine: String { "\(region.label) · \(label)" }
}

enum ScanKind: String, Codable, CaseIterable, Identifiable {
    case single
    case tracked

    var id: Self { self }
    var label: String { self == .single ? "Single photo" : "Tracked area" }
}

struct Analysis: Codable, Equatable, Sendable {
    var lesionType: String
    var colour: String
    var symmetry: String
    var borders: String
    var texture: String
    var summary: String

    static let fieldNames = ["Lesion Type", "Colour", "Symmetry", "Borders", "Texture", "Summary"]

    var rows: [(String, String)] {
        [
            ("Lesion Type", lesionType),
            ("Colour", colour),
            ("Symmetry", symmetry),
            ("Borders", borders),
            ("Texture", texture),
            ("Summary", summary),
        ]
    }
}

struct Observation: Codable, Identifiable, Equatable, Sendable {
    var id: UUID = UUID()
    var capturedAt: Date = Date()
    var encryptedImageName: String
    var analysis: Analysis
    var modelVersion: String
    var rawOutput: String
    var approximateSizeMM: Double?
}

struct TrackedArea: Codable, Identifiable, Equatable, Sendable {
    var id: UUID = UUID()
    var name: String
    var bodyPart: BodyPart
    var kind: ScanKind
    var createdAt: Date = Date()
    var updatedAt: Date = Date()
    var notes: String = ""
    var observations: [Observation] = []

    var latest: Observation? { observations.max(by: { $0.capturedAt < $1.capturedAt }) }
    var timeline: [Observation] { observations.sorted(by: { $0.capturedAt > $1.capturedAt }) }
}

struct ReminderItem: Codable, Identifiable, Equatable, Sendable {
    var id: String
    var areaID: UUID?
    var title: String
    var body: String
    var nextTrigger: Date
    var intervalHours: Int?
}

struct VaultPayload: Codable, Sendable {
    var schemaVersion = 1
    var areas: [TrackedArea] = []
    var reminders: [ReminderItem] = []
}

enum AnalysisSource: String, Codable, CaseIterable, Identifiable {
    case cloud
    case onDevice

    var id: Self { self }
    var title: String { self == .cloud ? "Sunny AI Cloud" : "Sunny MoE on this iPhone" }
}

enum AppRoute: Hashable {
    case area(UUID)
    case compare(UUID)
    case edit(UUID)
    case reports
    case report(URL)
    case modelSetup
    case privacy
    case pinSetup
    case reminders
}

