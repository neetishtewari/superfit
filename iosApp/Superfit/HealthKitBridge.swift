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
        // Workouts from the watch and other apps, with their distance and heart rate.
        HKObjectType.workoutType(),
        HKQuantityType(.distanceCycling),
        HKQuantityType(.distanceSwimming),
        HKQuantityType(.heartRate),
    ]

    // A workout's distance is kept under the kind that fits it; the first one it has is used.
    private let distanceTypes: [HKQuantityType] = [
        HKQuantityType(.distanceWalkingRunning),
        HKQuantityType(.distanceCycling),
        HKQuantityType(.distanceSwimming),
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

    func workouts(startMs: Int64, endMs: Int64, done: @escaping ([IosWorkout]?, String?) -> Void) {
        let query = HKSampleQuery(
            sampleType: HKObjectType.workoutType(),
            predicate: Self.range(startMs, endMs),
            limit: 2000,
            sortDescriptors: [NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: false)]
        ) { [distanceTypes] _, samples, error in
            DispatchQueue.main.async {
                if let error, (error as? HKError)?.code != .errorNoData {
                    done(nil, error.localizedDescription)
                    return
                }
                let workouts = (samples as? [HKWorkout]) ?? []
                done(workouts.map { Self.describe($0, distanceTypes: distanceTypes) }, nil)
            }
        }
        store.execute(query)
    }

    private static func describe(_ workout: HKWorkout, distanceTypes: [HKQuantityType]) -> IosWorkout {
        let source = workout.sourceRevision.source
        let distance = distanceTypes.lazy
            .compactMap { workout.statistics(for: $0)?.sumQuantity()?.doubleValue(for: .meter()) }
            .first { $0 > 0 } ?? 0
        let active = workout.statistics(for: HKQuantityType(.activeEnergyBurned))?.sumQuantity()?.doubleValue(for: .kilocalorie()) ?? 0
        let heartRate = workout.statistics(for: HKQuantityType(.heartRate))?.averageQuantity()?
            .doubleValue(for: HKUnit.count().unitDivided(by: .minute())) ?? 0
        let indoorFlag = (workout.metadata?[HKMetadataKeyIndoorWorkout] as? NSNumber)?.boolValue ?? false
        let pool = (workout.metadata?[HKMetadataKeySwimmingLocationType] as? NSNumber)?.intValue
            == HKWorkoutSwimmingLocationType.pool.rawValue
        return IosWorkout(
            id: workout.uuid.uuidString,
            sourceId: source.bundleIdentifier,
            sourceName: source.name,
            appleType: Int64(workout.workoutActivityType.rawValue),
            indoor: indoorFlag || pool,
            startMs: Int64(workout.startDate.timeIntervalSince1970 * 1000),
            endMs: Int64(workout.endDate.timeIntervalSince1970 * 1000),
            distanceMeters: distance,
            activeKcal: active,
            avgHeartRate: heartRate
        )
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
