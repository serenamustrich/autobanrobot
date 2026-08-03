import Foundation

struct BlockJob: Codable, Hashable, Identifiable {
    let id: UUID
    let username: String
    let displayName: String
    let reason: String
    let matchedKeywords: [String]
    let configuredKeywords: [String]
    let content: String
    let pageURL: String
    let hostname: String
    let csrf: String
    var attempts: Int

    init?(bridgePayload: String) {
        guard let data = bridgePayload.data(using: .utf8),
              let raw = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let username = raw["username"] as? String,
              Self.isValidUsername(username) else {
            return nil
        }
        self.id = UUID()
        self.username = username.trimmingCharacters(in: .whitespacesAndNewlines)
        self.displayName = (raw["displayName"] as? String ?? "").prefix(160).description
        self.reason = (raw["reason"] as? String ?? "规则命中").prefix(500).description
        self.matchedKeywords = Array((raw["matchedKeywords"] as? [String] ?? []).prefix(30))
        self.configuredKeywords = Array((raw["configuredKeywords"] as? [String] ?? []).prefix(1_000))
        self.content = (raw["content"] as? String ?? "").prefix(1_000).description
        self.pageURL = (raw["pageUrl"] as? String ?? "").prefix(1_000).description
        self.hostname = raw["hostname"] as? String == "twitter.com" ? "twitter.com" : "x.com"
        self.csrf = (raw["csrf"] as? String ?? "").prefix(300).description
        self.attempts = 0
    }

    init(record: BanRecord) {
        id = UUID()
        username = record.username
        displayName = record.displayName
        reason = record.reason
        matchedKeywords = []
        configuredKeywords = []
        content = record.content
        pageURL = ""
        hostname = record.hostname ?? "x.com"
        csrf = record.csrf ?? ""
        attempts = 0
    }

    static func isValidUsername(_ value: String) -> Bool {
        value.range(of: "^[A-Za-z0-9_]{1,15}$", options: .regularExpression) != nil
    }
}

struct BanRecord: Codable, Identifiable, Hashable {
    let id: UUID
    let username: String
    let displayName: String
    let reason: String
    let content: String
    let blockedAt: Date
    var unblockedAt: Date?
    var hidden: Bool
    let hostname: String?
    let csrf: String?

    init(job: BlockJob) {
        id = UUID()
        username = job.username
        displayName = job.displayName
        reason = job.reason
        content = job.content
        blockedAt = Date()
        unblockedAt = nil
        hidden = true
        hostname = job.hostname
        csrf = job.csrf
    }
}

enum BlockOutcome: Equatable {
    case success(String)
    case skipped(String)
    case retry(String)
    case failed(String)

    var statusText: String {
        switch self {
        case let .success(message), let .skipped(message), let .retry(message), let .failed(message): message
        }
    }

    var shouldRetry: Bool {
        if case .retry = self { return true }
        return false
    }
}

struct RemoteRuleConfig: Codable {
    let version: Int
    let updatedAt: String?
    let rules: [RemoteRule]
}

struct RemoteRule: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let description: String
    let enabled: Bool?
    let scope: String?
    let matcher: String?
    let normalization: String?
    let pattern: String?
    let flags: String?
    let requiresDefaultAvatar: Bool?
}

struct XSession {
    var bearer: String = ""
    var csrf: String = ""
    var viewerUsername: String?

    var isReady: Bool { !bearer.isEmpty && !csrf.isEmpty }
}
