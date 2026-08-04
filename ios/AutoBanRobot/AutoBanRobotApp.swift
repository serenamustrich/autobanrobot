import SwiftUI

@main
struct AutoBanRobotApp: App {
    @StateObject private var state = AppState()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(state)
                .onAppear { state.startAppHeartbeat() }
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active {
                        state.startAppHeartbeat()
                    } else {
                        state.stopAppHeartbeat()
                    }
                }
        }
    }
}
