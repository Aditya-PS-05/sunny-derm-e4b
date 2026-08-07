import XCTest
@testable import Sunny

final class ModelCatalogTests: XCTestCase {
    func testPublishedPackMatchesAndroidCatalog() {
        XCTAssertEqual(SunnyModelCatalog.assets.count, 6)
        XCTAssertEqual(SunnyModelCatalog.totalBytes, 412_049_555)
        XCTAssertEqual(SunnyModelCatalog.assets[0].fileName, "sunny-pad-smolvlm-500m-Q4_K_M.gguf")
        XCTAssertEqual(
            SunnyModelCatalog.assets[1].sha256,
            "ac585ec2ee776eab23c4502f1a71d9d90a4057752057c05ed2487d47dc31798f"
        )
    }
}
