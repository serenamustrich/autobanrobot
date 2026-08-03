import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var state: AppState

    var body: some View {
        TabView {
            BrowserView(state: state)
                .tabItem { Label("浏览", systemImage: "globe") }

            NavigationStack { KeywordsView() }
                .tabItem { Label("关键词", systemImage: "text.magnifyingglass") }
            NavigationStack { RulesView() }
                .tabItem { Label("规则", systemImage: "slider.horizontal.3") }
            NavigationStack { HistoryView() }
                .tabItem { Label("Ban记录", systemImage: "list.bullet.rectangle") }
                .badge(state.confirmedTotal)
        }
        .tint(.blue)
        .onAppear {
            // Retire any activity created by an earlier build. This app no
            // longer starts or maintains a Live Activity.
            LiveActivityManager.endAll()
        }
        // Draw in the physical Dynamic Island region, not beneath the safe
        // area. The island covers it during normal use while screenshots keep
        // the intentional brand treatment.
        .overlay { DynamicIslandScreenshotBrand().ignoresSafeArea() }
    }
}

private struct DynamicIslandScreenshotBrand: UIViewRepresentable {
    func makeUIView(context: Context) -> BrandView { BrandView() }
    func updateUIView(_ uiView: BrandView, context: Context) { uiView.setNeedsLayout() }

    final class BrandView: UIView {
        private let label = UILabel()

        override init(frame: CGRect) {
            super.init(frame: frame)
            isUserInteractionEnabled = false
            backgroundColor = .clear
            label.text = "AutoBanRobot"
            label.textAlignment = .center
            label.textColor = .white
            label.font = .systemFont(ofSize: 11, weight: .bold)
            label.backgroundColor = .systemRed
            label.layer.cornerCurve = .continuous
            label.layer.cornerRadius = 18.5
            label.clipsToBounds = true
            addSubview(label)
        }

        required init?(coder: NSCoder) { nil }

        override func layoutSubviews() {
            super.layoutSubviews()
            // UIKit supplies this value for the current device, orientation,
            // and status-bar configuration. It is the public vertical anchor.
            let topInset = window?.safeAreaInsets.top ?? safeAreaInsets.top
            let islandSize = CGSize(width: 126, height: 37) // iPhone 16 hardware profile
            label.bounds.size = islandSize
            label.center = CGPoint(x: bounds.midX, y: topInset / 2)
        }
    }
}

private struct KeywordsView: View {
    @EnvironmentObject private var state: AppState
    @State private var input = ""
    @State private var loadingPopular = false
    @State private var saveStatus = ""
    @State private var pendingPopular: [String] = []
    @State private var newlyAdded = Set<String>()

    var body: some View {
        List {
            Section("新增关键词") {
                TextField("输入关键词", text: $input)
                Text("保存后会立即重新扫描当前 X 页面")
                    .font(.footnote).foregroundStyle(.secondary)
                Button("保存并立即生效") {
                    let keyword = input.trimmingCharacters(in: .whitespacesAndNewlines)
                    let additions = pendingPopular + (keyword.isEmpty ? [] : [keyword])
                    guard !additions.isEmpty else { return }
                    state.replaceKeywords(additions + state.keywords)
                    saveStatus = "已保存，立即生效"
                    input = ""
                    pendingPopular = []
                    newlyAdded = Set(additions)
                }
                .disabled(input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && pendingPopular.isEmpty)
            }
            Section {
                ForEach(displayedKeywords, id: \.self) { keyword in
                    HStack(spacing: 8) {
                        Text(keyword)
                        if newlyAdded.contains(keyword) {
                            Text("NEW")
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(.blue)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(.blue.opacity(0.12), in: Capsule())
                        }
                    }
                }
                .onDelete { offsets in
                    let deleted = offsets.map { displayedKeywords[$0] }
                    deleted.forEach { keyword in
                        if state.keywords.contains(keyword) {
                            state.removeKeyword(keyword)
                        } else {
                            pendingPopular.removeAll { $0 == keyword }
                        }
                        newlyAdded.remove(keyword)
                    }
                    if let first = deleted.first {
                        saveStatus = "已删除“\(first)”，立即生效"
                    }
                }
            } header: {
                HStack(spacing: 8) {
                    Text("当前关键词（\(displayedKeywords.count)）")
                    Spacer(minLength: 8)
                    if !saveStatus.isEmpty {
                        Text(saveStatus)
                            .lineLimit(1)
                            .font(.caption.weight(.medium))
                            .foregroundStyle(saveStatus.hasPrefix("无法") ? .red : .green)
                    }
                }
            }
        }
        .navigationTitle("关键词")
        .onAppear {
            newlyAdded.removeAll()
            if saveStatus == "已保存，立即生效" {
                saveStatus = ""
            }
        }
        .toolbar {
            Button(loadingPopular ? "加载中" : "加载热门") {
                loadingPopular = true
                saveStatus = ""
                Task {
                    do {
                        let popular = try await state.loadPopularKeywords()
                        let currentSet = Set(state.keywords + pendingPopular)
                        pendingPopular = popular.filter { !currentSet.contains($0) }
                        newlyAdded = Set(pendingPopular)
                        saveStatus = "新增 \(pendingPopular.count) 个，点击保存立即生效"
                    } catch {
                        saveStatus = "无法连接热门关键词服务，请稍后重试"
                    }
                    loadingPopular = false
                }
            }
            .disabled(loadingPopular)
        }
    }

    private var displayedKeywords: [String] {
        pendingPopular + state.keywords
    }
}

private struct RulesView: View {
    @EnvironmentObject private var state: AppState

    var body: some View {
        List {
            Section {
                Toggle("自动 Ban 命中账号", isOn: autoBanBinding)
                Text("仅在 X 确认屏蔽和隐藏后记入记录")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            Section("匹配规则") {
                ForEach(state.rules) { rule in
                    Toggle(isOn: ruleBinding(for: rule)) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(rule.name).font(.headline)
                            Text(rule.description).font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .navigationTitle("规则")
        .toolbar {
            Button("更新") { Task { await state.refreshRules() } }
        }
    }

    private var autoBanBinding: Binding<Bool> {
        Binding(
            get: { state.autoBanEnabled },
            set: { enabled in state.setAutoBanEnabled(enabled) }
        )
    }

    private func ruleBinding(for rule: RemoteRule) -> Binding<Bool> {
        Binding(
            get: { state.isRuleEnabled(rule) },
            set: { enabled in state.setRuleEnabled(rule, enabled: enabled) }
        )
    }
}

private struct HistoryView: View {
    @EnvironmentObject private var state: AppState
    @State private var visible = 10

    var body: some View {
        List {
            Section {
                Text("累计 \(state.confirmedTotal) 条已确认；本机仅保留最近 1000 条")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            if !state.queue.isEmpty {
                Section("处理队列（\(state.pendingCount)）") {
                    ForEach(state.queue) { job in
                        VStack(alignment: .leading, spacing: 5) {
                            Text("@\(job.username)").font(.headline)
                            Text(job.attempts == 0 ? "等待处理" : "第 \(job.attempts + 1) 次处理")
                                .font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                    .onDelete { state.removeQueued(at: $0) }
                }
            }
            Section("记录") {
                ForEach(Array(state.history.prefix(visible))) { item in
                    HistoryRecordRow(item: item)
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets(top: 3, leading: 16, bottom: 3, trailing: 16))
                }
                if visible < state.history.count {
                    ProgressView().frame(maxWidth: .infinity).onAppear { visible += 10 }
                }
            }
        }
        .navigationTitle("Ban记录")
        .toolbar { NavigationLink("白名单") { WhitelistView() } }
        .listSectionSpacing(8)
    }
}

private struct HistoryRecordRow: View {
    @EnvironmentObject private var state: AppState
    let item: BanRecord

    private var isWhitelisted: Bool { state.whitelist.contains(item.username.lowercased()) }
    private var isBlocked: Bool { item.unblockedAt == nil }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top, spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(item.displayName.isEmpty ? "@\(item.username)" : item.displayName)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    if !item.displayName.isEmpty {
                        Text("@\(item.username)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 8)
                Menu {
                    Button(isWhitelisted ? "移出白名单" : "加入白名单", systemImage: isWhitelisted ? "person.badge.minus" : "person.badge.plus") {
                        if isWhitelisted {
                            state.removeWhitelist(item.username)
                        } else {
                            state.addWhitelistAndUnblock(item)
                        }
                    }
                    Button(isBlocked ? "取消屏蔽和隐藏" : "重新屏蔽和隐藏", systemImage: isBlocked ? "arrow.uturn.backward" : "hand.raised.fill") {
                        if isBlocked {
                            state.unblock(item)
                        } else {
                            state.reblock(item)
                        }
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.primary)
                        .frame(width: 32, height: 32)
                        .background(.quaternary, in: Circle())
                }
                .accessibilityLabel("@\(item.username) 操作")
            }

            if !item.content.isEmpty {
                Text(item.content)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }

            HStack(spacing: 6) {
                StateChip(
                    text: isBlocked ? "已屏蔽 + 隐藏" : "已取消",
                    color: isBlocked ? .red : .secondary,
                    icon: isBlocked ? "hand.raised.fill" : "arrow.uturn.backward"
                )
                if isWhitelisted {
                    StateChip(text: "白名单", color: .green, icon: "checkmark.shield.fill")
                }
                Spacer(minLength: 4)
                Text(item.blockedAt.formatted(date: .numeric, time: .shortened))
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(10)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

private struct StateChip: View {
    let text: String
    let color: Color
    let icon: String

    var body: some View {
        Label(text, systemImage: icon)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 7)
            .padding(.vertical, 5)
            .background(color.opacity(0.12), in: Capsule())
    }
}

private struct WhitelistView: View {
    @EnvironmentObject private var state: AppState
    @State private var input = ""

    var body: some View {
        List {
            Section("新增白名单") {
                TextField("X 用户名", text: $input).textInputAutocapitalization(.never)
                Button("加入白名单") { state.addWhitelist(input); input = "" }
            }
            Section("本机白名单") {
                ForEach(Array(state.whitelist).sorted(), id: \.self) { username in
                    HStack {
                        Text("@\(state.displayUsername(username))")
                        Spacer()
                        if username == "aagodofwealth" { Text("默认").foregroundStyle(.secondary) }
                    }
                        .swipeActions { if username != "aagodofwealth" { Button("移除", role: .destructive) { state.removeWhitelist(username) } } }
                }
            }
        }
        .navigationTitle("白名单")
    }
}
