import FirebaseFirestore
import Foundation
import Shared

/// The shared backup's cloud database on Firestore's iPhone SDK. Paths and fields are the same as
/// on Android (users/{uid}/...), so either app restores what the other saved.
final class FirestoreCloudStore: NSObject, IosCloudStore {
    private let db = Firestore.firestore()

    func get(path: String, done: @escaping ([String: Any]?, String?) -> Void) {
        db.document(path).getDocument { snapshot, error in
            if let error { return done(nil, error.localizedDescription) }
            done(snapshot?.data().map(FirestoreValues.toKotlin), nil)
        }
    }

    func list(collectionPath: String, done: @escaping ([CloudDoc]?, String?) -> Void) {
        db.collection(collectionPath).getDocuments { snapshot, error in
            if let error { return done(nil, error.localizedDescription) }
            let docs = snapshot?.documents.map { CloudDoc(id: $0.documentID, data: FirestoreValues.toKotlin($0.data())) } ?? []
            done(docs, nil)
        }
    }

    func set(path: String, data: [String: Any], done: @escaping (String?) -> Void) {
        db.document(path).setData(FirestoreValues.toFirestore(data)) { error in done(error?.localizedDescription) }
    }

    func setAll(paths: [String], data: [[String: Any]], done: @escaping (String?) -> Void) {
        let batch = db.batch()
        for (path, fields) in zip(paths, data) {
            batch.setData(FirestoreValues.toFirestore(fields), forDocument: db.document(path))
        }
        batch.commit { error in done(error?.localizedDescription) }
    }

    func delete(path: String, done: @escaping (String?) -> Void) {
        db.document(path).delete { error in done(error?.localizedDescription) }
    }

    func waitForPendingWrites(done: @escaping (String?) -> Void) {
        db.waitForPendingWrites { error in done(error?.localizedDescription) }
    }
}

/// Firestore on iPhone uses NSNumber for every number and Boolean; shared Kotlin code expects Long,
/// Double and Boolean. Booleans must be written as real Booleans, or Android would read 1/0.
enum FirestoreValues {
    static func toKotlin(_ fields: [String: Any]) -> [String: Any] {
        fields.mapValues(toKotlinValue)
    }

    private static func toKotlinValue(_ value: Any) -> Any {
        switch value {
        case let number as NSNumber:
            if CFGetTypeID(number) == CFBooleanGetTypeID() { return KotlinBoolean(value: number.boolValue) }
            let type = String(cString: number.objCType)
            if type == "d" || type == "f" { return KotlinDouble(value: number.doubleValue) }
            return KotlinLong(value: number.int64Value)
        case let list as [Any]:
            return list.map(toKotlinValue)
        case let map as [String: Any]:
            return toKotlin(map)
        default:
            return value
        }
    }

    static func toFirestore(_ fields: [String: Any]) -> [String: Any] {
        fields.mapValues(toFirestoreValue)
    }

    private static func toFirestoreValue(_ value: Any) -> Any {
        switch value {
        case let flag as KotlinBoolean:
            return flag.boolValue
        case let number as KotlinDouble:
            return number.doubleValue
        case let number as KotlinFloat:
            return Double(number.floatValue)
        case let number as KotlinLong:
            return number.int64Value
        case let number as KotlinInt:
            return Int64(number.int32Value)
        case let list as [Any]:
            return list.map(toFirestoreValue)
        case let map as [String: Any]:
            return toFirestore(map)
        default:
            return value
        }
    }
}
