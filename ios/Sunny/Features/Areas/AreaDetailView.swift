import SwiftUI

struct AreaDetailView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var subscription: SubscriptionManager
    let areaID: UUID
    @State private var showingFollowUp = false
    @State private var showingHistory = false
    @State private var showingPro = false

    private var area: TrackedArea? { app.areas.first(where: { $0.id == areaID }) }

    var body: some View {
        Group {
            if let area, let latest = area.latest {
                ScrollView {
                    VStack(spacing: 14) {
                        VaultImageView(name: latest.encryptedImageName)
                            .frame(maxWidth: .infinity)
                            .aspectRatio(1, contentMode: .fit)
                            .clipShape(RoundedRectangle(cornerRadius: 24))

                        SunnyCard {
                            Label(area.bodyPart.locationLine, systemImage: "mappin.and.ellipse")
                                .font(.headline)
                            Text("Last photographed \(latest.capturedAt.formatted(date: .long, time: .shortened))")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .padding(.top, 3)
                        }

                        AnalysisCard(analysis: latest.analysis)

                        DisclosureGroup("Photo history (\(area.observations.count))", isExpanded: $showingHistory) {
                            ScrollView(.horizontal) {
                                HStack(spacing: 10) {
                                    ForEach(area.timeline) { observation in
                                        VStack(alignment: .leading, spacing: 4) {
                                            VaultImageView(name: observation.encryptedImageName)
                                                .frame(width: 112, height: 112)
                                                .clipShape(RoundedRectangle(cornerRadius: 14))
                                            Text(observation.capturedAt.formatted(date: .abbreviated, time: .omitted))
                                                .font(.caption)
                                        }
                                    }
                                }
                            }
                            .padding(.top, 10)
                        }
                        .padding(16)
                        .background(SunnyTheme.surface, in: RoundedRectangle(cornerRadius: SunnyTheme.cornerRadius))

                        HStack(spacing: 10) {
                            NavigationLink { EditAreaView(areaID: areaID) } label: {
                                Label("Edit", systemImage: "pencil").frame(maxWidth: .infinity, minHeight: 46)
                            }
                            .buttonStyle(.bordered)

                            if subscription.hasProAccess {
                                NavigationLink { CompareView(areaID: areaID) } label: {
                                    Label("Compare", systemImage: "rectangle.split.2x1")
                                        .frame(maxWidth: .infinity, minHeight: 46)
                                }
                                .buttonStyle(.bordered)
                                .disabled(area.observations.count < 2)
                            } else {
                                Button { showingPro = true } label: {
                                    Label("Compare · Pro", systemImage: "lock")
                                        .frame(maxWidth: .infinity, minHeight: 46)
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                    }
                    .padding()
                    .padding(.bottom, 72)
                }
                .safeAreaInset(edge: .bottom) {
                    Button("Add follow-up photo") { showingFollowUp = true }
                        .sunnyPrimaryButton()
                        .padding()
                        .background(.bar)
                }
            } else {
                EmptyStateView(icon: "photo", title: "Area unavailable", message: "This tracked area may have been deleted.")
            }
        }
        .background(SunnyTheme.background)
        .navigationTitle(area?.name ?? "Tracked area")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showingFollowUp) { CaptureFlow(targetAreaID: areaID) }
        .sheet(isPresented: $showingPro) { ProUpsellView(feature: "Photo comparison") }
    }
}

struct EditAreaView: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss
    let areaID: UUID
    @State private var name = ""
    @State private var notes = ""
    @State private var seeded = false
    @State private var showingDiscard = false

    private var original: TrackedArea? { app.areas.first(where: { $0.id == areaID }) }
    private var changed: Bool { seeded && (name != original?.name || notes != original?.notes) }

    var body: some View {
        Form {
            Section("Name") { TextField("Tracked area name", text: $name) }
            Section("Notes") {
                TextEditor(text: $notes).frame(minHeight: 130)
                Text("Notes stay encrypted in your Device Vault.").font(.caption).foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Edit tracked area")
        .navigationBarBackButtonHidden(changed)
        .toolbar {
            if changed {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { showingDiscard = true } }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") {
                    guard var area = original else { return }
                    area.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
                    area.notes = notes
                    area.updatedAt = Date()
                    Task { await app.update(area); dismiss() }
                }
                .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !changed)
            }
        }
        .onAppear {
            guard !seeded, let original else { return }
            name = original.name; notes = original.notes; seeded = true
        }
        .confirmationDialog("Discard unsaved changes?", isPresented: $showingDiscard, titleVisibility: .visible) {
            Button("Discard", role: .destructive) { dismiss() }
            Button("Keep editing", role: .cancel) {}
        }
    }
}
