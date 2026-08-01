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
        XCTAssertEqual(result.colour, "light and dark brown")
        XCTAssertEqual(result.texture, "smooth")
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
        XCTAssertEqual(SchemaParser.parse(text)?.colour, "pink")
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
    }
}

