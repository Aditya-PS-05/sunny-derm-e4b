import SwiftUI

struct CheckSessionView: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var selectedArea: TrackedArea?
    @State private var completed: Set<UUID> = []
    @State private var skipped: Set<UUID> = []

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Review your tracked areas").font(.headline)
                        Text("Add a fresh photo to each area, or skip it for now. Progress stays on this screen while the session is open.")
                            .font(.subheadline).foregroundStyle(.secondary)
                        ProgressView(value: Double(completed.count + skipped.count), total: Double(max(app.areas.count, 1)))
                            .tint(SunnyTheme.orange)
                    }
                    .padding(.vertical, 4)
                }
                ForEach(app.areas) { area in
                    HStack(spacing: 12) {
                        AreaRow(area: area)
                        Spacer()
                        if completed.contains(area.id) {
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(SunnyTheme.success)
                        } else if skipped.contains(area.id) {
                            Text("Skipped").font(.caption).foregroundStyle(.secondary)
                        } else {
                            Menu {
                                Button("Add follow-up") { selectedArea = area }
                                Button("Skip for now") { skipped.insert(area.id) }
                            } label: {
                                Image(systemName: "ellipsis.circle").font(.title3)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Photo Check")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                        .disabled(completed.count + skipped.count < app.areas.count)
                }
            }
            .sheet(item: $selectedArea) { area in
                CaptureFlow(targetAreaID: area.id) { completed.insert(area.id) }
            }
        }
    }
}

