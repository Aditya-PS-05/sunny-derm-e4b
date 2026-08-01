import UIKit

enum ImagePreparation {
    static func serverJPEG(from image: UIImage) throws -> Data {
        let normalized = image.normalizedOrientation()
        let maximum: CGFloat = 1_600
        let longest = max(normalized.size.width, normalized.size.height)
        let target: UIImage
        if longest > maximum {
            let scale = maximum / longest
            let size = CGSize(width: normalized.size.width * scale, height: normalized.size.height * scale)
            target = UIGraphicsImageRenderer(size: size).image { _ in
                normalized.draw(in: CGRect(origin: .zero, size: size))
            }
        } else {
            target = normalized
        }
        guard let data = target.jpegData(compressionQuality: 0.88), data.count <= 5 * 1_024 * 1_024 else {
            throw AnalysisError.unreadable
        }
        return data
    }
}

private extension UIImage {
    func normalizedOrientation() -> UIImage {
        guard imageOrientation != .up else { return self }
        return UIGraphicsImageRenderer(size: size).image { _ in
            draw(in: CGRect(origin: .zero, size: size))
        }
    }
}

