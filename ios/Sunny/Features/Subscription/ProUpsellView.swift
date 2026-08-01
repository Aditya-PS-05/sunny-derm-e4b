import StoreKit
import SwiftUI

struct ProUpsellView: View {
    @EnvironmentObject private var subscription: SubscriptionManager
    @Environment(\.dismiss) private var dismiss
    let feature: String

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    Image(systemName: "sun.max.trianglebadge.exclamationmark.fill")
                        .font(.system(size: 58))
                        .foregroundStyle(SunnyTheme.orangeBright.gradient)
                    Text("Unlock \(feature)").font(.title.bold()).multilineTextAlignment(.center)
                    Text("Sunny Pro adds the downloadable on-device model, offline analysis, photo comparison and reports.")
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.secondary)

                    ForEach(subscription.products, id: \.id) { product in
                        Button {
                            Task { await subscription.purchase(product); if subscription.hasProAccess { dismiss() } }
                        } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(product.displayName).font(.headline)
                                    Text(product.description).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                                }
                                Spacer()
                                Text(product.displayPrice).font(.headline)
                            }
                            .padding()
                            .background(SunnyTheme.surface, in: RoundedRectangle(cornerRadius: 18))
                        }
                        .buttonStyle(.plain)
                    }

                    if subscription.products.isEmpty && !subscription.isLoading {
                        Text(subscription.errorMessage ?? "Subscriptions are not available in this build.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    Button("Restore purchases") { Task { await subscription.restore() } }
                    Text("Payment is charged through your Apple ID. Subscriptions renew automatically unless cancelled in App Store settings.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                .padding(24)
            }
            .background(SunnyTheme.background)
            .navigationTitle("Sunny Pro")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } } }
            .overlay { if subscription.isLoading { ProgressView().controlSize(.large) } }
        }
    }
}

