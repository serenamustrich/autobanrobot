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
        matchedKeywords = record.matchedKeywords
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
    let matchedKeywords: [String]
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
        matchedKeywords = job.matchedKeywords
        content = job.content
        blockedAt = Date()
        unblockedAt = nil
        hidden = true
        hostname = job.hostname
        csrf = job.csrf
    }

    private enum CodingKeys: String, CodingKey {
        case id, username, displayName, reason, matchedKeywords, content, blockedAt, unblockedAt, hidden, hostname, csrf
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        username = try container.decode(String.self, forKey: .username)
        displayName = try container.decode(String.self, forKey: .displayName)
        reason = try container.decode(String.self, forKey: .reason)
        matchedKeywords = try container.decodeIfPresent([String].self, forKey: .matchedKeywords) ?? []
        content = try container.decode(String.self, forKey: .content)
        blockedAt = try container.decode(Date.self, forKey: .blockedAt)
        unblockedAt = try container.decodeIfPresent(Date.self, forKey: .unblockedAt)
        hidden = try container.decodeIfPresent(Bool.self, forKey: .hidden) ?? true
        hostname = try container.decodeIfPresent(String.self, forKey: .hostname)
        csrf = try container.decodeIfPresent(String.self, forKey: .csrf)
    }
}

struct BanStats: Decodable {
    let total: Int
}

struct AppHeartbeat: Encodable {
    let installationId: String
    let platform: String
    let version: String
    let clientType: String
    let deviceName: String
}

struct BanEventUpload: Codable, Hashable {
    let clientEventId: String
    let username: String
    let displayName: String
    let reason: String
    let matchedKeywords: [String]
    let configuredKeywords: [String]
    let content: String
    let pageURL: String
    let blockedAt: String

    init(job: BlockJob, clientEventId: String, blockedAt: Date) {
        self.clientEventId = clientEventId
        self.username = job.username
        self.displayName = job.displayName
        self.reason = job.reason
        self.matchedKeywords = job.matchedKeywords
        self.configuredKeywords = job.configuredKeywords
        self.content = job.content
        self.pageURL = job.pageURL
        self.blockedAt = ISO8601DateFormatter().string(from: blockedAt)
    }
}

struct ContributionAchievement: Identifiable, Hashable {
    let level: Int
    let threshold: Int
    let title: String
    let symbol: String

    var id: Int { level }

    static let all: [ContributionAchievement] = [
        .init(level: 1, threshold: 10, title: "侦察员", symbol: "scope"),
        .init(level: 2, threshold: 30, title: "清道夫", symbol: "broom"),
        .init(level: 3, threshold: 100, title: "猎手", symbol: "target"),
        .init(level: 4, threshold: 300, title: "先锋", symbol: "flag.checkered"),
        .init(level: 5, threshold: 1_000, title: "守望者", symbol: "shield.lefthalf.filled"),
        .init(level: 6, threshold: 3_000, title: "净域使", symbol: "sparkles"),
        .init(level: 7, threshold: 10_000, title: "万级猎人", symbol: "bolt.shield"),
        .init(level: 8, threshold: 30_000, title: "破障者", symbol: "flame"),
        .init(level: 9, threshold: 100_000, title: "裁决官", symbol: "crown"),
        .init(level: 10, threshold: 300_000, title: "终局守护", symbol: "medal.star")
    ]

    static func current(for contribution: Int) -> ContributionAchievement? {
        all.last { contribution >= $0.threshold }
    }

    static func next(after contribution: Int) -> ContributionAchievement? {
        all.first { contribution < $0.threshold }
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
    let engine: RuleEngineDescriptor?
    let keywordSets: [KeywordSet]?
    let keywordPolicies: [KeywordPolicy]?
    let accountPolicies: [AccountPolicy]?
    let rules: [RemoteRule]
}

struct RuleEngineDescriptor: Codable, Hashable {
    let schemaVersion: Int
}

struct KeywordSet: Codable, Hashable {
    let id: String
    let enabled: Bool?
    let keywords: [String]
}

struct KeywordPolicy: Codable, Hashable {
    let id: String
    let scopes: [String]
    let `operator`: String
    let normalization: String
    let minLength: Int
    let keywordPattern: String?
    let keywordFlags: String?
    let flags: String?
}

struct AccountPolicy: Codable, Hashable {
    let id: String
    let keywordPattern: String
    let keywordFlags: String
    let targets: [AccountPolicyTarget]
}

struct AccountPolicyTarget: Codable, Hashable {
    let scope: String
    let pattern: String
    let flags: String
    let normalization: String?
}

indirect enum JSONValue: Codable, Hashable {
    case string(String)
    case number(Double)
    case bool(Bool)
    case object([String: JSONValue])
    case array([JSONValue])
    case null

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() { self = .null }
        else if let value = try? container.decode(Bool.self) { self = .bool(value) }
        else if let value = try? container.decode(Double.self) { self = .number(value) }
        else if let value = try? container.decode(String.self) { self = .string(value) }
        else if let value = try? container.decode([String: JSONValue].self) { self = .object(value) }
        else { self = .array(try container.decode([JSONValue].self)) }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case let .string(value): try container.encode(value)
        case let .number(value): try container.encode(value)
        case let .bool(value): try container.encode(value)
        case let .object(value): try container.encode(value)
        case let .array(value): try container.encode(value)
        case .null: try container.encodeNil()
        }
    }
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
    let condition: JSONValue?
}

struct XSession {
    var bearer: String = ""
    var csrf: String = ""
    var viewerUsername: String?

    var isReady: Bool { !bearer.isEmpty && !csrf.isEmpty }
}
