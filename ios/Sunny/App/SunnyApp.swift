import SwiftUI

@main
struct SunnyApp: App {
    @StateObject private var appModel = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(appModel)
                .environmentObject(appModel.pin)
                .environmentObject(appModel.subscription)
                .environmentObject(appModel.modelDownload)
                .tint(SunnyTheme.orange)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { appModel.pin.lock() }
        }
    }
}

