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

    var normalized: Analysis { AnalysisVocabulary.normalize(self) }
}

enum AnalysisVocabulary {
    static func normalize(_ raw: Analysis) -> Analysis {
        let type = lesionType(raw.lesionType)
        let colour = colour(raw.colour)
        let symmetry = symmetry(raw.symmetry)
        let borders = borders(raw.borders)
        let texture = texture(typeValue: raw.lesionType, textureValue: raw.texture)
        return Analysis(
            lesionType: type,
            colour: colour,
            symmetry: symmetry,
            borders: borders,
            texture: texture,
            summary: summary(type: type, colour: colour, symmetry: symmetry, borders: borders, texture: texture)
        )
    }

    private static func lesionType(_ value: String) -> String {
        let text = clean(value)
        if text == "visible skin mark" { return "Visible skin mark" }
        if hasAny(text, ["blister", "vesicle", "fluid-filled", "fluid filled"]) { return "Fluid-filled bump" }
        if hasAny(text, ["papule", "nodule", "bump", "raised", "elevated"]) { return "Raised spot" }
        if hasAny(text, ["patch", "plaque", "area"]) { return "Patch" }
        if hasAny(text, ["macule", "spot", "freckle", "dot", "mark"]) { return "Flat spot" }
        return "Visible skin mark"
    }

    private static func colour(_ value: String) -> String {
        let text = clean(value)
        var values: [String] = []
        func add(_ value: String) { if !values.contains(value) { values.append(value) } }
        if hasAny(text, ["skin-toned", "skin toned", "skin-coloured", "skin colored"]) { add("Skin-coloured") }
        if hasAny(text, ["light and dark brown", "dark and light brown"]) {
            add("Light brown"); add("Dark brown")
        } else {
            if text.contains("light brown") { add("Light brown") }
            if text.contains("dark brown") { add("Dark brown") }
        }
        if text.contains("brown") && !text.contains("light brown") && !text.contains("dark brown") { add("Brown") }
        if text.contains("tan") { add("Tan") }
        if matches(text, #"\bpink(?:ish)?\b"#) { add("Pink") }
        if matches(text, #"\bred(?:dish)?\b"#) { add("Red") }
        if matches(text, #"\b(?:purple|violet)\b"#) { add("Purple") }
        if matches(text, #"\b(?:blue|bluish)\b"#) { add("Blue") }
        if matches(text, #"\bblack(?:ish)?\b"#) { add("Black") }
        if matches(text, #"\b(?:white|whitish)\b"#) { add("White") }
        if matches(text, #"\byellow(?:ish)?\b"#) { add("Yellow") }
        switch values.count {
        case 0: return "Colour unclear"
        case 1: return values[0]
        case 2: return values.joined(separator: " and ")
        default: return values.prefix(2).joined(separator: ", ") + ", and " + values[2]
        }
    }

    private static func symmetry(_ value: String) -> String {
        let text = clean(value)
        let negated = hasAny(text, ["not symmetric", "not symmetrical"])
        let asymmetric = matches(text, #"\b(?:asymmetric|asymmetrical)\b"#) ||
            negated || hasAny(text, ["uneven shape", "irregular shape"])
        let symmetric = (!negated && matches(text, #"\b(?:symmetric|symmetrical)\b"#)) ||
            hasAny(text, ["roughly even", "even shape", "balanced"])
        if asymmetric && symmetric { return "Symmetry unclear" }
        if asymmetric { return "Asymmetric" }
        if symmetric { return "Symmetric" }
        return "Symmetry unclear"
    }

    private static func borders(_ value: String) -> String {
        let text = clean(value)
        if hasAny(text, ["unclear", "not clear", "cannot tell", "can't tell"]) { return "Border detail unclear" }
        let irregular = matches(text, #"\b(?:irregular|ragged|uneven|notched|scalloped)\b"#)
        let smooth = matches(text, #"\b(?:smooth|regular|even)\b"#)
        let poorlyDefined = hasAny(text, [
            "poorly-defined", "poorly defined", "ill-defined", "ill defined", "blurred", "blurry",
            "fuzzy", "indistinct", "diffuse",
        ])
        let wellDefined = hasAny(text, ["well-defined", "well defined"]) ||
            matches(text, #"\b(?:clear|sharp|distinct)\b"#)
        if (irregular && smooth) || (poorlyDefined && wellDefined) { return "Border detail unclear" }
        var values: [String] = []
        if irregular { values.append("Irregular") } else if smooth { values.append("Smooth") }
        if poorlyDefined { values.append("poorly defined") } else if wellDefined { values.append("well defined") }
        return values.isEmpty ? "Border detail unclear" : values.joined(separator: " and ")
    }

    private static func texture(typeValue: String, textureValue: String) -> String {
        let text = clean(typeValue + " " + textureValue)
        let scaly = hasAny(text, ["scaly", "scale", "flaky", "flaking", "crust", "crusted"])
        let rough = hasAny(text, ["rough", "coarse"])
        let smooth = hasAny(text, ["smooth", "even surface"])
        let raised = hasAny(text, ["raised", "elevated"])
        if smooth && (rough || scaly) { return "Texture unclear" }
        if scaly && rough { return "Rough and scaly" }
        if scaly { return "Scaly" }
        if rough { return "Rough" }
        if smooth { return "Smooth" }
        if raised { return "Raised" }
        return "Texture unclear"
    }

    private static func summary(type: String, colour: String, symmetry: String, borders: String, texture: String) -> String {
        let opening = colour == "Colour unclear"
            ? "This image shows a \(type.lowercased()); its colour is unclear."
            : "This image shows a \(colour.lowercased()) \(type.lowercased())."
        let shape = symmetry == "Symmetric" ? "Its shape appears symmetric."
            : symmetry == "Asymmetric" ? "Its shape appears asymmetric." : "Its symmetry is unclear."
        let border = borders == "Border detail unclear" ? "The border detail is unclear."
            : "The border appears \(borders.lowercased())."
        let surface = texture == "Texture unclear" ? "The surface texture is unclear."
            : "The surface appears \(texture.lowercased())."
        return "\(opening) \(shape) \(border) \(surface)"
    }

    private static func clean(_ value: String) -> String {
        value.lowercased()
            .replacingOccurrences(of: "–", with: "-")
            .replacingOccurrences(of: "—", with: "-")
            .split(whereSeparator: { $0.isWhitespace })
            .joined(separator: " ")
    }

    private static func hasAny(_ text: String, _ terms: [String]) -> Bool { terms.contains(where: text.contains) }
    private static func matches(_ text: String, _ pattern: String) -> Bool {
        text.range(of: pattern, options: .regularExpression) != nil
    }
}

struct AnalysisChange: Identifiable, Equatable, Sendable {
    var id: String { label }
    let label: String
    let previous: String
    let current: String
}

enum AnalysisComparison {
    static func changes(previous: Analysis, current: Analysis) -> [AnalysisChange] {
        let before = Dictionary(uniqueKeysWithValues: previous.normalized.rows.filter { $0.0 != "Summary" })
        let after = Dictionary(uniqueKeysWithValues: current.normalized.rows.filter { $0.0 != "Summary" })
        return Analysis.fieldNames.filter { $0 != "Summary" }.compactMap { label in
            guard let old = before[label], let new = after[label],
                  old.caseInsensitiveCompare(new) != .orderedSame else { return nil }
            return AnalysisChange(label: label, previous: old, current: new)
        }
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
    var title: String { self == .cloud ? "Sunny AI Cloud" : "Sunny Offline on this iPhone" }
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
