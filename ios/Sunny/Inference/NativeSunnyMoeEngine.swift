import Foundation

enum NativeRuntimeAvailability {
    static var isLinked: Bool {
        #if SUNNY_MOE_RUNTIME
        true
        #else
        false
        #endif
    }
}

/// The Swift side of the optional iOS native runtime. The default target remains
/// buildable without proprietary weights or a native XCFramework. Define
/// `SUNNY_MOE_RUNTIME` only after linking a library that implements the C ABI in
/// `ios/README.md`.
final class NativeSunnyMoeEngine: @unchecked Sendable, InferenceClient {
    let textModelURL: URL
    let projectorURL: URL
    var modelVersion: String { "Sunny-MoE-2.2B-v4 (on-device)" }

    #if SUNNY_MOE_RUNTIME
    private var handle: OpaquePointer?
    #endif

    init(textModelURL: URL, projectorURL: URL) throws {
        self.textModelURL = textModelURL
        self.projectorURL = projectorURL
        #if SUNNY_MOE_RUNTIME
        handle = textModelURL.path.withCString { textPath in
            projectorURL.path.withCString { projectorPath in
                sunny_moe_create(textPath, projectorPath)
            }
        }
        guard handle != nil else { throw AnalysisError.serviceUnavailable }
        #else
        throw AnalysisError.serviceUnavailable
        #endif
    }

    deinit {
        #if SUNNY_MOE_RUNTIME
        if let handle { sunny_moe_destroy(handle) }
        #endif
    }

    func describe(jpeg: Data) async throws -> String {
        #if SUNNY_MOE_RUNTIME
        guard let handle else { throw AnalysisError.serviceUnavailable }
        return try await Task.detached(priority: .userInitiated) {
            let pointer = jpeg.withUnsafeBytes { bytes in
                SunnyPrompt.schema.withCString { prompt in
                    sunny_moe_describe(handle, bytes.bindMemory(to: UInt8.self).baseAddress, jpeg.count, prompt)
                }
            }
            guard let pointer else { throw AnalysisError.unreadable }
            defer { sunny_moe_free_string(pointer) }
            return String(cString: pointer)
        }.value
        #else
        throw AnalysisError.serviceUnavailable
        #endif
    }
}

#if SUNNY_MOE_RUNTIME
@_silgen_name("sunny_moe_create")
private func sunny_moe_create(_ textModel: UnsafePointer<CChar>, _ projector: UnsafePointer<CChar>) -> OpaquePointer?

@_silgen_name("sunny_moe_describe")
private func sunny_moe_describe(
    _ handle: OpaquePointer,
    _ jpeg: UnsafePointer<UInt8>?,
    _ jpegCount: Int,
    _ prompt: UnsafePointer<CChar>
) -> UnsafeMutablePointer<CChar>?

@_silgen_name("sunny_moe_free_string")
private func sunny_moe_free_string(_ value: UnsafeMutablePointer<CChar>)

@_silgen_name("sunny_moe_destroy")
private func sunny_moe_destroy(_ handle: OpaquePointer)
#endif
