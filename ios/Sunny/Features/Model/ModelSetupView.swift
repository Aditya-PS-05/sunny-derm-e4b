import SwiftUI

struct ModelSetupView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var subscription: SubscriptionManager
    @EnvironmentObject private var model: ModelDownloadManager
    @State private var showingPro = false
    @State private var betaToken = CredentialStore().inferenceToken

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                SunnyCard {
                    Label("Sunny AI Cloud", systemImage: "cloud.fill")
                        .font(.headline)
                        .foregroundStyle(SunnyTheme.orange)
                    Text("Included · no model download")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Text("Uses Sunny’s configured HTTPS server. The selected photo is uploaded for analysis and is not retained by the app.")
                        .foregroundStyle(.secondary)
                        .padding(.vertical, 5)
                    Button(app.analysisSource == .cloud ? "Cloud selected" : "Use cloud analysis") {
                        app.analysisSource = .cloud
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(app.analysisSource == .cloud)
                }

                SunnyCard {
                    HStack {
                        Label("Sunny MoE", systemImage: "iphone.and.arrow.forward")
                            .font(.headline)
                        Spacer()
                        Text("PRO").font(.caption.bold()).foregroundStyle(SunnyTheme.orange)
                    }
                    Text("3.08 GB · on this iPhone")
                        .font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    Text("Runs privately and offline after installation. A supported native runtime must be linked to the iOS target.")
                        .foregroundStyle(.secondary).padding(.vertical, 5)
                    modelControls
                }

                if !NativeRuntimeAvailability.isLinked {
                    Label(
                        "The Swift app is ready, but libsunny_moe.xcframework has not been linked. Cloud analysis works; local analysis remains disabled until that native runtime is added.",
                        systemImage: "hammer"
                    )
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 4)
                }

                #if DEBUG || BETA
                SunnyCard {
                    Text("Private beta access").font(.headline)
                    SecureField("Beta token", text: $betaToken)
                        .textInputAutocapitalization(.never)
                        .textContentType(.password)
                    Button("Save beta token") {
                        var store = CredentialStore()
                        store.inferenceToken = betaToken.trimmingCharacters(in: .whitespacesAndNewlines)
                    }
                    .buttonStyle(.bordered)
                }
                #endif

                Text("Sunny describes observable appearance only. It does not diagnose disease or replace professional care.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            .padding()
        }
        .background(SunnyTheme.background)
        .navigationTitle("AI Model")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showingPro) { ProUpsellView(feature: "offline analysis") }
    }

    @ViewBuilder private var modelControls: some View {
        switch model.state {
        case .unavailable:
            Label("No secure download source is configured.", systemImage: "exclamationmark.triangle")
                .font(.subheadline).foregroundStyle(.secondary)
        case .idle:
            Toggle("Allow mobile data", isOn: $model.allowsCellular)
            Button(subscription.hasProAccess ? "Download Sunny MoE" : "Get Pro to download") {
                if subscription.hasProAccess { model.start(hasProAccess: true) }
                else { showingPro = true }
            }
            .sunnyPrimaryButton()
        case let .downloading(bytes, total, rate, eta):
            ProgressView(value: Double(bytes), total: Double(total)).tint(SunnyTheme.orange)
            HStack {
                Text("\(bytes.formatted(.byteCount(style: .file))) of \(total.formatted(.byteCount(style: .file)))")
                Spacer()
                if let eta { Text(eta.formattedETA) }
            }
            .font(.caption).foregroundStyle(.secondary)
            if rate > 0 { Text("\(rate.formatted(.byteCount(style: .file)))/s").font(.caption).foregroundStyle(.secondary) }
            Button("Cancel download", role: .cancel) { model.cancel() }
        case .verifying:
            HStack { ProgressView(); Text("Verifying downloaded model…") }
        case .ready:
            Label("Model pack installed", systemImage: "checkmark.seal.fill")
                .foregroundStyle(SunnyTheme.success)
            if NativeRuntimeAvailability.isLinked {
                Button(app.analysisSource == .onDevice ? "On-device selected" : "Use on-device analysis") {
                    app.analysisSource = .onDevice
                }
                .buttonStyle(.borderedProminent)
                .disabled(app.analysisSource == .onDevice)
            }
            Button("Remove downloaded model", role: .destructive) { model.removeInstalledModel() }
        case let .failed(message):
            Label(message, systemImage: "exclamationmark.triangle").font(.subheadline).foregroundStyle(.red)
            Button("Try again") { model.start(hasProAccess: subscription.hasProAccess) }.buttonStyle(.bordered)
        }
    }
}

private extension TimeInterval {
    var formattedETA: String {
        let seconds = Int(self.rounded(.up))
        if seconds < 60 { return "About \(seconds)s left" }
        if seconds < 3_600 { return "About \((seconds + 59) / 60)m left" }
        return "About \(seconds / 3_600)h \((seconds % 3_600) / 60)m left"
    }
}

