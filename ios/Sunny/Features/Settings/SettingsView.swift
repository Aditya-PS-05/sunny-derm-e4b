import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var pin: PINManager
    @EnvironmentObject private var subscription: SubscriptionManager
    @EnvironmentObject private var model: ModelDownloadManager
    @State private var showingPro = false
    @State private var improveSunny = false

    var body: some View {
        List {
            Section {
                HStack {
                    Label(subscription.hasProAccess ? "Sunny Pro" : "Sunny Free", systemImage: "sun.max.fill")
                    Spacer()
                    if !subscription.hasProAccess {
                        Button("View Pro") { showingPro = true }.buttonStyle(.bordered)
                    }
                }
            }

            Section("Analysis") {
                NavigationLink {
                    ModelSetupView()
                } label: {
                    settingsRow(
                        icon: app.analysisSource == .cloud ? "cloud.fill" : "iphone",
                        title: "AI Model",
                        detail: app.analysisSource.title
                    )
                }
                Picker("Analysis source", selection: Binding(
                    get: { app.analysisSource },
                    set: { source in
                        if source == .onDevice && (!subscription.hasProAccess || model.state != .ready || !NativeRuntimeAvailability.isLinked) {
                            showingPro = true
                        } else { app.analysisSource = source }
                    }
                )) {
                    Text("Cloud").tag(AnalysisSource.cloud)
                    Text("On-device").tag(AnalysisSource.onDevice)
                }
                .pickerStyle(.segmented)
            }

            Section("Device Vault") {
                settingsRow(icon: "lock.shield.fill", title: "Encrypted local storage", detail: "iOS Data Protection + AES-256-GCM")
                NavigationLink {
                    PINSetupView()
                } label: {
                    settingsRow(icon: "number.square", title: "App PIN", detail: pin.isEnabled ? "On" : "Optional")
                }
            }

            Section("Tracking") {
                NavigationLink { ReminderSettingsView() } label: {
                    settingsRow(icon: "bell", title: "Reminders", detail: "Review or change schedule")
                }
                NavigationLink { ReportsView() } label: {
                    settingsRow(icon: "doc.text", title: "Reports", detail: "Create and share reports")
                }
            }

            Section("Privacy") {
                Toggle(isOn: $improveSunny) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Help improve Sunny")
                        Text("Off by default; requires explicit contribution consent").font(.caption).foregroundStyle(.secondary)
                    }
                }
                .onChange(of: improveSunny) { _, value in app.improveSunny = value }
                NavigationLink { PrivacyView() } label: {
                    settingsRow(icon: "hand.raised", title: "Privacy and limitations", detail: "How Sunny handles your data")
                }
            }

            Section {
                Text("Sunny 1.0 · Appearance tracking only—not a diagnostic tool.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Settings")
        .onAppear { improveSunny = app.improveSunny }
        .sheet(isPresented: $showingPro) { ProUpsellView(feature: "Sunny Pro") }
    }

    private func settingsRow(icon: String, title: String, detail: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).foregroundStyle(SunnyTheme.orange).frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                Text(detail).font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 3)
    }
}

