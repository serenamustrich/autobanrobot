import XCTest
@testable import AutoBanRobot

final class BlockJobTests: XCTestCase {
    func testBridgePayloadAcceptsXUsername() {
        let payload = #"{"username":"spam_123","displayName":"Spam","reason":"规则命中"}"#
        XCTAssertEqual(BlockJob(bridgePayload: payload)?.username, "spam_123")
    }

    func testBridgePayloadRejectsInvalidUsername() {
        XCTAssertNil(BlockJob(bridgePayload: #"{"username":"not valid"}"#))
    }
}
