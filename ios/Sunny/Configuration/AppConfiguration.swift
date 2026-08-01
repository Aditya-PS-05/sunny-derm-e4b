import Foundation

struct AppConfiguration: Sendable {
    let inferenceBaseURL: URL?
    let modelBaseURL: URL?
    let entitlementBaseURL: URL?
    let monthlyProductID: String
    let annualProductID: String

    static let current = AppConfiguration(
        inferenceBaseURL: infoURL("SUNNY_INFERENCE_API_URL"),
        modelBaseURL: infoURL("SUNNY_MODEL_BASE_URL"),
        entitlementBaseURL: infoURL("SUNNY_ENTITLEMENT_API_URL"),
        monthlyProductID: infoString("SUNNY_PRO_MONTHLY_PRODUCT_ID", fallback: "sunny.pro.monthly"),
        annualProductID: infoString("SUNNY_PRO_ANNUAL_PRODUCT_ID", fallback: "sunny.pro.annual")
    )

    private static func infoURL(_ key: String) -> URL? {
        let value = infoString(key, fallback: "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, let url = URL(string: value), url.scheme == "https" else { return nil }
        return url
    }

    private static func infoString(_ key: String, fallback: String) -> String {
        Bundle.main.object(forInfoDictionaryKey: key) as? String ?? fallback
    }
}

