import XCTest
@testable import Sunny

final class ModelCatalogTests: XCTestCase {
    func testPublishedPackMatchesAndroidCatalog() {
        XCTAssertEqual(SunnyModelCatalog.assets.count, 3)
        XCTAssertEqual(SunnyModelCatalog.totalBytes, 3_082_369_677)
        XCTAssertEqual(SunnyModelCatalog.assets[0].fileName, "sunny-moe-text-Q4_K_M.gguf")
        XCTAssertEqual(
            SunnyModelCatalog.assets[1].sha256,
            "c4149a795d2c4af070d94e2130e2e1026d96bb912bbac1595b6fd12d376b91f4"
        )
    }
}

