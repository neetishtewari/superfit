---
name: fitness-intelligence-spec
description: Always-on architectural constraint for a modern, Health Connect-driven fitness and nutrition application.
---

# Project Blueprint: Modern Biometric Telemetry & Nutrition Engine

You are a principal Android solutions architect. Your objective is to build a modern, high-signal fitness and nutrition ecosystem in Kotlin and Jetpack Compose. The core value proposition is utilizing native Google Health Connect telemetry to dynamically drive nutritional intelligence, recovery windows, and physical output targeting.

## 1. System Infrastructure & Data Streams

The application acts as a real-time analytics dashboard fed by Google Health Connect SDK v1.1.0 (Stable). It aggregates three foundational telemetry vectors:

### Vector 1: Physical Output & Activity Telemetry
- **Ingestion:** Continuous asynchronous background polling of `TotalCaloriesBurnedRecord`, `StepsRecord`, and `ExerciseSessionRecord`.
- **Processing:** Detects activity intensity profiles (e.g., distinguishing an endurance run from daily baseline steps) to automatically scale physiological resource needs.

### Vector 2: Intelligent Nutrition Ledger
- **Ingestion:** Natural language micro-logging inputs parsed locally into clean semantic data structures.
- **Function:** Tracks total energy input, structural macronutrients, and micro-densities relative to the day's real-time metabolic burn.

### Vector 3: Recovery & Homeostasis Metrics
- **Ingestion:** Pulls sleep state records (`SleepSessionRecord`) and resting vitals where available.
- **Processing:** Correlates sleep duration and physical exertion to generate adaptive daily readiness zones.

---

## 2. Antigravity 2.0 Agent Choreography

When writing or refactoring code within this workspace, delegate development across these specialized operational subagents:

1. **`TelemetryOrchestrator`:** Owns the Health Connect background workers, permission state loops, session queries, and local data aggregation pipelines.
2. **`IntelligenceEngine`:** Builds the Room DB schema layer, data correlation logic, and semantic parsing algorithms that turn physical data into insights.
3. **`DashboardEngineer`:** Builds a highly responsive, modern Jetpack Compose interface that visualizes live biometric balances, adaptive target wheels, and physical trends.

## 3. Strict Development Boundaries

- **UI Pattern:** Strictly declarative Jetpack Compose utilizing unidirectional data flow (UDF) via state-driven ViewModels.
- **Local-First Processing:** Core biometric parsing, trends, and data correlations must process entirely on-device via Room/SQLite for maximum performance and security.
- **Self-Healing Loop:** Intercept any Kotlin compiler or Android linting errors directly from terminal output, patch the structural files automatically, and verify builds autonomously.