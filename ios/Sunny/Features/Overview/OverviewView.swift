import SwiftUI

struct OverviewView: View {
    @EnvironmentObject private var app: AppModel
    let onAddPhoto: () -> Void
    @State private var showingCheckSession = false

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Track visible skin changes over time.")
                        .font(.title2.bold())
                    Text("Private photos, consistent follow-ups and appearance descriptions in one place.")
                        .foregroundStyle(.secondary)
                }

                if app.areas.isEmpty {
                    SunnyCard {
                        Label("Start your first tracked area", systemImage: "camera.viewfinder")
                            .font(.headline)
                        Text("Take a clear photo now, then add follow-ups later to compare visible changes.")
                            .foregroundStyle(.secondary)
                            .padding(.vertical, 4)
                        Button("Add your first photo", action: onAddPhoto)
                            .buttonStyle(.borderedProminent)
                    }
                }

                HStack(spacing: 10) {
                    metric(value: app.photoCount, label: "Photos", icon: "photo.stack")
                    metric(value: app.areas.count, label: "Areas", icon: "scope")
                    metric(value: app.updateCount, label: "Updated", icon: "arrow.triangle.2.circlepath")
                }

                SunnyCard {
                    HStack(spacing: 18) {
                        Image(systemName: "figure.stand")
                            .font(.system(size: 72, weight: .light))
                            .foregroundStyle(SunnyTheme.orange.gradient)
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Body coverage").font(.headline)
                            Text("\(Set(app.areas.map(\.bodyPart)).count) locations tracked")
                                .foregroundStyle(.secondary)
                            ProgressView(value: Double(Set(app.areas.map(\.bodyPart)).count), total: Double(BodyPart.allCases.count))
                                .tint(SunnyTheme.orange)
                        }
                    }
                }

                if !app.areas.isEmpty {
                    Button {
                        showingCheckSession = true
                    } label: {
                        Label("Review tracked areas", systemImage: "checklist")
                            .frame(maxWidth: .infinity, minHeight: 48)
                    }
                    .buttonStyle(.bordered)
                }

                if !app.areas.isEmpty {
                    Text("Recently tracked").font(.title3.bold()).padding(.top, 4)
                    ForEach(app.areas.prefix(4)) { area in
                        NavigationLink {
                            AreaDetailView(areaID: area.id)
                        } label: {
                            AreaRow(area: area)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding()
            .padding(.bottom, 76)
        }
        .background(SunnyTheme.background)
        .navigationTitle("Sunny")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: onAddPhoto) { Image(systemName: "camera.fill") }
                    .accessibilityLabel("Add photo")
            }
        }
        .sheet(isPresented: $showingCheckSession) { CheckSessionView() }
    }

    private func metric(value: Int, label: String, icon: String) -> some View {
        VStack(spacing: 5) {
            Image(systemName: icon).foregroundStyle(SunnyTheme.orange)
            Text(value.formatted()).font(.title2.bold()).contentTransition(.numericText())
            Text(label).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, minHeight: 94)
        .background(SunnyTheme.surface, in: RoundedRectangle(cornerRadius: 18))
        .accessibilityElement(children: .combine)
    }
}
