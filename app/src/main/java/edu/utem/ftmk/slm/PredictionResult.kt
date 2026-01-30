package edu.utem.ftmk.slm

data class PredictionResult(
    // Basic Info
    val dataId: String,
    val name: String,
    val ingredients: String,
    val allergensRaw: String,
    val allergensMapped: String,
    val predictedAllergens: String,
    val modelName: String,

    // Quality Metrics
    val truePositives: Int,
    val falsePositives: Int,
    val falseNegatives: Int,
    val trueNegatives: Int,
    val precision: Double,
    val recall: Double,
    val f1Score: Double,
    val accuracy: Double,
    val isExactMatch: Boolean,
    val hammingLoss: Double,
    val falseNegativeRate: Double,

    // Error Analysis - ✅ CHANGED TO COUNTS
    val hallucinationCount: Int,  // ✅ Changed from hasHallucination: Boolean
    val hallucinatedAllergens: String,
    val overPredictionCount: Int,  // ✅ Changed from hasOverPrediction: Boolean
    val overPredictedAllergens: String,
    val isAbstentionCase: Boolean,
    val isAbstentionCorrect: Boolean,

    // Performance Metrics
    val latencyMs: Long,
    val ttftMs: Long,
    val itps: Long,
    val otps: Long,
    val oetMs: Long,
    val totalTimeMs: Long,

    // Memory Metrics
    val javaHeapKb: Long,
    val nativeHeapKb: Long,
    val totalPssKb: Long,

    // Device Info
    val deviceModel: String,
    val androidVersion: String,

    // Timestamp
    val timestamp: Long = System.currentTimeMillis()
)