import SwiftUI

struct AreasView: View {
    @EnvironmentObject private var app: AppModel
    let onAddPhoto: () -> Void
    @State private var query = ""
    @State private var areaToDelete: TrackedArea?

    private var filtered: [TrackedArea] {
        guard !query.isEmpty else { return app.areas }
        return app.areas.filter {
            $0.name.localizedCaseInsensitiveContains(query) ||
                $0.bodyPart.locationLine.localizedCaseInsensitiveContains(query)
        }
    }

    var body: some View {
        Group {
            if app.areas.isEmpty {
                EmptyStateView(
                    icon: "square.stack.3d.up",
                    title: "No tracked areas",
                    message: "Saved photos and follow-ups will appear here.",
                    actionTitle: "Add photo",
                    action: onAddPhoto
                )
            } else {
                List {
                    ForEach(filtered) { area in
                        NavigationLink { AreaDetailView(areaID: area.id) } label: { AreaRow(area: area) }
                            .swipeActions(edge: .trailing) {
                                Button("Delete", role: .destructive) { areaToDelete = area }
                            }
                    }
                }
                .listStyle(.insetGrouped)
                .searchable(text: $query, prompt: "Search tracked areas")
                .overlay {
                    if filtered.isEmpty {
                        ContentUnavailableView.search(text: query)
                    }
                }
            }
        }
        .navigationTitle("Tracked Areas")
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                NavigationLink { ReportsView() } label: { Label("Reports", systemImage: "doc.text") }
                Button(action: onAddPhoto) { Image(systemName: "plus") }.accessibilityLabel("Add photo")
            }
        }
        .alert("Delete this tracked area?", isPresented: Binding(
            get: { areaToDelete != nil }, set: { if !$0 { areaToDelete = nil } }
        ), presenting: areaToDelete) { area in
            Button("Delete", role: .destructive) { Task { await app.deleteArea(id: area.id) } }
            Button("Cancel", role: .cancel) {}
        } message: { _ in
            Text("Its encrypted photos and history will be permanently removed from this device.")
        }
    }
}

struct AreaRow: View {
    let area: TrackedArea

    var body: some View {
        HStack(spacing: 13) {
            if let latest = area.latest {
                VaultImageView(name: latest.encryptedImageName)
                    .frame(width: 62, height: 62)
                    .clipShape(RoundedRectangle(cornerRadius: 13))
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(area.name).font(.headline).lineLimit(1)
                Text(area.bodyPart.locationLine).font(.subheadline).foregroundStyle(.secondary)
                Text("\(area.observations.count) photo\(area.observations.count == 1 ? "" : "s") · \(area.updatedAt.formatted(date: .abbreviated, time: .omitted))")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}

