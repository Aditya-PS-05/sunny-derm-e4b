import UIKit

enum ReportGenerator {
    static func create(areas: [TrackedArea], imageLoader: @escaping (String) async throws -> Data) async throws -> URL {
        let format = UIGraphicsPDFRendererFormat()
        let metadata: [String: Any] = [
            kCGPDFContextTitle as String: "Sunny skin tracking report",
            kCGPDFContextCreator as String: "Sunny",
        ]
        format.documentInfo = metadata
        let page = CGRect(x: 0, y: 0, width: 612, height: 792)
        let renderer = UIGraphicsPDFRenderer(bounds: page, format: format)
        let images = try await loadImages(areas: areas, loader: imageLoader)
        let data = renderer.pdfData { context in
            context.beginPage()
            var y: CGFloat = 52
            draw("Sunny skin tracking report", at: CGPoint(x: 48, y: y), font: .boldSystemFont(ofSize: 24))
            y += 38
            draw(
                "Generated \(Date().formatted(date: .long, time: .shortened)) · Descriptive tracking only—not a diagnosis.",
                at: CGPoint(x: 48, y: y),
                font: .systemFont(ofSize: 10),
                color: .secondaryLabel
            )
            y += 34

            for area in areas {
                if y > 650 { context.beginPage(); y = 48 }
                draw(area.name, at: CGPoint(x: 48, y: y), font: .boldSystemFont(ofSize: 16))
                y += 22
                draw(area.bodyPart.locationLine, at: CGPoint(x: 48, y: y), font: .systemFont(ofSize: 10), color: .secondaryLabel)
                y += 18
                guard let observation = area.latest else { continue }
                if let image = images[observation.encryptedImageName] {
                    image.draw(in: CGRect(x: 48, y: y, width: 100, height: 100))
                }
                var textY = y
                for (label, value) in observation.analysis.normalized.rows {
                    let line = "\(label): \(value)"
                    drawWrapped(line, in: CGRect(x: 166, y: textY, width: 398, height: 40), font: .systemFont(ofSize: 10))
                    textY += min(36, line.count > 75 ? 28 : 16)
                }
                y = max(y + 118, textY + 10)
            }
        }
        let directory = FileManager.default.temporaryDirectory.appending(path: "SunnyReports", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appending(path: "sunny-report-\(Int(Date().timeIntervalSince1970)).pdf")
        try data.write(to: url, options: [.atomic, .completeFileProtection])
        return url
    }

    private static func loadImages(
        areas: [TrackedArea],
        loader: @escaping (String) async throws -> Data
    ) async throws -> [String: UIImage] {
        var result: [String: UIImage] = [:]
        for area in areas {
            guard let name = area.latest?.encryptedImageName,
                  let image = UIImage(data: try await loader(name)) else { continue }
            result[name] = image
        }
        return result
    }

    private static func draw(_ text: String, at point: CGPoint, font: UIFont, color: UIColor = .label) {
        text.draw(at: point, withAttributes: [.font: font, .foregroundColor: color])
    }

    private static func drawWrapped(_ text: String, in rect: CGRect, font: UIFont) {
        text.draw(
            with: rect,
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: [.font: font, .foregroundColor: UIColor.label],
            context: nil
        )
    }
}
