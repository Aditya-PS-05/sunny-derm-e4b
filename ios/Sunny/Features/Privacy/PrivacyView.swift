import SwiftUI

struct PrivacyView: View {
    private let sections: [(String, String, String)] = [
        (
            "Device Vault",
            "lock.shield",
            "Tracking records and photos are encrypted with AES-256-GCM. The encryption key is stored in the iOS Keychain as device-only data, and files use complete iOS Data Protection."
        ),
        (
            "Cloud analysis",
            "cloud",
            "When Sunny AI Cloud is selected, only the photo you explicitly analyse is sent to the configured HTTPS endpoint. Cloud processing should use authenticated, rate-limited infrastructure with a documented retention policy."
        ),
        (
            "On-device analysis",
            "iphone",
            "When Sunny Offline is installed and selected, analysis runs locally and the photo does not leave this iPhone. The approximately 393 MB model pack is stored inside Sunny’s protected app container. PAD-UFES-20 attribution and the SmolVLM Apache 2.0 license accompany the download."
        ),
        (
            "Helping improve Sunny",
            "hand.raised",
            "Contribution is off by default. Enabling it must be treated as explicit, versioned consent and should provide a deletion process before any public deployment."
        ),
        (
            "Medical limitations",
            "cross.case",
            "Sunny reports observable appearance only. It is not clinically validated, does not diagnose disease, and must not provide reassurance, urgency or treatment recommendations."
        ),
        (
            "Deleting data",
            "trash",
            "Deleting a tracked area removes its encrypted photos and history from this device. Deleting Sunny removes its app container and downloaded model from the device."
        ),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                SunnyCard {
                    Label("Your data flow", systemImage: "arrow.triangle.branch")
                        .font(.headline).foregroundStyle(SunnyTheme.orange)
                    Text("Capture → encrypted Device Vault → your selected analysis source")
                        .foregroundStyle(.secondary).padding(.top, 4)
                }
                ForEach(sections, id: \.0) { section in
                    DisclosureGroup {
                        Text(section.2).foregroundStyle(.secondary).padding(.top, 8)
                    } label: {
                        Label(section.0, systemImage: section.1).font(.headline)
                    }
                    .padding(16)
                    .background(SunnyTheme.surface, in: RoundedRectangle(cornerRadius: SunnyTheme.cornerRadius))
                }
            }
            .padding()
        }
        .background(SunnyTheme.background)
        .navigationTitle("Privacy")
        .navigationBarTitleDisplayMode(.inline)
    }
}
