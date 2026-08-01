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
                ForEach(Array(analysis.rows.enumerated()), id: \.offset) { _, row in
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

