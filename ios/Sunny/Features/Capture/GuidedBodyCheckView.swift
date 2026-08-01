import SwiftUI

struct GuidedBodyCheckView: View {
    @State private var selectedPart: BodyPart?
    @State private var completed: Set<BodyPart> = []

    var body: some View {
        List {
            Section {
                Text("Choose a location, photograph one visible area and return to continue. You can stop at any time.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            ForEach(BodyRegion.allCases) { region in
                Section(region.label) {
                    ForEach(BodyPart.allCases.filter { $0.region == region }) { part in
                        Button { selectedPart = part } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(part.label).foregroundStyle(.primary)
                                    Text(framingHint(for: part)).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Image(systemName: completed.contains(part) ? "checkmark.circle.fill" : "camera")
                                    .foregroundStyle(completed.contains(part) ? SunnyTheme.success : SunnyTheme.orange)
                            }
                            .contentShape(Rectangle())
                        }
                    }
                }
            }
        }
        .navigationTitle("Guided check")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $selectedPart) { part in
            CaptureFlow(presetBodyPart: part) { completed.insert(part) }
        }
    }

    private func framingHint(for part: BodyPart) -> String {
        switch part.region {
        case .head: "Use even light and keep facial features out when possible."
        case .arms: "Relax the arm and hold the camera parallel to the skin."
        case .torso: "Keep the phone level and fill the frame with one area."
        case .legs: "Sit or stand steadily and avoid casting a shadow."
        }
    }
}

