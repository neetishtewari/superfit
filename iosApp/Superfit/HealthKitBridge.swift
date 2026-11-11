import Foundation
import HealthKit
import Shared

/// Reads Apple Health for shared Kotlin code: day totals and sleep stretches over a time range.
/// Read only: Superfit never asks to write anything to Apple Health. Answers come back on the
/// main thread, once each.
final class HealthKitBridge: NSObject, IosHealth {
    private let store = HKHealthStore()

    private let readTypes: Set<HKObjectType> = [
        HKQuantityType(.stepCount),
        HKQuantityType(.activeEnergyBurned),
        HKQuantityType(.distanceWalkingRunning),
        HKCategoryType(.sleepAnalysis),
    ]

    func isAvailable() -> Bool {
        HKHealthStore.isHealthDataAvailable()
    }

    func requestAccess(done: @escaping (String?) -> Void) {
        store.requestAuthorization(toShare: [], read: readTypes) { _, error in
            DispatchQueue.main.async { done(error?.localizedDescription) }
        }
    }

    func sum(kind: String, startMs: Int64, endMs: Int64, done: @escaping (KotlinDouble?, String?) -> Void) {
        let (type, unit): (HKQuantityType, HKUnit)
        switch kind {
        case "steps": (type, unit) = (HKQuantityType(.stepCount), .count())
        case "activeKcal": (type, unit) = (HKQuantityType(.activeEnergyBurned), .kilocalorie())
        case "distanceM": (type, unit) = (HKQuantityType(.distanceWalkingRunning), .meter())
        default:
            done(nil, "Unknown health value \(kind)")
            return
        }
        // Apple Health adds the iPhone and the watch together without counting a step twice.
        let query = HKStatisticsQuery(
            quantityType: type,
            quantitySamplePredicate: Self.range(startMs, endMs),
            options: .cumulativeSum
        ) { _, statistics, error in
            DispatchQueue.main.async {
                if let error, (error as? HKError)?.code != .errorNoData {
                    done(nil, error.localizedDescription)
                } else {
                    done(statistics?.sumQuantity().map { KotlinDouble(value: $0.doubleValue(for: unit)) }, nil)
                }
            }
        }
        store.execute(query)
    }

    func sleepSpans(startMs: Int64, endMs: Int64, done: @escaping ([KotlinLong]?, String?) -> Void) {
        let query = HKSampleQuery(
            sampleType: HKCategoryType(.sleepAnalysis),
            predicate: Self.range(startMs, endMs),
            limit: HKObjectQueryNoLimit,
            sortDescriptors: nil
        ) { _, samples, error in
            DispatchQueue.main.async {
                if let error, (error as? HKError)?.code != .errorNoData {
                    done(nil, error.localizedDescription)
                    return
                }
                let all = (samples as? [HKCategorySample]) ?? []
                let asleepValues = Set(HKCategoryValueSleepAnalysis.allAsleepValues.map(\.rawValue))
                let asleep = all.filter { asleepValues.contains($0.value) }
                // Without a watch or sleep app the iPhone only knows time in bed.
                let inBed = all.filter { $0.value == HKCategoryValueSleepAnalysis.inBed.rawValue }
                let chosen = asleep.isEmpty ? inBed : asleep
                done(chosen.flatMap { [Self.ms($0.startDate), Self.ms($0.endDate)] }, nil)
            }
        }
        store.execute(query)
    }

    private static func range(_ startMs: Int64, _ endMs: Int64) -> NSPredicate {
        HKQuery.predicateForSamples(
            withStart: Date(timeIntervalSince1970: Double(startMs) / 1000),
            end: Date(timeIntervalSince1970: Double(endMs) / 1000),
            options: .strictStartDate
        )
    }

    private static func ms(_ date: Date) -> KotlinLong {
        KotlinLong(value: Int64(date.timeIntervalSince1970 * 1000))
    }
}
