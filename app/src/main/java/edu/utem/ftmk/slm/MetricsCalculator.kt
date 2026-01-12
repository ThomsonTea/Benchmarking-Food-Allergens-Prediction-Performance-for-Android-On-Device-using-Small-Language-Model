package edu.utem.ftmk.slm

import android.util.Log

/**
 * Utility class for calculating all prediction metrics
 */
object MetricsCalculator {
    
    private const val TAG = "MetricsCalculator"
    
    /**
     * Calculate all metrics for a single prediction
     */
    fun calculateMetrics(
        groundTruth: String,
        predicted: String,
        ingredients: String,
        totalAllergenCount: Int = 9  // Total possible allergens
    ): MetricsResult {
        
        // Parse allergen sets
        val groundTruthSet = parseAllergens(groundTruth)
        val predictedSet = parseAllergens(predicted)
        val ingredientSet = parseIngredients(ingredients)
        
        // Calculate confusion matrix
        val tp = groundTruthSet.intersect(predictedSet).size
        val fp = predictedSet.subtract(groundTruthSet).size
        val fn = groundTruthSet.subtract(predictedSet).size
        val tn = totalAllergenCount - tp - fp - fn
        
        // Calculate quality metrics
        val precision = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
        val recall = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
        val f1Score = if (precision + recall > 0) {
            2 * precision * recall / (precision + recall)
        } else 0.0
        
        val isExactMatch = groundTruthSet == predictedSet
        val hammingLoss = (fp + fn).toDouble() / totalAllergenCount
        val fnr = if (tp + fn > 0) fn.toDouble() / (tp + fn) else 0.0
        
        // Calculate safety metrics
        val hallucinatedAllergens = detectHallucinations(predictedSet, ingredientSet)
        val hasHallucination = hallucinatedAllergens.isNotEmpty()
        
        val overPredictedAllergens = predictedSet.subtract(groundTruthSet)
        val hasOverPrediction = overPredictedAllergens.isNotEmpty()
        
        val isAbstentionCase = groundTruthSet.isEmpty()
        val isAbstentionCorrect = isAbstentionCase && predictedSet.isEmpty()
        
        return MetricsResult(
            tp = tp,
            fp = fp,
            fn = fn,
            tn = tn,
            precision = precision,
            recall = recall,
            f1Score = f1Score,
            isExactMatch = isExactMatch,
            hammingLoss = hammingLoss,
            fnr = fnr,
            hasHallucination = hasHallucination,
            hallucinatedAllergens = hallucinatedAllergens.joinToString(", "),
            hasOverPrediction = hasOverPrediction,
            overPredictedAllergens = overPredictedAllergens.joinToString(", "),
            isAbstentionCase = isAbstentionCase,
            isAbstentionCorrect = isAbstentionCorrect
        )
    }
    
    /**
     * Parse allergen string to set
     */
    private fun parseAllergens(allergens: String): Set<String> {
        if (allergens.isBlank() || allergens.lowercase() == "none") {
            return emptySet()
        }
        
        return allergens.lowercase()
            .split(",", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "none" }
            .toSet()
    }
    
    /**
     * Parse ingredients to set of words (for hallucination detection)
     */
    private fun parseIngredients(ingredients: String): Set<String> {
        return ingredients.lowercase()
            .replace(Regex("[^a-z ]"), " ")
            .split(" ")
            .map { it.trim() }
            .filter { it.length > 2 }  // Ignore very short words
            .toSet()
    }
    
    /**
     * Detect hallucinated allergens (predicted but not in ingredients)
     */
    private fun detectHallucinations(
        predicted: Set<String>,
        ingredients: Set<String>
    ): Set<String> {
        
        val hallucinated = mutableSetOf<String>()
        
        // Define allergen keywords that should appear in ingredients
        val allergenKeywords = mapOf(
            "milk" to setOf("milk", "cream", "butter", "cheese", "yogurt", "whey", "casein", "lait", "dairy"),
            "egg" to setOf("egg", "oeuf", "albumin", "mayonnaise"),
            "peanut" to setOf("peanut", "groundnut", "arachide"),
            "tree nut" to setOf("nut", "hazelnut", "almond", "walnut", "cashew", "pecan", "pistachio", "macadamia", "noisette", "mandeln"),
            "wheat" to setOf("wheat", "flour", "gluten", "oat", "rye"),
            "soy" to setOf("soy", "soya", "soja", "lecithin", "tofu"),
            "fish" to setOf("fish", "salmon", "tuna", "cod", "anchov", "poisson"),
            "shellfish" to setOf("shellfish", "shrimp", "crab", "lobster", "prawn", "crevette"),
            "sesame" to setOf("sesame", "tahini", "sesamum")
        )
        
        for (allergen in predicted) {
            val keywords = allergenKeywords[allergen] ?: continue
            
            // Check if any keyword appears in ingredients
            val found = keywords.any { keyword ->
                ingredients.any { ingredient -> ingredient.contains(keyword) }
            }
            
            if (!found) {
                hallucinated.add(allergen)
            }
        }
        
        return hallucinated
    }
    
    /**
     * Calculate aggregate metrics across multiple predictions
     */
    fun calculateAggregateMetrics(results: List<PredictionResult>): AggregateMetrics {
        if (results.isEmpty()) {
            return AggregateMetrics()
        }
        
        // Sum up confusion matrix
        val totalTp = results.sumOf { it.truePositives }
        val totalFp = results.sumOf { it.falsePositives }
        val totalFn = results.sumOf { it.falseNegatives }
        val totalTn = results.sumOf { it.trueNegatives }
        
        // Calculate micro-averaged metrics
        val microPrecision = if (totalTp + totalFp > 0) {
            totalTp.toDouble() / (totalTp + totalFp)
        } else 0.0
        
        val microRecall = if (totalTp + totalFn > 0) {
            totalTp.toDouble() / (totalTp + totalFn)
        } else 0.0
        
        val microF1 = if (microPrecision + microRecall > 0) {
            2 * microPrecision * microRecall / (microPrecision + microRecall)
        } else 0.0
        
        // Calculate macro-averaged metrics (average of per-item metrics)
        val macroPrecision = results.map { it.precision }.average()
        val macroRecall = results.map { it.recall }.average()
        val macroF1 = results.map { it.f1Score }.average()
        
        // Calculate other metrics
        val exactMatchRatio = results.count { it.isExactMatch }.toDouble() / results.size
        val avgHammingLoss = results.map { it.hammingLoss }.average()
        val avgFnr = results.map { it.falseNegativeRate }.average()
        
        // Safety metrics
        val hallucinationRate = results.count { it.hasHallucination }.toDouble() / results.size
        val overPredictionRate = results.count { it.hasOverPrediction }.toDouble() / results.size
        
        val abstentionCases = results.filter { it.isAbstentionCase }
        val abstentionAccuracy = if (abstentionCases.isNotEmpty()) {
            abstentionCases.count { it.isAbstentionCorrect }.toDouble() / abstentionCases.size
        } else 0.0
        
        // Efficiency metrics
        val avgLatency = results.map { it.latencyMs }.average()
        val avgTtft = results.filter { it.ttftMs > 0 }.map { it.ttftMs }.average()
        val avgItps = results.filter { it.itps > 0 }.map { it.itps }.average()
        val avgOtps = results.filter { it.otps >= 0 }.map { it.otps }.average()
        val avgOet = results.filter { it.oetMs > 0 }.map { it.oetMs }.average()
        
        val avgJavaHeap = results.map { it.javaHeapKb }.average()
        val avgNativeHeap = results.map { it.nativeHeapKb }.average()
        val avgTotalPss = results.map { it.totalPssKb }.average()
        
        return AggregateMetrics(
            sampleCount = results.size,
            
            // Confusion matrix
            totalTp = totalTp,
            totalFp = totalFp,
            totalFn = totalFn,
            totalTn = totalTn,
            
            // Quality metrics
            microPrecision = microPrecision,
            microRecall = microRecall,
            microF1 = microF1,
            macroPrecision = macroPrecision,
            macroRecall = macroRecall,
            macroF1 = macroF1,
            exactMatchRatio = exactMatchRatio,
            avgHammingLoss = avgHammingLoss,
            avgFnr = avgFnr,
            
            // Safety metrics
            hallucinationRate = hallucinationRate,
            overPredictionRate = overPredictionRate,
            abstentionAccuracy = abstentionAccuracy,
            
            // Efficiency metrics
            avgLatency = avgLatency,
            avgTtft = avgTtft,
            avgItps = avgItps,
            avgOtps = avgOtps,
            avgOet = avgOet,
            avgJavaHeap = avgJavaHeap,
            avgNativeHeap = avgNativeHeap,
            avgTotalPss = avgTotalPss
        )
    }
}

/**
 * Result of metrics calculation for a single prediction
 */
data class MetricsResult(
    val tp: Int,
    val fp: Int,
    val fn: Int,
    val tn: Int,
    val precision: Double,
    val recall: Double,
    val f1Score: Double,
    val isExactMatch: Boolean,
    val hammingLoss: Double,
    val fnr: Double,
    val hasHallucination: Boolean,
    val hallucinatedAllergens: String,
    val hasOverPrediction: Boolean,
    val overPredictedAllergens: String,
    val isAbstentionCase: Boolean,
    val isAbstentionCorrect: Boolean
)

/**
 * Aggregate metrics across all predictions for a model
 */
data class AggregateMetrics(
    val sampleCount: Int = 0,
    
    // Confusion matrix totals
    val totalTp: Int = 0,
    val totalFp: Int = 0,
    val totalFn: Int = 0,
    val totalTn: Int = 0,
    
    // Quality metrics
    val microPrecision: Double = 0.0,
    val microRecall: Double = 0.0,
    val microF1: Double = 0.0,
    val macroPrecision: Double = 0.0,
    val macroRecall: Double = 0.0,
    val macroF1: Double = 0.0,
    val exactMatchRatio: Double = 0.0,
    val avgHammingLoss: Double = 0.0,
    val avgFnr: Double = 0.0,
    
    // Safety metrics
    val hallucinationRate: Double = 0.0,
    val overPredictionRate: Double = 0.0,
    val abstentionAccuracy: Double = 0.0,
    
    // Efficiency metrics
    val avgLatency: Double = 0.0,
    val avgTtft: Double = 0.0,
    val avgItps: Double = 0.0,
    val avgOtps: Double = 0.0,
    val avgOet: Double = 0.0,
    val avgJavaHeap: Double = 0.0,
    val avgNativeHeap: Double = 0.0,
    val avgTotalPss: Double = 0.0
)
