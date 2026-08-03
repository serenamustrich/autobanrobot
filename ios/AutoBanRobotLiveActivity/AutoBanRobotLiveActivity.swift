import ActivityKit
import SwiftUI
import WidgetKit

struct AutoBanRobotLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: AutoBanLiveActivityAttributes.self) { context in
            HStack(spacing: 8) {
                Image(systemName: "shield.fill")
                Text("Ban: \(context.state.banTotal) · 隐藏: \(context.state.hiddenTotal)")
                    .font(.headline)
            }
            .padding(.horizontal)
            .foregroundStyle(.white)
            .activityBackgroundTint(.black)
            .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.center) {
                    VStack(spacing: 2) {
                        Text("AutoBanRobot")
                            .font(.caption2)
                            .foregroundStyle(.white.opacity(0.78))
                        Text("Ban: \(context.state.banTotal) · 隐藏: \(context.state.hiddenTotal)")
                            .font(.headline)
                            .foregroundStyle(.white)
                    }
                }
            } compactLeading: {
                Image(systemName: "shield.fill")
                    .foregroundStyle(.red)
            } compactTrailing: {
                Text("\(context.state.banTotal)")
                    .monospacedDigit()
                    .fontWeight(.bold)
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            } minimal: {
                Text("\(context.state.banTotal)")
                    .monospacedDigit()
                    .fontWeight(.bold)
                    .foregroundStyle(.white)
            }
            .keylineTint(.red)
        }
    }
}

@main
struct AutoBanRobotLiveActivityBundle: WidgetBundle {
    var body: some Widget {
        AutoBanRobotLiveActivity()
    }
}
