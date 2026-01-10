package edu.utem.ftmk.slm

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a prediction result to be stored in Firebase
 */
data class PredictionResult(
    val dataId: String = "",
    val name: String = "",
    val ingredients: String = "",
    val allergensRaw: String = "",
    val allergensMapped: String = "",
    val predictedAllergens: String = "",
    
    @ServerTimestamp
    val timestamp: Date? = null,
    
    // Inference Metrics
    val latencyMs: Long = 0,
    val javaHeapKb: Long = 0,
    val nativeHeapKb: Long = 0,
    val totalPssKb: Long = 0,
    val ttft: Long = 0,
    val itps: Long = 0,
    val otps: Long = 0,
    val oet: Long = 0,
    
    // Additional info
    val deviceModel: String = "",
    val androidVersion: String = ""
) {
    // Convert to map for Firebase
    fun toMap(): Map<String, Any?> {
        return hashMapOf(
            "dataId" to dataId,
            "name" to name,
            "ingredients" to ingredients,
            "allergensRaw" to allergensRaw,
            "allergensMapped" to allergensMapped,
            "predictedAllergens" to predictedAllergens,
            "timestamp" to timestamp,
            "latencyMs" to latencyMs,
            "javaHeapKb" to javaHeapKb,
            "nativeHeapKb" to nativeHeapKb,
            "totalPssKb" to totalPssKb,
            "ttft" to ttft,
            "itps" to itps,
            "otps" to otps,
            "oet" to oet,
            "deviceModel" to deviceModel,
            "androidVersion" to androidVersion
        )
    }
}
