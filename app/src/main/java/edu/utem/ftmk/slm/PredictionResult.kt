package edu.utem.ftmk.slm

/**
 * Data class representing a single prediction result
 * NOW INCLUDES ACCURACY METRIC!
 */
data class PredictionResult(
    // Product Information
    val dataId: String = "",
    val name: String = "",
    val ingredients: String = "",
    val allergensRaw: String = "",
    val allergensMapped: String = "",
    val predictedAllergens: String = "",
    val modelName: String = "",

    // Confusion Matrix
    val truePositives: Int = 0,
    val falsePositives: Int = 0,
    val falseNegatives: Int = 0,
    val trueNegatives: Int = 0,

    // Quality Metrics
    val precision: Double = 0.0,
    val recall: Double = 0.0,
    val f1Score: Double = 0.0,
    val accuracy: Double = 0.0,  // ← NEW: ACCURACY METRIC
    val isExactMatch: Boolean = false,
    val hammingLoss: Double = 0.0,
    val falseNegativeRate: Double = 0.0,

    // Safety Metrics
    val hasHallucination: Boolean = false,
    val hallucinatedAllergens: String = "",
    val hasOverPrediction: Boolean = false,
    val overPredictedAllergens: String = "",
    val isAbstentionCase: Boolean = false,
    val isAbstentionCorrect: Boolean = false,

    // Performance Metrics
    val latencyMs: Long = 0L,
    val ttftMs: Long = 0L,
    val itps: Long = 0L,
    val otps: Long = 0L,
    val oetMs: Long = 0L,
    val totalTimeMs: Long = 0L,

    // Memory Metrics
    val javaHeapKb: Long = 0L,
    val nativeHeapKb: Long = 0L,
    val totalPssKb: Long = 0L,

    // Device Information
    val deviceModel: String = "",
    val androidVersion: String = ""
)
