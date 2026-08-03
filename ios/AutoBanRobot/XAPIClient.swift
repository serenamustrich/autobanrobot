import Foundation

struct XAPIClient {
    private struct HTTPResult {
        let statusCode: Int
        let body: [String: Any]?
    }

    func block(job: BlockJob, session: XSession, cookie: String?) async -> BlockOutcome {
        guard job.username.caseInsensitiveCompare("AAAGodofWealth") != .orderedSame else {
            return .skipped("已跳过作者账号")
        }
        guard session.isReady else { return .retry("等待 X 登录会话") }

        let relationship = await request(
            host: job.hostname,
            path: "/i/api/1.1/friendships/show.json?target_screen_name=\(job.username)",
            method: "GET", session: session, cookie: cookie
        )
        guard let relationshipObject = relationship.body?["relationship"] as? [String: Any],
              let source = relationshipObject["source"] as? [String: Any] else {
            return retryable(relationship.statusCode, "无法确认关注关系")
        }
        guard let following = booleanValue(source, key: "following") else {
            return .retry("无法明确确认关注关系")
        }
        if following { return .skipped("已跳过关注账号") }

        if booleanValue(source, key: "muting") != true {
            let mute = await action(path: "/i/api/1.1/mutes/users/create.json", job: job, session: session, cookie: cookie)
            guard mute else { return .retry("隐藏请求未确认") }
        }
        if booleanValue(source, key: "blocking") != true {
            let block = await action(path: "/i/api/1.1/blocks/create.json", job: job, session: session, cookie: cookie)
            guard block else { return .retry("屏蔽请求未确认") }
        }

        let verification = await request(
            host: job.hostname,
            path: "/i/api/1.1/friendships/show.json?target_screen_name=\(job.username)",
            method: "GET", session: session, cookie: cookie
        )
        let verified = (verification.body?["relationship"] as? [String: Any])?["source"] as? [String: Any]
        if verification.statusCode == 200,
           let verified,
           booleanValue(verified, key: "blocking") == true,
           booleanValue(verified, key: "muting") == true {
            return .success("已处理 @\(job.username)")
        }
        return retryable(verification.statusCode, "X 未确认屏蔽和隐藏")
    }

    func unblock(record: BanRecord, session: XSession, cookie: String?) async -> BlockOutcome {
        guard session.isReady else { return .retry("等待 X 登录会话") }
        let host = record.hostname ?? "x.com"
        let relationship = await request(
            host: host,
            path: "/i/api/1.1/friendships/show.json?target_screen_name=\(record.username)",
            method: "GET", session: session, cookie: cookie
        )
        guard let source = (relationship.body?["relationship"] as? [String: Any])?["source"] as? [String: Any] else {
            return retryable(relationship.statusCode, "无法确认当前屏蔽状态")
        }
        if booleanValue(source, key: "muting") != false,
           await action(path: "/i/api/1.1/mutes/users/destroy.json", username: record.username, host: host, session: session, cookie: cookie) == false {
            return .retry("取消隐藏失败")
        }
        if booleanValue(source, key: "blocking") != false,
           await action(path: "/i/api/1.1/blocks/destroy.json", username: record.username, host: host, session: session, cookie: cookie) == false {
            return .retry("取消屏蔽失败")
        }
        let verification = await request(
            host: host,
            path: "/i/api/1.1/friendships/show.json?target_screen_name=\(record.username)",
            method: "GET", session: session, cookie: cookie
        )
        let verified = (verification.body?["relationship"] as? [String: Any])?["source"] as? [String: Any]
        if verification.statusCode == 200,
           let verified,
           booleanValue(verified, key: "blocking") != true,
           booleanValue(verified, key: "muting") != true {
            return .success("已取消屏蔽和隐藏 @\(record.username)")
        }
        return retryable(verification.statusCode, "X 未确认取消屏蔽和隐藏")
    }

    func currentUsername(session: XSession, cookie: String?) async -> String? {
        guard session.isReady else { return nil }
        let primary = await request(
            host: "api.x.com", path: "/1.1/account/verify_credentials.json?skip_status=true&include_entities=false",
            method: "GET", session: session, cookie: cookie
        )
        if primary.statusCode == 200,
           let username = primary.body?["screen_name"] as? String,
           BlockJob.isValidUsername(username) {
            return username
        }
        let fallback = await request(
            host: "x.com", path: "/i/api/1.1/account/settings.json", method: "GET", session: session, cookie: cookie
        )
        if fallback.statusCode == 200,
           let username = fallback.body?["screen_name"] as? String,
           BlockJob.isValidUsername(username) {
            return username
        }
        return nil
    }

    private func action(path: String, job: BlockJob, session: XSession, cookie: String?) async -> Bool {
        await action(path: path, username: job.username, host: job.hostname, session: session, cookie: cookie)
    }

    private func action(path: String, username: String, host: String, session: XSession, cookie: String?) async -> Bool {
        let result = await request(
            host: host, path: path, method: "POST", session: session, cookie: cookie,
            form: "screen_name=\(username.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? username)"
        )
        return (200..<300).contains(result.statusCode) && ((result.body?["errors"] as? [[String: Any]])?.isEmpty ?? true)
    }

    private func request(
        host: String,
        path: String,
        method: String,
        session: XSession,
        cookie: String?,
        form: String? = nil
    ) async -> HTTPResult {
        let normalizedHost: String
        switch host {
        case "twitter.com", "api.x.com": normalizedHost = host
        default: normalizedHost = "x.com"
        }
        guard let url = URL(string: "https://\(normalizedHost)\(path)") else { return HTTPResult(statusCode: 0, body: nil) }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 12
        request.setValue(session.bearer, forHTTPHeaderField: "authorization")
        request.setValue(session.csrf, forHTTPHeaderField: "x-csrf-token")
        request.setValue("yes", forHTTPHeaderField: "x-twitter-active-user")
        request.setValue("OAuth2Session", forHTTPHeaderField: "x-twitter-auth-type")
        request.setValue("application/json, text/plain, */*", forHTTPHeaderField: "accept")
        request.setValue("https://\(normalizedHost)", forHTTPHeaderField: "origin")
        request.setValue("https://\(normalizedHost)/", forHTTPHeaderField: "referer")
        if let cookie, !cookie.isEmpty { request.setValue(cookie, forHTTPHeaderField: "cookie") }
        if let form {
            request.httpBody = form.data(using: .utf8)
            request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "content-type")
        }
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            return HTTPResult(statusCode: code, body: json)
        } catch {
            return HTTPResult(statusCode: 0, body: nil)
        }
    }

    private func retryable(_ code: Int, _ message: String) -> BlockOutcome {
        if code == 0 || code == 408 || code == 425 || code == 429 || code >= 500 { return .retry(message) }
        return .failed(message)
    }

    private func booleanValue(_ source: [String: Any], key: String) -> Bool? {
        switch source[key] {
        case let value as Bool:
            return value
        case let value as NSNumber:
            return value.boolValue
        case let value as String:
            switch value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
            case "true", "1": return true
            case "false", "0": return false
            default: return nil
            }
        default:
            return nil
        }
    }
}
