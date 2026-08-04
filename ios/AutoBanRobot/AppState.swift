import Foundation
import SwiftUI
import UIKit
import Security

@MainActor
final class AppState: ObservableObject {
    @Published private(set) var keywords: [String]
    @Published private(set) var rules: [RemoteRule]
    @Published private(set) var history: [BanRecord]
    @Published private(set) var confirmedTotal: Int
    @Published private(set) var globalBanTotal: Int?
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
    private var heartbeatTask: Task<Void, Never>?
    private var banUploadInFlight = false
    private let api = XAPIClient()
    private let accountClient = AccountClient()
    private let defaults = UserDefaults.standard
    private let owner = "aagodofwealth"
    private let installationId: String
    private let deviceName: String
    private var pendingBanUploads: [BanEventUpload]
    private var ruleEngine: RuleEngineDescriptor?
    private var keywordSets: [KeywordSet]
    private var keywordPolicies: [KeywordPolicy]
    private var accountPolicies: [AccountPolicy]

    init() {
        let defaultOwner = "aagodofwealth"
        installationId = defaults.string(forKey: "ios_installation_id") ?? UUID().uuidString
        defaults.set(installationId, forKey: "ios_installation_id")
        deviceName = String(UIDevice.current.name.prefix(128))
        keywords = Self.load([String].self, key: "ios_keywords") ?? Self.bundledKeywords()
        let cachedRules = Self.load(RemoteRuleConfig.self, key: "ios_rules")
        let config: RemoteRuleConfig
        let shouldPersistBundledRules: Bool
        if let cachedRules, cachedRules.version >= 12, cachedRules.rules.allSatisfy({ rule in
            rule.matcher?.isEmpty == false || rule.pattern?.isEmpty == false || rule.condition != nil
        }) {
            config = cachedRules
            shouldPersistBundledRules = false
        } else {
            config = Self.bundledRules()
            shouldPersistBundledRules = true
        }
        rules = config.rules
        ruleEngine = config.engine
        keywordSets = config.keywordSets ?? []
        keywordPolicies = config.keywordPolicies ?? []
        accountPolicies = config.accountPolicies ?? []
        history = Self.load([BanRecord].self, key: "ios_history") ?? []
        queue = Self.load([BlockJob].self, key: "ios_queue") ?? []
        pendingBanUploads = Self.load([BanEventUpload].self, key: "ios_ban_upload_queue") ?? []
        let shouldResetUnsafeQueue = !defaults.bool(forKey: "ios_queue_safety_reset_v2")
        if shouldResetUnsafeQueue {
            queue = []
        }
        confirmedTotal = defaults.integer(forKey: "ios_confirmed_total")
        globalBanTotal = defaults.object(forKey: "ios_global_ban_total") as? Int
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
        persistAchievementDatesIfNeeded()
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
        syncLocalAccountSettings()
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
        syncLocalAccountSettings()
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
        syncLocalAccountSettings()
    }

    func replaceWhitelist(_ values: [String]) {
        whitelist = Set(values.map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() })
        whitelist.insert(owner)
        persist(Array(whitelist).sorted(), key: "ios_whitelist")
        configurationChanged?()
        syncLocalAccountSettings()
        syncLocalAccountSettings()
    }

    func accountInstallationId() -> String { installationId }

    func achievementEarnedAt(threshold: Int) -> Date? { defaults.object(forKey: "ios_achievement_\(threshold)_earned_at") as? Date }

    private func persistAchievementDatesIfNeeded() {
        let fallback = history.last?.blockedAt ?? Date()
        for threshold in [10, 30, 100, 300, 1_000, 3_000, 10_000, 30_000, 100_000, 300_000] where confirmedTotal >= threshold {
            let key = "ios_achievement_\(threshold)_earned_at"
            if defaults.object(forKey: key) == nil { defaults.set(fallback, forKey: key) }
        }
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
        syncLocalAccountSettings()
    }

    func applyCloudSettings(keywords: [String], whitelist: [String]) {
        var seen = Set<String>()
        self.keywords = keywords.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && seen.insert($0).inserted }
            .prefix(1_000).map { $0 }
        self.whitelist = Set(whitelist.map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() })
        self.whitelist.insert(owner)
        persist(self.keywords, key: "ios_keywords")
        persist(Array(self.whitelist).sorted(), key: "ios_whitelist")
        configurationChanged?()
    }

    private func syncLocalAccountSettings() {
        guard AccountClient.currentToken() != nil else { return }
        Task { [weak self] in
            guard let self else { return }
            try? await self.accountClient.push(state: self)
        }
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
        ruleStates[rule.id] ?? (rule.enabled != false)
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
            ruleEngine = config.engine
            keywordSets = config.keywordSets ?? []
            keywordPolicies = config.keywordPolicies ?? []
            accountPolicies = config.accountPolicies ?? []
            persist(config, key: "ios_rules")
            statusText = "规则已更新"
            configurationChanged?()
        } catch {
            statusText = "规则更新失败"
        }
    }

    func refreshGlobalBanTotal() async {
        guard let url = URL(string: "https://ban.richccy.com/api/bans/stats") else { return }
        var request = URLRequest(url: url)
        request.timeoutInterval = 8
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.setValue("application/json", forHTTPHeaderField: "accept")
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse,
                  (200..<300).contains(httpResponse.statusCode),
                  let stats = try? JSONDecoder().decode(BanStats.self, from: data) else {
                return
            }
            let total = max(stats.total, 0)
            globalBanTotal = total
            defaults.set(total, forKey: "ios_global_ban_total")
        } catch {
            // Keep the last successfully received total visible while offline.
        }
    }

    func startAppHeartbeat() {
        guard heartbeatTask == nil else { return }
        Task { [weak self] in await self?.flushBanUploads() }
        accountClient.startSettingsStream(state: self)
        heartbeatTask = Task { [weak self] in
            while !Task.isCancelled {
                await self?.sendAppHeartbeat()
                if let self { try? await self.accountClient.pull(state: self) }
                try? await Task.sleep(for: .seconds(30))
            }
        }
    }

    func stopAppHeartbeat() {
        heartbeatTask?.cancel()
        heartbeatTask = nil
        accountClient.stopSettingsStream()
    }

    func setBrowserLoadError(_ message: String) {
        statusText = "X 页面加载失败：\(message)"
    }

    func injectedConfigurationScript() -> String {
        let keywordJSON = String(data: (try? JSONEncoder().encode(keywords)) ?? Data("[]".utf8), encoding: .utf8) ?? "[]"
        let rulesConfig = RemoteRuleConfig(
            version: 12,
            updatedAt: nil,
            engine: ruleEngine,
            keywordSets: keywordSets,
            keywordPolicies: keywordPolicies,
            accountPolicies: accountPolicies,
            rules: rules
        )
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
                persistAchievementDatesIfNeeded()
                persist(history, key: "ios_history")
                enqueueBanUpload(BanEventUpload(
                    job: job,
                    clientEventId: record.id.uuidString,
                    blockedAt: record.blockedAt
                ))
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

    private func sendAppHeartbeat() async {
        guard let url = URL(string: "https://ban.richccy.com/api/clients/heartbeat") else { return }
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown"
        let payload = AppHeartbeat(
            installationId: installationId,
            platform: "ios-webkit",
            version: version,
            clientType: "app",
            deviceName: deviceName
        )
        guard let body = try? JSONEncoder().encode(payload) else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 8
        request.setValue("application/json", forHTTPHeaderField: "content-type")
        request.setValue("ios-webkit", forHTTPHeaderField: "x-autoban-client")
        if let token = AccountClient.currentToken() { request.setValue("Bearer \(token)", forHTTPHeaderField: "authorization") }
        request.httpBody = body
        _ = try? await URLSession.shared.data(for: request)
    }

    private func enqueueBanUpload(_ event: BanEventUpload) {
        guard !pendingBanUploads.contains(where: { $0.clientEventId == event.clientEventId }) else { return }
        pendingBanUploads.append(event)
        persist(pendingBanUploads, key: "ios_ban_upload_queue")
        Task { [weak self] in await self?.flushBanUploads() }
    }

    private func flushBanUploads() async {
        guard !banUploadInFlight else { return }
        banUploadInFlight = true
        defer { banUploadInFlight = false }
        while let event = pendingBanUploads.first {
            guard await uploadBanEvent(event) else { return }
            pendingBanUploads.removeFirst()
            persist(pendingBanUploads, key: "ios_ban_upload_queue")
        }
    }

    private func uploadBanEvent(_ event: BanEventUpload) async -> Bool {
        guard let url = URL(string: "https://ban.richccy.com/api/bans") else { return false }
        let payload: [String: Any] = [
            "clientEventId": event.clientEventId,
            "installationId": installationId,
            "username": event.username,
            "displayName": event.displayName,
            "reason": event.reason,
            "matchedKeywords": event.matchedKeywords,
            "configuredKeywords": event.configuredKeywords,
            "content": event.content,
            "pageUrl": event.pageURL,
            "blockedAt": event.blockedAt,
            "clientType": "app"
        ]
        guard JSONSerialization.isValidJSONObject(payload),
              let body = try? JSONSerialization.data(withJSONObject: payload) else { return false }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 8
        request.setValue("application/json", forHTTPHeaderField: "content-type")
        request.setValue("ios-webkit", forHTTPHeaderField: "x-autoban-client")
        if let token = AccountClient.currentToken() { request.setValue("Bearer \(token)", forHTTPHeaderField: "authorization") }
        request.httpBody = body
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            return (response as? HTTPURLResponse).map { (200..<300).contains($0.statusCode) } ?? false
        } catch {
            return false
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
            return RemoteRuleConfig(
                version: 12,
                updatedAt: nil,
                engine: RuleEngineDescriptor(schemaVersion: 1),
                keywordSets: [],
                keywordPolicies: [],
                accountPolicies: [],
                rules: []
            )
        }
        return config
    }
}

struct AutoBanAccountSession: Codable { let accessToken: String; let username: String; let expiresAt: String }

@MainActor
final class AccountClient: ObservableObject {
    @Published private(set) var session: AutoBanAccountSession?
    private let base = URL(string: "https://ban.richccy.com/api")!
    private var settingsStreamTask: Task<Void, Never>?
    init() { session = Self.load() }
    func logout() { if let session { Task { try? await request("auth/logout", method: "POST", token: session.accessToken) } }; Self.clear(); session = nil }
    func authenticate(mode: String, payload: [String: Any], state: AppState) async throws {
        let response = try await request("auth/\(mode)", method: "POST", body: payload)
        let next = try JSONDecoder().decode(AutoBanAccountSession.self, from: response)
        Self.save(next); session = next; try await bindAndMerge(state: state, merge: true)
    }
    func recoveryQuestion(username: String) async throws -> String {
        let data = try await request("auth/recovery/question", method: "POST", body: ["username": username])
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
        return json["securityQuestionKey"] as? String ?? ""
    }
    func bindAndMerge(state: AppState, merge: Bool = false) async throws {
        guard let session else { throw NSError(domain: "AUTH_REQUIRED", code: 401) }
        _ = try await request("auth/devices/bind", method: "POST", body: ["installationId": state.accountInstallationId()], token: session.accessToken)
        let data = try await request("account/settings\(merge ? "/merge" : "")", method: merge ? "POST" : "PUT", body: ["keywords": state.keywords, "whitelist": Array(state.whitelist)], token: session.accessToken)
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
        state.applyCloudSettings(keywords: json["keywords"] as? [String] ?? [], whitelist: json["whitelist"] as? [String] ?? [])
    }
    func push(state: AppState) async throws { try await bindAndMerge(state: state, merge: false) }
    func pull(state: AppState) async throws {
        guard let session else { return }
        let data = try await request("account/settings", method: "GET", token: session.accessToken)
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
        state.applyCloudSettings(keywords: json["keywords"] as? [String] ?? [], whitelist: json["whitelist"] as? [String] ?? [])
    }
    func startSettingsStream(state: AppState) {
        guard settingsStreamTask == nil, let session else { return }
        settingsStreamTask = Task { [weak self, weak state] in
            defer { self?.settingsStreamTask = nil }
            guard let self, let state else { return }
            while !Task.isCancelled {
                do {
                    var request = URLRequest(url: base.appending(path: "account/settings/stream"))
                    request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "authorization")
                    request.timeoutInterval = 0
                    let (bytes, response) = try await URLSession.shared.bytes(for: request)
                    guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { return }
                    for try await line in bytes.lines where !Task.isCancelled {
                        guard line.hasPrefix("data:"), let data = line.dropFirst(5).trimmingCharacters(in: .whitespaces).data(using: .utf8),
                              let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { continue }
                        state.applyCloudSettings(keywords: json["keywords"] as? [String] ?? [], whitelist: json["whitelist"] as? [String] ?? [])
                    }
                } catch {
                    if !Task.isCancelled { try? await Task.sleep(for: .seconds(2)) }
                }
            }
        }
    }
    func stopSettingsStream() { settingsStreamTask?.cancel(); settingsStreamTask = nil }
    private func request(_ path: String, method: String, body: [String: Any]? = nil, token: String? = nil) async throws -> Data {
        var request = URLRequest(url: base.appending(path: path)); request.httpMethod = method; request.timeoutInterval = 8; request.setValue("application/json", forHTTPHeaderField: "content-type"); if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "authorization") }; if let body { request.httpBody = try JSONSerialization.data(withJSONObject: body) }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { let code = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["code"] as? String ?? "AUTH_FAILED"; throw NSError(domain: code, code: 1) }; return data
    }
    private static func save(_ session: AutoBanAccountSession) { let data = try? JSONEncoder().encode(session); clear(); SecItemAdd([kSecClass: kSecClassGenericPassword, kSecAttrService: "com.autobanrobot.ios.account", kSecAttrAccount: "session", kSecValueData: data as Any] as CFDictionary, nil) }
    private static func load() -> AutoBanAccountSession? { var result: CFTypeRef?; let status = SecItemCopyMatching([kSecClass: kSecClassGenericPassword, kSecAttrService: "com.autobanrobot.ios.account", kSecAttrAccount: "session", kSecReturnData: true] as CFDictionary, &result); return status == errSecSuccess ? (result as? Data).flatMap { try? JSONDecoder().decode(AutoBanAccountSession.self, from: $0) } : nil }
    private static func clear() { SecItemDelete([kSecClass: kSecClassGenericPassword, kSecAttrService: "com.autobanrobot.ios.account", kSecAttrAccount: "session"] as CFDictionary) }
    static func currentToken() -> String? { load()?.accessToken }
}
