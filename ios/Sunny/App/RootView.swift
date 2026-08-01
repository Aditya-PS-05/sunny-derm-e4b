import SwiftUI

struct RootView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var pin: PINManager

    var body: some View {
        Group {
            if !app.isLoaded {
                ProgressView("Opening your private vault…")
            } else if !app.hasSeenOnboarding {
                OnboardingView { app.hasSeenOnboarding = true }
            } else if pin.isEnabled && !pin.isUnlocked {
                PINLockView()
            } else {
                MainTabView()
            }
        }
        .animation(.smooth, value: app.isLoaded)
        .animation(.smooth, value: app.hasSeenOnboarding)
        .alert("Sunny", isPresented: Binding(
            get: { app.presentedError != nil },
            set: { if !$0 { app.presentedError = nil } }
        )) {
            Button("OK") { app.presentedError = nil }
        } message: {
            Text(app.presentedError ?? "")
        }
    }
}

struct MainTabView: View {
    @EnvironmentObject private var app: AppModel
    @State private var showingCapture = false

    var body: some View {
        TabView(selection: $app.selectedTab) {
            NavigationStack { OverviewView(onAddPhoto: { showingCapture = true }) }
                .tabItem { Label("Overview", systemImage: "house") }
                .tag(0)

            NavigationStack { AreasView(onAddPhoto: { showingCapture = true }) }
                .tabItem { Label("Areas", systemImage: "square.stack.3d.up") }
                .tag(1)

            NavigationStack { SettingsView() }
                .tabItem { Label("Settings", systemImage: "gearshape") }
                .tag(2)
        }
        .overlay(alignment: .bottom) {
            Button {
                showingCapture = true
            } label: {
                Image(systemName: "plus")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(.white)
                    .frame(width: 56, height: 56)
                    .background(SunnyTheme.orange, in: Circle())
                    .shadow(color: .black.opacity(0.18), radius: 12, y: 5)
            }
            .accessibilityLabel("Add photo")
            .padding(.bottom, 38)
        }
        .sheet(isPresented: $showingCapture) {
            CaptureFlow()
        }
    }
}

