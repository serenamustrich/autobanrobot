import Foundation
import SwiftUI

@MainActor
final class AppState: ObservableObject {
    @Published private(set) var keywords: [String]
    @Published private(set) var rules: [RemoteRule]
    @Published private(set) var history: [BanRecord]
    @Published private(set) var confirmedTotal: Int
    @Published private(set) var statusText = "等待 X 页面加载"
    @Published private(set) var pendingCount = 0
    @Published private(set) var whitelist: Set<String>
    @Published private(set) var autoBanEnabled: Bool
    @Published private(set) var ruleStates: [String: Bool]

    var configurationChanged: (() -> Void)?
    var cookieProvider: ((String) async -> String?)?
    var pageScriptExecutor: ((String) -> Void)?

    private var session = XSession()
    @Published private(set) var queue: [BlockJob]
    private var isProcessing = false
    private var viewerLookupInFlight = false
    private let api = XAPIClient()
    private let defaults = UserDefaults.standard
    private let owner = "aagodofwealth"

    init() {
        let defaultOwner = "aagodofwealth"
        keywords = Self.load([String].self, key: "ios_keywords") ?? Self.bundledKeywords()
        let cachedRules = Self.load(RemoteRuleConfig.self, key: "ios_rules")
        let config: RemoteRuleConfig
        let shouldPersistBundledRules: Bool
        if let cachedRules, cachedRules.rules.allSatisfy({ rule in
            rule.matcher?.isEmpty == false || rule.pattern?.isEmpty == false
        }) {
            config = cachedRules
            shouldPersistBundledRules = false
        } else {
            config = Self.bundledRules()
            shouldPersistBundledRules = true
        }
        rules = config.rules
        history = Self.load([BanRecord].self, key: "ios_history") ?? []
        queue = Self.load([BlockJob].self, key: "ios_queue") ?? []
        let shouldResetUnsafeQueue = !defaults.bool(forKey: "ios_queue_safety_reset_v2")
        if shouldResetUnsafeQueue {
            queue = []
        }
        confirmedTotal = defaults.integer(forKey: "ios_confirmed_total")
        whitelist = Set(Self.load([String].self, key: "ios_whitelist") ?? [defaultOwner])
        autoBanEnabled = defaults.object(forKey: "ios_auto_ban_enabled") as? Bool ?? true
        ruleStates = Self.load([String: Bool].self, key: "ios_rule_states") ?? [:]
        pendingCount = queue.count
        whitelist.insert(defaultOwner)
        if shouldPersistBundledRules { persist(config, key: "ios_rules") }
        if shouldResetUnsafeQueue {
            defaults.set(true, forKey: "ios_queue_safety_reset_v2")
            persist(queue, key: "ios_queue")
        }
    }

    func updateAuth(bearer: String, csrf: String) {
        if !bearer.isEmpty { session.bearer = bearer }
        if !csrf.isEmpty { session.csrf = csrf }
        print("[AutoBanRobot] X session capture bearer=\(!session.bearer.isEmpty) csrf=\(!session.csrf.isEmpty)")
        if session.isReady {
            statusText = "已获取 X 登录会话"
            resolveViewerUsernameIfNeeded()
        }
        processSoon()
    }

    func updateViewerUsername(_ value: String) {
        let username = value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard BlockJob.isValidUsername(username) else { return }
        session.viewerUsername = username
        whitelist.insert(username)
        persist(Array(whitelist).sorted(), key: "ios_whitelist")
        configurationChanged?()
    }

    func reportScanDiagnostic(_ value: String) {
        guard !value.isEmpty else { return }
        print("[AutoBanRobot] Scan diagnostic: \(value)")
    }

    func enqueue(payload: String) {
        guard let job = BlockJob(bridgePayload: payload) else { return }
        let normalized = job.username.lowercased()
        if !job.csrf.isEmpty { session.csrf = job.csrf }
        guard autoBanEnabled else {
            publishResult(for: job, state: "skipped", message: "自动处理已暂停")
            return
        }
        guard !whitelist.contains(normalized) else {
            publishResult(for: job, state: "skipped", message: "白名单账号")
            return
        }
        guard !queue.contains(where: { $0.username.caseInsensitiveCompare(job.username) == .orderedSame }) else { return }
        queue.append(job)
        persistQueue()
        pendingCount = queue.count
        statusText = "已处理队列 @\(job.username)"
        processSoon()
    }

    func addKeyword(_ value: String) {
        let keyword = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !keyword.isEmpty, !keywords.contains(keyword) else { return }
        keywords.append(keyword)
        persist(keywords, key: "ios_keywords")
        statusText = "关键词已保存"
        configurationChanged?()
    }

    func replaceKeywords(_ values: [String]) {
        var seen = Set<String>()
        keywords = values
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && seen.insert($0).inserted }
            .prefix(1_000)
            .map { $0 }
        persist(keywords, key: "ios_keywords")
        statusText = "关键词已保存，立即生效"
        configurationChanged?()
    }

    func loadPopularKeywords() async throws -> [String] {
        guard let url = URL(string: "https://ban.richccy.com/api/popular-terms") else { return [] }
        var request = URLRequest(url: url)
        request.timeoutInterval = 8
        request.setValue("application/json", forHTTPHeaderField: "accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse,
              (200..<300).contains(httpResponse.statusCode) else {
            throw URLError(.badServerResponse)
        }
        guard let raw = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            throw URLError(.cannotParseResponse)
        }
        var seen = Set<String>()
        return raw.compactMap { $0["term"] as? String }
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && seen.insert($0).inserted }
    }

    func removeKeyword(_ value: String) {
        keywords.removeAll { $0 == value }
        persist(keywords, key: "ios_keywords")
        configurationChanged?()
    }

    func addWhitelist(_ value: String) {
        let username = value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard BlockJob.isValidUsername(username) else { return }
        whitelist.insert(username)
        persist(Array(whitelist).sorted(), key: "ios_whitelist")
        configurationChanged?()
    }

    func removeWhitelist(_ value: String) {
        let username = value.lowercased()
        guard username != owner else { return }
        whitelist.remove(username)
        persist(Array(whitelist).sorted(), key: "ios_whitelist")
        configurationChanged?()
    }

    func displayUsername(_ value: String) -> String {
        value.caseInsensitiveCompare(owner) == .orderedSame ? "AAAGodofWealth" : value
    }

    func setAutoBanEnabled(_ enabled: Bool) {
        autoBanEnabled = enabled
        defaults.set(enabled, forKey: "ios_auto_ban_enabled")
        configurationChanged?()
        if enabled { processSoon() }
    }

    func isRuleEnabled(_ rule: RemoteRule) -> Bool {
        rule.enabled != false && ruleStates[rule.id] != false
    }

    func setRuleEnabled(_ rule: RemoteRule, enabled: Bool) {
        ruleStates[rule.id] = enabled
        persist(ruleStates, key: "ios_rule_states")
        configurationChanged?()
    }

    func addWhitelistAndUnblock(_ record: BanRecord) {
        addWhitelist(record.username)
        unblock(record)
    }

    func unblock(_ record: BanRecord) {
        Task {
            let cookie = await cookieProvider?(record.hostname ?? "x.com")
            let outcome = await api.unblock(record: record, session: session, cookie: cookie)
            statusText = outcome.statusText
            guard case .success = outcome,
                  let index = history.firstIndex(where: { $0.id == record.id }) else { return }
            history[index].unblockedAt = Date()
            history[index].hidden = false
            persist(history, key: "ios_history")
            publishResult(for: BlockJob(record: record), state: "skipped", message: "已取消屏蔽和隐藏")
        }
    }

    func reblock(_ record: BanRecord) {
        let job = BlockJob(record: record)
        queue.removeAll { $0.username.caseInsensitiveCompare(job.username) == .orderedSame }
        queue.insert(job, at: 0)
        persistQueue()
        pendingCount = queue.count
        processSoon()
    }

    func removeQueued(at offsets: IndexSet) {
        queue.remove(atOffsets: offsets)
        persistQueue()
        pendingCount = queue.count
    }

    func refreshRules() async {
        guard let url = URL(string: "https://ban.richccy.com/api/rules") else { return }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard (response as? HTTPURLResponse)?.statusCode == 200,
                  let config = try? JSONDecoder().decode(RemoteRuleConfig.self, from: data) else {
                statusText = "规则更新失败"
                return
            }
            rules = config.rules
            persist(config, key: "ios_rules")
            statusText = "规则已更新"
            configurationChanged?()
        } catch {
            statusText = "规则更新失败"
        }
    }

    func setBrowserLoadError(_ message: String) {
        statusText = "X 页面加载失败：\(message)"
    }

    func injectedConfigurationScript() -> String {
        let keywordJSON = String(data: (try? JSONEncoder().encode(keywords)) ?? Data("[]".utf8), encoding: .utf8) ?? "[]"
        let rulesConfig = RemoteRuleConfig(version: 11, updatedAt: nil, rules: rules)
        let ruleJSON = String(data: (try? JSONEncoder().encode(rulesConfig)) ?? Data("{}".utf8), encoding: .utf8) ?? "{}"
        let whitelistJSON = String(data: (try? JSONEncoder().encode(Array(whitelist).sorted())) ?? Data("[]".utf8), encoding: .utf8) ?? "[]"
        let stateJSON = String(data: (try? JSONEncoder().encode(ruleStates)) ?? Data("{}".utf8), encoding: .utf8) ?? "{}"
        return """
        window.dispatchEvent(new CustomEvent('__twblocker_keywords__', { detail: { kws: \(keywordJSON) } }));
        window.dispatchEvent(new CustomEvent('__twblocker_rules__', { detail: { config: \(ruleJSON), states: \(stateJSON) } }));
        window.dispatchEvent(new CustomEvent('__twblocker_whitelist__', { detail: { accounts: \(whitelistJSON) } }));
        """
    }

    private func processSoon() {
        guard !isProcessing else { return }
        Task { await drainQueue() }
    }

    private func drainQueue() async {
        guard !isProcessing else { return }
        isProcessing = true
        defer { isProcessing = false }
        while !queue.isEmpty {
            guard session.isReady else {
                statusText = "等待 X 登录会话"
                return
            }
            var job = queue.removeFirst()
            persistQueue()
            pendingCount = queue.count
            if whitelist.contains(job.username.lowercased()) { continue }
            statusText = "正在处理 @\(job.username)"
            let cookie = await cookieProvider?(job.hostname)
            let outcome = await api.block(job: job, session: session, cookie: cookie)
            switch outcome {
            case .success:
                let record = BanRecord(job: job)
                history.removeAll { $0.username.caseInsensitiveCompare(job.username) == .orderedSame }
                history.insert(record, at: 0)
                history = Array(history.prefix(1_000))
                confirmedTotal += 1
                defaults.set(confirmedTotal, forKey: "ios_confirmed_total")
                persist(history, key: "ios_history")
                statusText = outcome.statusText
            case .skipped:
                statusText = outcome.statusText
            case .retry:
                job.attempts += 1
                if job.attempts < 3 {
                    queue.append(job)
                    persistQueue()
                    pendingCount = queue.count
                }
                statusText = outcome.statusText
            case .failed:
                statusText = outcome.statusText
            }
            publishResult(for: job, outcome: outcome)
            try? await Task.sleep(for: .milliseconds(500))
        }
    }

    private func publishResult(for job: BlockJob, outcome: BlockOutcome) {
        switch outcome {
        case .success:
            publishResult(for: job, state: "success", message: "")
        case .skipped(let message):
            publishResult(for: job, state: "skipped", message: message)
        case .retry(let message), .failed(let message):
            publishResult(for: job, state: "failed", message: message)
        }
    }

    private func publishResult(for job: BlockJob, state: String, message: String) {
        let payload: [String: Any] = [
            "username": job.username,
            "state": state,
            "message": message,
            "pageKey": job.pageURL.components(separatedBy: "x.com").last?.components(separatedBy: "?").first ?? "",
            "historical": false
        ]
        guard JSONSerialization.isValidJSONObject(payload),
              let data = try? JSONSerialization.data(withJSONObject: payload),
              let json = String(data: data, encoding: .utf8) else { return }
        pageScriptExecutor?("window.dispatchEvent(new CustomEvent('__twblocker_block_result__', { detail: \(json) }));")
    }

    private func persistQueue() { persist(queue, key: "ios_queue") }

    private func resolveViewerUsernameIfNeeded() {
        guard !viewerLookupInFlight, session.viewerUsername == nil else { return }
        viewerLookupInFlight = true
        Task {
            let cookie = await cookieProvider?("x.com")
            let username = await api.currentUsername(session: session, cookie: cookie)
            viewerLookupInFlight = false
            if let username { updateViewerUsername(username) }
        }
    }
    private func persist<T: Encodable>(_ value: T, key: String) {
        defaults.set(try? JSONEncoder().encode(value), forKey: key)
    }
    private static func load<T: Decodable>(_ type: T.Type, key: String) -> T? {
        guard let data = UserDefaults.standard.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }
    private static func bundledKeywords() -> [String] {
        guard let url = Bundle.main.url(forResource: "default-keywords", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return [] }
        return (try? JSONDecoder().decode([String].self, from: data)) ?? []
    }
    private static func bundledRules() -> RemoteRuleConfig {
        guard let url = Bundle.main.url(forResource: "default-rules", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let config = try? JSONDecoder().decode(RemoteRuleConfig.self, from: data) else {
            return RemoteRuleConfig(version: 11, updatedAt: nil, rules: [])
        }
        return config
    }
}
