@preconcurrency import ActivityKit
import Foundation

@MainActor
enum LiveActivityManager {
    private static var activity: Activity<AutoBanLiveActivityAttributes>?
    private static var presentationRevision = 0
    private static var latestTotal = 0

    static func startOrUpdate(confirmedTotal: Int) {
        Task { @MainActor in
            await startOrUpdateNow(confirmedTotal: confirmedTotal)
        }
    }

    private static func startOrUpdateNow(confirmedTotal: Int) async {
        latestTotal = confirmedTotal
        let authorization = ActivityAuthorizationInfo()
        guard authorization.areActivitiesEnabled else {
            print("[AutoBanRobot] Live Activity unavailable: disabled by system")
            return
        }
        if let activity {
            print("[AutoBanRobot] Live Activity updated: Ban \(confirmedTotal)")
            await activity.update(nextContent())
            return
        }
        if let existing = Activity<AutoBanLiveActivityAttributes>.activities.first(where: isReusable) {
            activity = existing
            observe(existing)
            await existing.update(nextContent())
            print("[AutoBanRobot] Live Activity adopted id=\(existing.id) Ban \(confirmedTotal)")
            return
        }
        await requestActivity()
    }

    private static func isReusable(_ activity: Activity<AutoBanLiveActivityAttributes>) -> Bool {
        switch activity.activityState {
        case .active, .stale:
            return true
        case .ended, .dismissed:
            return false
        @unknown default:
            return false
        }
    }

    private static func requestActivity() async {
        do {
            let attributes = AutoBanLiveActivityAttributes(title: "AutoBanRobot")
            let started = try Activity.request(
                attributes: attributes,
                content: nextContent(),
                pushType: nil
            )
            activity = started
            observe(started)
            print("[AutoBanRobot] Live Activity started id=\(started.id) Ban \(latestTotal)")
        } catch {
            print("[AutoBanRobot] Live Activity start failed: \(error.localizedDescription)")
        }
    }

    private static func observe(_ activity: Activity<AutoBanLiveActivityAttributes>) {
        Task { @MainActor in
            for await state in activity.activityStateUpdates {
                print("[AutoBanRobot] Live Activity state id=\(activity.id): \(state)")
                if case .dismissed = state, Self.activity?.id == activity.id {
                    Self.activity = nil
                }
            }
        }
    }

    private static func nextContent() -> ActivityContent<AutoBanLiveActivityAttributes.ContentState> {
        presentationRevision += 1
        return ActivityContent(
            state: AutoBanLiveActivityAttributes.ContentState(
                banTotal: latestTotal,
                hiddenTotal: latestTotal,
                presentationRevision: presentationRevision
            ),
            staleDate: nil,
            relevanceScore: 100
        )
    }

    static func end() {
        guard let activity else { return }
        self.activity = nil
        print("[AutoBanRobot] Live Activity ended for background")
        Task { @MainActor in
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }

    static func endAll() {
        let activeActivities = Activity<AutoBanLiveActivityAttributes>.activities
        activity = nil
        Task { @MainActor in
            for item in activeActivities {
                await item.end(nil, dismissalPolicy: .immediate)
            }
        }
    }
}
