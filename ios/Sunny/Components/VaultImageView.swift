import SwiftUI

struct VaultImageView: View {
    @EnvironmentObject private var app: AppModel
    let name: String
    var contentMode: ContentMode = .fill
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else {
                ZStack {
                    Color.secondary.opacity(0.08)
                    ProgressView().controlSize(.small)
                }
            }
        }
        .task(id: name) { image = await app.image(named: name) }
        .accessibilityLabel("Tracked skin photo")
    }
}

struct AnalysisCard: View {
    let analysis: Analysis

    var body: some View {
        SunnyCard {
            VStack(alignment: .leading, spacing: 12) {
                Label("Visible features", systemImage: "sparkles")
                    .font(.headline)
                    .foregroundStyle(SunnyTheme.orange)
                ForEach(Array(analysis.normalized.rows.enumerated()), id: \.offset) { _, row in
                    VStack(alignment: .leading, spacing: 3) {
                        Text(row.0).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                        Text(row.1).font(.body)
                    }
                    if row.0 != "Summary" { Divider() }
                }
                Text("Sunny describes appearance only. This is not a diagnosis.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

struct AnalysisChangesCard: View {
    let since: Date
    let changes: [AnalysisChange]

    var body: some View {
        SunnyCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("What changed since \(since.formatted(date: .abbreviated, time: .omitted))")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text(changes.isEmpty
                     ? "No description differences found"
                     : "\(changes.count) \(changes.count == 1 ? "detail looks" : "details look") different")
                    .font(.title3.bold())

                ForEach(changes) { change in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(change.label)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(SunnyTheme.orange)
                        HStack(alignment: .center, spacing: 8) {
                            changeValue("Previous", change.previous)
                            Image(systemName: "arrow.right")
                                .foregroundStyle(.tertiary)
                                .accessibilityLabel("changed to")
                            changeValue("Current", change.current)
                        }
                    }
                    .padding(12)
                    .background(.background.opacity(0.55), in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(.separator.opacity(0.45)))
                }

                Label(
                    changes.isEmpty
                        ? "No description difference does not prove the area is unchanged. Lighting, framing, and AI descriptions can miss visual changes."
                        : "This compares AI-generated visual descriptions, not medical risk. Review the photos side by side or share them with a clinician.",
                    systemImage: "info.circle"
                )
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
    }

    private func changeValue(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption2).foregroundStyle(.tertiary)
            Text(value).font(.subheadline.weight(.medium))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct EmptyStateView: View {
    let icon: String
    let title: String
    let message: String
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        ContentUnavailableView {
            Label(title, systemImage: icon)
        } description: {
            Text(message)
        } actions: {
            if let actionTitle, let action {
                Button(actionTitle, action: action).buttonStyle(.borderedProminent)
            }
        }
    }
}
