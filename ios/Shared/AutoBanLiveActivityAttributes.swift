import ActivityKit

struct AutoBanLiveActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var banTotal: Int
        var hiddenTotal: Int
        // Optional keeps activities created by earlier app versions decodable.
        var presentationRevision: Int?
    }

    var title: String
}
