package edu.utem.ftmk.slm

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ServerTimestamp

data class PredictionResult(
    val dataId: String = "",
    val name: String = "",
    val ingredients: String = "",
    val allergensRaw: String = "",
    val allergensMapped: String = "",
    val predictedAllergens: String = "",

    // Timing metrics - THESE ARE CRITICAL!
    val latencyMs: Long = 0,
    val ttftMs: Long = -1,      // Time To First Token
    val itps: Long = -1,        // Input Tokens Per Second
    val otps: Long = -1,        // Output Tokens Per Second
    val oetMs: Long = -1,       // Output Evaluation Time

    // Memory metrics
    val javaHeapKb: Long = 0,
    val nativeHeapKb: Long = 0,
    val totalPssKb: Long = 0,

    // Device info
    val deviceModel: String = "",
    val androidVersion: String = "",

    // Accuracy tracking
    val isCorrect: Boolean = false,

    @ServerTimestamp
    val timestamp: Timestamp? = null
) {
    /**
     * Convert to Map for Firebase storage
     */
    fun toMap(): Map<String, Any?> {
        return hashMapOf(
            "dataId" to dataId,
            "name" to name,
            "ingredients" to ingredients,
            "allergensRaw" to allergensRaw,
            "allergensMapped" to allergensMapped,
            "predictedAllergens" to predictedAllergens,

            // Timing metrics - MAKE SURE THESE ARE INCLUDED!
            "latencyMs" to latencyMs,
            "ttftMs" to ttftMs,
            "itps" to itps,
            "otps" to otps,
            "oetMs" to oetMs,

            // Memory metrics
            "javaHeapKb" to javaHeapKb,
            "nativeHeapKb" to nativeHeapKb,
            "totalPssKb" to totalPssKb,

            // Device info
            "deviceModel" to deviceModel,
            "androidVersion" to androidVersion,

            // Accuracy
            "isCorrect" to isCorrect,

            "timestamp" to FieldValue.serverTimestamp()
        )
    }
}