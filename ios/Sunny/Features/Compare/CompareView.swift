import SwiftUI

struct CompareView: View {
    @EnvironmentObject private var app: AppModel
    let areaID: UUID
    @State private var earlierID: UUID?
    @State private var laterID: UUID?

    private var area: TrackedArea? { app.areas.first(where: { $0.id == areaID }) }
    private var observations: [Observation] { area.map { Array($0.timeline.reversed()) } ?? [] }
    private var earlier: Observation? { observations.first(where: { $0.id == earlierID }) }
    private var later: Observation? { observations.first(where: { $0.id == laterID }) }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                if observations.count >= 2 {
                    SunnyCard {
                        Picker("Earlier", selection: $earlierID) {
                            ForEach(observations) { Text($0.capturedAt.formatted(date: .abbreviated, time: .omitted)).tag(Optional($0.id)) }
                        }
                        Picker("Later", selection: $laterID) {
                            ForEach(observations) { Text($0.capturedAt.formatted(date: .abbreviated, time: .omitted)).tag(Optional($0.id)) }
                        }
                    }
                    if let earlier, let later {
                        HStack(spacing: 8) {
                            comparisonPhoto(earlier, label: "Earlier")
                            comparisonPhoto(later, label: "Later")
                        }
                        AnalysisChangesCard(
                            since: earlier.capturedAt,
                            changes: AnalysisComparison.changes(
                                previous: earlier.analysis,
                                current: later.analysis
                            )
                        )
                    }
                } else {
                    EmptyStateView(icon: "rectangle.split.2x1", title: "Add another photo", message: "At least two photos are required for comparison.")
                }
            }
            .padding()
        }
        .background(SunnyTheme.background)
        .navigationTitle("Compare")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            guard observations.count >= 2 else { return }
            earlierID = earlierID ?? observations.first?.id
            laterID = laterID ?? observations.last?.id
        }
    }

    private func comparisonPhoto(_ observation: Observation, label: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            VaultImageView(name: observation.encryptedImageName)
                .aspectRatio(1, contentMode: .fill)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            Text(label).font(.caption.weight(.semibold))
            Text(observation.capturedAt.formatted(date: .abbreviated, time: .omitted))
                .font(.caption2).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}
