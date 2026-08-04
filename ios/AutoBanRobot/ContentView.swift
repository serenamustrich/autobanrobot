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
            NavigationStack { AccountView() }
                .tabItem { Label("account.title", systemImage: "person.crop.circle") }
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

private struct AccountView: View {
    private enum Route { case login, register, recovery }
    @EnvironmentObject private var state: AppState
    @StateObject private var account = AccountClient()
    @State private var username = ""
    @State private var password = ""
    @State private var answer = ""
    @State private var newPassword = ""
    @State private var selectedQuestion = "first_teacher"
    @State private var recoveryQuestion: String?
    @State private var status = ""
    @State private var route: Route = .login
    @State private var isSyncing = false
    @State private var syncSucceeded = false
    @State private var selectedAchievement: ContributionAchievement?
    private let questions = ["first_teacher": "你第一位老师的名字是什么？", "childhood_nickname": "你童年的昵称是什么？", "first_pet": "你第一只宠物的名字是什么？", "favorite_book": "你最喜欢的书是什么？", "favorite_food": "你最喜欢的食物是什么？", "dream_job": "你儿时梦想的职业是什么？", "first_concert": "你第一次看的演唱会是什么？", "favorite_city": "你最喜欢的城市是什么？", "childhood_friend": "你童年好友的名字是什么？", "favorite_film": "你最喜欢的电影是什么？"]
    var body: some View {
        Form {
            if let session = account.session {
                Section {
                    AccountProfileCard(username: session.username, syncing: isSyncing, syncSucceeded: syncSucceeded) {
                        Task { await syncNow() }
                    }
                    .listRowInsets(EdgeInsets(top: 10, leading: 16, bottom: 8, trailing: 16))
                }
                Section("贡献与成就") {
                    ContributionSummaryCard(globalTotal: state.globalBanTotal, contribution: state.confirmedTotal, selectedBadge: $selectedAchievement)
                        .listRowInsets(EdgeInsets(top: 5, leading: 16, bottom: 7, trailing: 16))
                }
                Section { Button("account.logout", role: .destructive) { account.logout() } }
            } else {
                switch route {
                case .login:
                    Section("account.title") {
                        TextField("account.username", text: $username).textInputAutocapitalization(.never)
                        SecureField("account.password", text: $password)
                        Button("account.login") { Task { await perform { try await account.authenticate(mode: "login", payload: ["username": username, "password": password], state: state) } } }
                    }
                    Section {
                        Button("account.register") { withAnimation(.snappy) { route = .register } }
                        Button("account.recover") { withAnimation(.snappy) { route = .recovery } }
                    }
                case .register:
                    Section("注册账号") {
                        TextField("account.username", text: $username).textInputAutocapitalization(.never)
                        SecureField("account.password", text: $password)
                        Picker("account.security.question", selection: $selectedQuestion) { ForEach(questions.keys.sorted(), id: \.self) { Text(questions[$0]!).tag($0) } }
                        TextField("account.security.answer", text: $answer).textContentType(.none)
                        Button("account.register") { Task { await perform { try await account.authenticate(mode: "register", payload: ["username": username, "password": password, "securityQuestionKey": selectedQuestion, "securityAnswer": answer], state: state) } } }
                    }
                    Section { Button("返回登录") { withAnimation(.snappy) { route = .login } } }
                case .recovery:
                    Section("account.recover") {
                        TextField("account.username", text: $username).textInputAutocapitalization(.never)
                        if let recoveryQuestion {
                            Text(questions[recoveryQuestion] ?? "")
                                .font(.footnote).foregroundStyle(.secondary)
                            TextField("account.security.answer", text: $answer).textContentType(.none)
                            SecureField("account.new.password", text: $newPassword)
                            Button("account.reset") { Task { await perform { try await account.authenticate(mode: "recovery/reset", payload: ["username": username, "securityQuestionKey": recoveryQuestion, "securityAnswer": answer, "newPassword": newPassword], state: state) } } }
                        } else {
                            Button("account.recover") { Task { await loadRecoveryQuestion() } }
                        }
                    }
                    Section { Button("返回登录") { recoveryQuestion = nil; withAnimation(.snappy) { route = .login } } }
                }
            }
            if !status.isEmpty { Section { Text(status).font(.footnote).foregroundStyle(.secondary) } }
        }
        .navigationTitle("account.title")
        .overlay {
            if let badge = selectedAchievement {
                AchievementOverlay(badge: badge, contribution: state.confirmedTotal, earnedAt: state.achievementEarnedAt(threshold: badge.threshold)) {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.82)) { selectedAchievement = nil }
                }
                .transition(.opacity.combined(with: .scale(scale: 0.9)))
            }
        }
    }
    private func perform(_ action: () async throws -> Void) async {
        do { try await action(); status = "已完成并同步本机数据" } catch { status = error.localizedDescription }
    }
    private func loadRecoveryQuestion() async {
        do { recoveryQuestion = try await account.recoveryQuestion(username: username); status = "" }
        catch { status = error.localizedDescription }
    }
    private func syncNow() async {
        guard !isSyncing else { return }
        withAnimation(.snappy) { isSyncing = true; syncSucceeded = false }
        do {
            try await account.bindAndMerge(state: state)
            withAnimation(.spring(response: 0.35, dampingFraction: 0.62)) { isSyncing = false; syncSucceeded = true }
            try? await Task.sleep(for: .seconds(1.6))
            withAnimation(.easeOut(duration: 0.25)) { syncSucceeded = false }
        } catch {
            isSyncing = false
            status = error.localizedDescription
        }
    }
}

private struct AccountProfileCard: View {
    let username: String
    let syncing: Bool
    let syncSucceeded: Bool
    let sync: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if syncSucceeded {
                HStack {
                    Spacer()
                    Label("同步完成", systemImage: "checkmark.circle.fill")
                        .font(.caption.weight(.bold)).foregroundStyle(.green)
                        .contentTransition(.symbolEffect(.replace))
                }
            }
            Text(username).font(.title3.weight(.bold))
            Text("关键词和白名单会在此设备自动同步")
                .font(.caption).foregroundStyle(.secondary)
            Button(action: sync) {
                HStack(spacing: 8) {
                    Image(systemName: syncSucceeded ? "checkmark" : "arrow.triangle.2.circlepath")
                        .rotationEffect(.degrees(syncing ? 360 : 0))
                        .animation(syncing ? .linear(duration: 0.8).repeatForever(autoreverses: false) : .default, value: syncing)
                        .contentTransition(.symbolEffect(.replace))
                    Text(syncSucceeded ? "同步完成" : (syncing ? "正在同步…" : "立即同步"))
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(syncing)
        }
        .padding(16)
        .background(.blue.opacity(0.08), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
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
            Section {
                VStack(alignment: .leading, spacing: 0) {
                    Text("新增关键词")
                        .font(.headline)
                        .padding(.bottom, 14)
                    TextField("输入关键词", text: $input)
                        .font(.body)
                        .textFieldStyle(.plain)
                        .padding(.horizontal, 16)
                        .frame(height: 52)
                        .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                Text("保存后会立即重新扫描当前 X 页面")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .padding(.top, 10)
                    Button(action: saveKeywords) {
                        Text("保存并立即生效")
                            .font(.headline)
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity, minHeight: 52)
                            .background(.blue, in: RoundedRectangle(cornerRadius: 17, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(!canSave)
                    .opacity(canSave ? 1 : 0.45)
                    .padding(.top, 16)
                }
                .padding(18)
                .background(Color(uiColor: .systemBackground), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .stroke(Color(uiColor: .separator).opacity(0.28), lineWidth: 1)
                }
            }
            .listRowInsets(EdgeInsets(top: 12, leading: 0, bottom: 10, trailing: 0))
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
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
        .scrollContentBackground(.hidden)
        .background(Color(uiColor: .systemGroupedBackground))
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

    private var canSave: Bool {
        !input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !pendingPopular.isEmpty
    }

    private func saveKeywords() {
        let keyword = input.trimmingCharacters(in: .whitespacesAndNewlines)
        let additions = pendingPopular + (keyword.isEmpty ? [] : [keyword])
        guard !additions.isEmpty else { return }
        state.replaceKeywords(additions + state.keywords)
        saveStatus = "已保存，立即生效"
        input = ""
        pendingPopular = []
        newlyAdded = Set(additions)
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
        .task { await state.refreshGlobalBanTotal() }
        .refreshable { await state.refreshGlobalBanTotal() }
    }
}

private struct ContributionSummaryCard: View {
    let globalTotal: Int?
    let contribution: Int
    @Binding var selectedBadge: ContributionAchievement?

    private var achievement: ContributionAchievement? {
        ContributionAchievement.current(for: contribution)
    }

    private var nextAchievement: ContributionAchievement? {
        ContributionAchievement.next(after: contribution)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 0) {
                MetricValue(title: "全网累计 Ban", value: globalTotal.map { $0.formatted() } ?? "--", tint: .blue)
                Divider().frame(height: 42).padding(.horizontal, 16)
                MetricValue(title: "本机贡献", value: contribution.formatted(), tint: .red)
            }

            HStack(spacing: 16) {
                AchievementBadgeArtwork(level: achievement?.level ?? 1, unlocked: achievement != nil)
                    .frame(width: 92, height: 92)
                    .shadow(color: .blue.opacity(0.20), radius: 12, y: 5)
                VStack(alignment: .leading, spacing: 5) {
                    Text("当前成就").font(.caption.weight(.medium)).foregroundStyle(.secondary)
                    Text(achievement.map { "Lv.\($0.level) \($0.title)" } ?? "首枚徽章待解锁")
                        .font(.title3.weight(.bold))
                    if let nextAchievement {
                        Text("再处理 \(max(nextAchievement.threshold - contribution, 0).formatted()) 条，解锁 Lv.\(nextAchievement.level) \(nextAchievement.title)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } else {
                        Text("已解锁全部 10 枚成就徽章")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 0)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(ContributionAchievement.all) { badge in
                        Button { selectedBadge = badge } label: {
                            VStack(spacing: 4) {
                                AchievementBadgeArtwork(level: badge.level, unlocked: contribution >= badge.threshold)
                                    .frame(width: 62, height: 62)
                                Text("Lv.\(badge.level)")
                                    .font(.caption2.weight(.bold))
                                    .foregroundStyle(contribution >= badge.threshold ? .blue : .secondary)
                            }
                            .frame(width: 74, height: 92)
                            .background(contribution >= badge.threshold ? Color.blue.opacity(0.10) : Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Lv.\(badge.level) \(badge.title)，需贡献 \(badge.threshold.formatted()) 条")
                    }
                }
            }
        }
        .padding(14)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

private struct AchievementOverlay: View {
    let badge: ContributionAchievement
    let contribution: Int
    let earnedAt: Date?
    let dismiss: () -> Void
    @State private var appeared = false

    private var unlocked: Bool { contribution >= badge.threshold }
    private var progress: Double { min(1, Double(contribution) / Double(badge.threshold)) }

    var body: some View {
        ZStack {
            Color.black.opacity(0.72).ignoresSafeArea()
            VStack(spacing: 18) {
                AchievementBadgeArtwork(level: badge.level, unlocked: unlocked)
                    .frame(width: 238, height: 238)
                    .shadow(color: .blue.opacity(unlocked ? 0.28 : 0.08), radius: 22, y: 10)
                VStack(spacing: 6) {
                    Text("Lv.\(badge.level)")
                        .font(.title2.weight(.heavy)).foregroundStyle(.blue)
                    Text(badge.title).font(.title3.weight(.bold))
                }
                VStack(spacing: 8) {
                    Text("达成条件：累计贡献 \(badge.threshold.formatted()) 条")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.82))
                    HStack {
                        Text("贡献进度")
                        Spacer()
                        Text("\(contribution.formatted()) / \(badge.threshold.formatted())")
                            .fontWeight(.bold)
                    }
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.78))
                    ProgressView(value: progress)
                        .tint(unlocked ? Color(red: 0.95, green: 0.72, blue: 0.16) : .blue)
                        .scaleEffect(y: 1.7)
                        .padding(.vertical, 5)
                }
                .frame(width: 260)
                if unlocked, let earnedAt {
                    Text("你在 \(earnedAt.formatted(date: .long, time: .shortened)) 获得此成就")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.65))
                }
            }
            .foregroundStyle(.white)
            .scaleEffect(appeared ? 1 : 0.72)
            .opacity(appeared ? 1 : 0)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea()
        .contentShape(Rectangle())
        .onTapGesture(perform: dismiss)
        .onAppear { withAnimation(.spring(response: 0.48, dampingFraction: 0.68)) { appeared = true } }
    }
}

private struct AchievementBadgeArtwork: View {
    let level: Int
    let unlocked: Bool

    var body: some View {
        Image(String(format: "ContributionBadge%02d", level))
            .resizable()
            .scaledToFit()
            .padding(2)
            .saturation(unlocked ? 1 : 0)
            .opacity(unlocked ? 1 : 0.32)
            .overlay {
                if !unlocked {
                    Image(systemName: "lock.fill")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.white)
                        .padding(4)
                        .background(.black.opacity(0.35), in: Circle())
                }
            }
    }
}

private struct MetricValue: View {
    let title: String
    let value: String
    let tint: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.title3.weight(.bold)).foregroundStyle(tint)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
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

            VStack(alignment: .leading, spacing: 5) {
                if !item.matchedKeywords.isEmpty {
                    Label("关键词：\(item.matchedKeywords.joined(separator: "、"))", systemImage: "text.magnifyingglass")
                        .foregroundStyle(.blue)
                }
                Label("规则依据：\(item.reason)", systemImage: "checkmark.seal")
                    .foregroundStyle(.secondary)
            }
            .font(.caption)
            .lineLimit(2)
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .background(.quaternary, in: RoundedRectangle(cornerRadius: 9, style: .continuous))

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
