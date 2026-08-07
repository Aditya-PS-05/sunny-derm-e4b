import XCTest
@testable import Sunny

final class SchemaParserTests: XCTestCase {
    func testParsesSixFields() throws {
        let text = """
        Lesion Type: flat pigmented macule
        Colour: light and dark brown
        Symmetry: asymmetric
        Borders: irregular and poorly-defined
        Texture: smooth
        Summary: A flat two-tone spot with uneven shape; this description is not a diagnosis.
        """
        let result = try XCTUnwrap(SchemaParser.parse(text))
        XCTAssertEqual(result.lesionType, "Flat spot")
        XCTAssertEqual(result.colour, "Light brown and Dark brown")
        XCTAssertEqual(result.symmetry, "Asymmetric")
        XCTAssertEqual(result.borders, "Irregular and poorly defined")
        XCTAssertEqual(result.texture, "Smooth")
        XCTAssertTrue(result.summary.hasPrefix("This image shows"))
    }

    func testRejectsPartialSchema() {
        XCTAssertNil(SchemaParser.parse("Lesion Type: papule\nColour: pink"))
    }

    func testAcceptsAmericanColorSpelling() {
        let text = """
        Lesion Type: raised papule
        Color: pink
        Symmetry: symmetric
        Borders: smooth
        Texture: raised
        Summary: A small raised pink spot; this is not a diagnosis.
        """
        XCTAssertEqual(SchemaParser.parse(text)?.colour, "Pink")
    }

    func testGuardrailRejectsDiagnosisLanguage() throws {
        let analysis = Analysis(
            lesionType: "melanoma",
            colour: "brown",
            symmetry: "asymmetric",
            borders: "irregular",
            texture: "smooth",
            summary: "A dark spot."
        )
        XCTAssertFalse(OutputGuardrails.isClean(analysis))
        XCTAssertFalse(OutputGuardrails.isClean("Possible melanoma with irregular borders"))
    }

    func testContradictoryTextureUsesUnclearControlledValue() throws {
        let analysis = Analysis(
            lesionType: "rough scaly patch",
            colour: "skin-toned and tan",
            symmetry: "roughly even surface",
            borders: "ragged",
            texture: "smooth, even surface",
            summary: "Generated text is replaced."
        ).normalized

        XCTAssertEqual(analysis.lesionType, "Patch")
        XCTAssertEqual(analysis.colour, "Skin-coloured and Tan")
        XCTAssertEqual(analysis.symmetry, "Symmetric")
        XCTAssertEqual(analysis.borders, "Irregular")
        XCTAssertEqual(analysis.texture, "Texture unclear")
        XCTAssertEqual(analysis, analysis.normalized)
    }
}
