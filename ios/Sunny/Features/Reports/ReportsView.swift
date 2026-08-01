import SwiftUI

struct ReportsView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var subscription: SubscriptionManager
    @State private var showingGenerator = false
    @State private var showingPro = false

    var body: some View {
        Group {
            if app.generatedReports.isEmpty {
                EmptyStateView(
                    icon: "doc.text",
                    title: "No reports yet",
                    message: "Create a protected PDF summary from selected tracked areas.",
                    actionTitle: "Create report",
                    action: openGenerator
                )
            } else {
                List {
                    ForEach(app.generatedReports, id: \.self) { url in
                        HStack {
                            Image(systemName: "doc.richtext.fill").foregroundStyle(SunnyTheme.orange)
                            VStack(alignment: .leading) {
                                Text("Skin tracking report").font(.headline)
                                Text(url.deletingPathExtension().lastPathComponent.replacingOccurrences(of: "sunny-report-", with: "Report "))
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            ShareLink(item: url) { Image(systemName: "square.and.arrow.up") }
                        }
                    }
                    .onDelete(perform: app.deleteReport)
                }
            }
        }
        .navigationTitle("Reports")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { Button("Create", action: openGenerator) }
        }
        .sheet(isPresented: $showingGenerator) { GenerateReportView() }
        .sheet(isPresented: $showingPro) { ProUpsellView(feature: "PDF reports") }
    }

    private func openGenerator() {
        if subscription.hasProAccess { showingGenerator = true }
        else { showingPro = true }
    }
}

private struct GenerateReportView: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var selected: Set<UUID> = []
    @State private var generating = false
    @State private var generatedURL: URL?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(app.areas) { area in
                        Button {
                            if selected.contains(area.id) { selected.remove(area.id) }
                            else { selected.insert(area.id) }
                        } label: {
                            HStack {
                                AreaRow(area: area)
                                Spacer()
                                Image(systemName: selected.contains(area.id) ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(selected.contains(area.id) ? SunnyTheme.orange : .tertiary)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                } header: {
                    HStack {
                        Text("Tracked areas")
                        Spacer()
                        Button(selected.count == app.areas.count ? "Clear all" : "Select all") {
                            selected = selected.count == app.areas.count ? [] : Set(app.areas.map(\.id))
                        }
                    }
                }

                if let generatedURL {
                    Section {
                        ShareLink(item: generatedURL) { Label("Share report", systemImage: "square.and.arrow.up") }
                    }
                }
            }
            .navigationTitle("Create Report")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(generating ? "Creating…" : "Create") {
                        generating = true
                        Task {
                            generatedURL = await app.generateReport(areaIDs: selected)
                            generating = false
                        }
                    }
                    .disabled(generating || selected.isEmpty)
                }
            }
            .overlay { if generating { ProgressView("Creating report…").padding().background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16)) } }
            .onAppear { selected = Set(app.areas.map(\.id)) }
        }
    }
}
