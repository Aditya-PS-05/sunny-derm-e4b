import Foundation

enum SunnyPrompt {
    static let schema = """
    You are a dermatology description assistant. Look at this skin lesion photo and describe what you see. Do NOT diagnose or name a disease. Report only observable features in this exact format:
    Lesion Type: <descriptive category, e.g. pigmented macule / raised papule>
    Colour: <colours present>
    Symmetry: <symmetric / asymmetric>
    Borders: <smooth / irregular / well- or poorly-defined>
    Texture: <smooth / rough / raised / scaly>
    Summary: <one plain-language sentence describing the lesion's appearance and reminding the user this is not a diagnosis>
    """
}

enum SchemaParser {
    static func parse(_ text: String) -> Analysis? {
        guard let lesionType = field("Lesion Type", in: text),
              let colour = field("Colour", in: text) ?? field("Color", in: text),
              let symmetry = field("Symmetry", in: text),
              let borders = field("Borders", in: text),
              let texture = field("Texture", in: text),
              let summary = field("Summary", in: text) else { return nil }
        return Analysis(
            lesionType: lesionType,
            colour: colour,
            symmetry: symmetry,
            borders: borders,
            texture: texture,
            summary: summary
        )
    }

    private static func field(_ label: String, in text: String) -> String? {
        let escaped = NSRegularExpression.escapedPattern(for: label)
        guard let expression = try? NSRegularExpression(
            pattern: "^\(escaped):[\\t ]*([^\\r\\n]+)[\\t ]*$",
            options: [.anchorsMatchLines, .caseInsensitive]
        ) else { return nil }
        let range = NSRange(text.startIndex..., in: text)
        guard let match = expression.firstMatch(in: text, range: range),
              let capture = Range(match.range(at: 1), in: text) else { return nil }
        let value = text[capture].trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty || value.count > 600 ? nil : value
    }
}

enum OutputGuardrails {
    private static let banned = [
        "cancer", "melanoma", "carcinoma", "basal cell", "squamous", "benign", "malignant",
        "biopsy", "tumour", "tumor", "precancerous", "pre-cancerous", "metasta",
        "lesion is dangerous", "keratosis", "nevus", "nevi", "dermatofibroma", "risk",
        "urgent", "urgency", "safe to wait", "harmless", "reassur", "concerning",
        "suspicious", "worrisome", "dangerous", "recommend", "should see", "seek care",
    ]

    static func isClean(_ analysis: Analysis) -> Bool {
        analysis.rows.allSatisfy { _, value in
            let lower = value.lowercased()
            return !banned.contains(where: lower.contains)
        }
    }
}

struct AnalysisResult: Sendable {
    let analysis: Analysis
    let rawOutput: String
    let modelVersion: String
}

enum AnalysisError: LocalizedError {
    case serviceUnavailable
    case unreadable
    case invalidResponse
    case rejectedByGuardrail
    case server(Int, String)

    var errorDescription: String? {
        switch self {
        case .serviceUnavailable: "This analysis source is not available yet."
        case .unreadable: "Sunny couldn’t read this image. Try a closer, well-lit photo of one area."
        case .invalidResponse: "Sunny returned an incomplete response."
        case .rejectedByGuardrail: "Sunny suppressed an unsafe response. Please try another photo."
        case let .server(code, _): "Sunny AI Cloud returned an error (\(code))."
        }
    }
}

protocol InferenceClient: Sendable {
    var modelVersion: String { get }
    func describe(jpeg: Data) async throws -> String
}

struct AnalysisCoordinator: Sendable {
    let client: any InferenceClient

    func analyze(jpeg: Data) async throws -> AnalysisResult {
        var lastError: AnalysisError = .invalidResponse
        for _ in 0..<2 {
            let raw = try await client.describe(jpeg: jpeg)
            guard let analysis = SchemaParser.parse(raw) else {
                lastError = .invalidResponse
                continue
            }
            guard OutputGuardrails.isClean(analysis) else {
                lastError = .rejectedByGuardrail
                continue
            }
            return AnalysisResult(analysis: analysis, rawOutput: raw, modelVersion: client.modelVersion)
        }
        throw lastError
    }
}

