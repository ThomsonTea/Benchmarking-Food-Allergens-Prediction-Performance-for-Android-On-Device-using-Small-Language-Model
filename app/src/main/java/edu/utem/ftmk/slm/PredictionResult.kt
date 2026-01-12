package edu.utem.ftmk.slm

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ServerTimestamp

/**
 * Complete prediction result with all required metrics for benchmarking
 */
data class PredictionResult(
    // Basic info
    val dataId: String = "",
    val name: String = "",
    val ingredients: String = "",
    val allergensRaw: String = "",
    val allergensMapped: String = "",
    val predictedAllergens: String = "",

    // Model info
    val modelName: String = "",  // Which model was used

    // === PREDICTION QUALITY METRICS ===
    // Confusion matrix counts
    val truePositives: Int = 0,
    val falsePositives: Int = 0,
    val falseNegatives: Int = 0,
    val trueNegatives: Int = 0,

    // Calculated metrics (per item)
    val precision: Double = 0.0,
    val recall: Double = 0.0,
    val f1Score: Double = 0.0,
    val isExactMatch: Boolean = false,
    val hammingLoss: Double = 0.0,
    val falseNegativeRate: Double = 0.0,

    // === SAFETY METRICS ===
    val hasHallucination: Boolean = false,
    val hallucinatedAllergens: String = "",  // List of hallucinated allergens
    val hasOverPrediction: Boolean = false,
    val overPredictedAllergens: String = "",  // Extra allergens beyond ground truth
    val isAbstentionCase: Boolean = false,  // True if ground truth is "none"
    val isAbstentionCorrect: Boolean = false,  // True if correctly predicted "none"

    // === ON-DEVICE EFFICIENCY METRICS ===
    val latencyMs: Long = 0,
    val ttftMs: Long = -1,      // Time To First Token
    val itps: Long = -1,        // Input Tokens Per Second
    val otps: Long = -1,        // Output Tokens Per Second
    val oetMs: Long = -1,       // Output Evaluation Time
    val totalTimeMs: Long = 0,  // Total time (same as latency)

    // Memory metrics
    val javaHeapKb: Long = 0,
    val nativeHeapKb: Long = 0,
    val totalPssKb: Long = 0,

    // Device info
    val deviceModel: String = "",
    val androidVersion: String = "",

    @ServerTimestamp
    val timestamp: Timestamp? = null
) {
    /**
     * Convert to Map for Firebase storage
     */
    fun toMap(): Map<String, Any?> {
        return hashMapOf(
            // Basic info
            "dataId" to dataId,
            "name" to name,
            "ingredients" to ingredients,
            "allergensRaw" to allergensRaw,
            "allergensMapped" to allergensMapped,
            "predictedAllergens" to predictedAllergens,
            "modelName" to modelName,

            // Prediction quality metrics
            "truePositives" to truePositives,
            "falsePositives" to falsePositives,
            "falseNegatives" to falseNegatives,
            "trueNegatives" to trueNegatives,
            "precision" to precision,
            "recall" to recall,
            "f1Score" to f1Score,
            "isExactMatch" to isExactMatch,
            "hammingLoss" to hammingLoss,
            "falseNegativeRate" to falseNegativeRate,

            // Safety metrics
            "hasHallucination" to hasHallucination,
            "hallucinatedAllergens" to hallucinatedAllergens,
            "hasOverPrediction" to hasOverPrediction,
            "overPredictedAllergens" to overPredictedAllergens,
            "isAbstentionCase" to isAbstentionCase,
            "isAbstentionCorrect" to isAbstentionCorrect,

            // Efficiency metrics
            "latencyMs" to latencyMs,
            "ttftMs" to ttftMs,
            "itps" to itps,
            "otps" to otps,
            "oetMs" to oetMs,
            "totalTimeMs" to totalTimeMs,

            // Memory metrics
            "javaHeapKb" to javaHeapKb,
            "nativeHeapKb" to nativeHeapKb,
            "totalPssKb" to totalPssKb,

            // Device info
            "deviceModel" to deviceModel,
            "androidVersion" to androidVersion,

            // Timestamp - use FieldValue for server-side timestamp
            "timestamp" to FieldValue.serverTimestamp()
        )
    }

    /**
     * Convert to Excel row (for export)
     */
    fun toExcelRow(): List<Any> {
        return listOf(
            dataId,
            name,
            ingredients,
            allergensMapped,
            predictedAllergens,
            truePositives,
            falsePositives,
            falseNegatives,
            trueNegatives,
            String.format("%.4f", precision),
            String.format("%.4f", recall),
            String.format("%.4f", f1Score),
            if (isExactMatch) "Yes" else "No",
            String.format("%.4f", hammingLoss),
            String.format("%.4f", falseNegativeRate),
            if (hasHallucination) "Yes" else "No",
            hallucinatedAllergens,
            if (hasOverPrediction) "Yes" else "No",
            overPredictedAllergens,
            latencyMs,
            ttftMs,
            itps,
            otps,
            oetMs,
            javaHeapKb,
            nativeHeapKb,
            totalPssKb
        )
    }

    companion object {
        /**
         * Get Excel column headers
         */
        fun getExcelHeaders(): List<String> {
            return listOf(
                "ID",
                "Name",
                "Ingredients",
                "Ground Truth",
                "Predicted",
                "TP",
                "FP",
                "FN",
                "TN",
                "Precision",
                "Recall",
                "F1 Score",
                "Exact Match",
                "Hamming Loss",
                "FNR",
                "Has Hallucination",
                "Hallucinated Allergens",
                "Has Over-Prediction",
                "Over-Predicted Allergens",
                "Latency (ms)",
                "TTFT (ms)",
                "ITPS",
                "OTPS",
                "OET (ms)",
                "Java Heap (KB)",
                "Native Heap (KB)",
                "Total PSS (KB)"
            )
        }
    }
}
