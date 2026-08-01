import Foundation

struct CloudInferenceClient: InferenceClient {
    let baseURL: URL
    let bearerToken: String
    var modelVersion: String { "Sunny-Gemma4-E4B (server)" }

    func describe(jpeg: Data) async throws -> String {
        let endpoint = baseURL.appending(path: "v1/chat/completions")
        guard endpoint.scheme == "https" else { throw AnalysisError.serviceUnavailable }

        let dataURI = "data:image/jpeg;base64,\(jpeg.base64EncodedString())"
        let body: [String: Any] = [
            "messages": [[
                "role": "user",
                "content": [
                    ["type": "image_url", "image_url": ["url": dataURI]],
                    ["type": "text", "text": SunnyPrompt.schema],
                ],
            ]],
            "max_tokens": 1024,
            "temperature": 0.0,
        ]
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.timeoutInterval = 180
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(UUID().uuidString, forHTTPHeaderField: "X-Analysis-ID")
        if !bearerToken.isEmpty {
            request.setValue("Bearer \(bearerToken)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw AnalysisError.invalidResponse }
        guard 200..<300 ~= http.statusCode else {
            throw AnalysisError.server(http.statusCode, String(data: data, encoding: .utf8) ?? "")
        }
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let choices = root["choices"] as? [[String: Any]],
              let message = choices.first?["message"] as? [String: Any],
              let content = message["content"] as? String else { throw AnalysisError.invalidResponse }

        let block: Substring
        if let range = content.range(of: "Lesion Type:", options: .backwards) {
            block = content[range.lowerBound...]
        } else {
            block = content[...]
        }
        return block.replacingOccurrences(
            of: "<[|/a-zA-Z_]{0,32}>",
            with: "",
            options: .regularExpression
        ).trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

