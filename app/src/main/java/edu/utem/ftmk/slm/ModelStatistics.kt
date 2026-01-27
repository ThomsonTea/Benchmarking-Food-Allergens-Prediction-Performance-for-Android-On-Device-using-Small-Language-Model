package edu.utem.ftmk.slm

/**
 * Model statistics data class for dashboard comparison
 * Contains all averaged metrics for a model
 */
data class ModelStatistics(
    val modelName: String,
    val count: Int,
    val predictions: List<PredictionResult>,
    
    // Quality Metrics
    val avgAccuracy: Double,
    val avgF1: Double,
    val avgPrecision: Double,
    val avgRecall: Double,
    
    // Confusion Matrix Averages
    val avgTP: Double,
    val avgFP: Double,
    val avgFN: Double,
    val avgTN: Double,
    
    // Performance Metrics
    val avgLatency: Double,
    val avgTTFT: Double,
    val avgITPS: Double,
    val avgOTPS: Double,
    val avgOET: Double,
    val avgMemory: Double
)
