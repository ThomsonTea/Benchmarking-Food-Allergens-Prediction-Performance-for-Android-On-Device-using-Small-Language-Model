package edu.utem.ftmk.slm

object MetricsCalculator {

    data class Metrics(
        val tp: Int,
        val fp: Int,
        val fn: Int,
        val tn: Int,
        val precision: Double,
        val recall: Double,
        val f1Score: Double,
        val accuracy: Double,
        val isExactMatch: Boolean,
        val hammingLoss: Double,
        val fnr: Double,
        val hallucinationCount: Int,  // ✅ CHANGED from Boolean
        val hallucinatedAllergens: String,
        val overPredictionCount: Int,  // ✅ CHANGED from Boolean
        val overPredictedAllergens: String,
        val isAbstentionCase: Boolean,
        val isAbstentionCorrect: Boolean
    )

    fun calculateMetrics(
        groundTruth: String,
        predicted: String,
        ingredients: String
    ): Metrics {

        // Parse allergens into sets
        val gtSet = parseAllergens(groundTruth)
        val predSet = parseAllergens(predicted)
        val allAllergens = setOf("milk", "egg", "peanut", "tree nut", "wheat", "soy", "fish", "shellfish", "sesame")

        var tp = 0
        var fp = 0
        var fn = 0
        var tn = 0

        // Normal multi-label calculation
        for (allergen in allAllergens) {
            val inGroundTruth = allergen in gtSet
            val inPredicted = allergen in predSet

            when {
                inGroundTruth && inPredicted -> tp++      // True Positive
                !inGroundTruth && inPredicted -> fp++     // False Positive
                inGroundTruth && !inPredicted -> fn++     // False Negative
                !inGroundTruth && !inPredicted -> tn++    // True Negative
            }
        }

        // Calculate Precision
        val precision = if (tp + fp > 0) {
            tp.toDouble() / (tp + fp)
        } else {
            0.0
        }

        // Calculate Recall
        val recall = if (tp + fn > 0) {
            tp.toDouble() / (tp + fn)
        } else {
            0.0
        }

        // Calculate F1 Score
        val f1Score = if (precision + recall > 0) {
            2 * (precision * recall) / (precision + recall)
        } else {
            0.0
        }

        // Calculate Accuracy
        val totalLabels = tp + fp + fn + tn
        val accuracy = if (totalLabels > 0) {
            (tp + tn).toDouble() / totalLabels
        } else {
            0.0
        }

        // Exact Match
        val isExactMatch = (groundTruth.trim().lowercase() == predicted.trim().lowercase())

        // Hamming Loss
        val hammingLoss = (fp + fn).toDouble() / allAllergens.size

        // False Negative Rate
        val fnr = if (tp + fn > 0) {
            fn.toDouble() / (tp + fn)
        } else {
            0.0
        }

        val hallucinatedList = checkHallucinations(predSet, ingredients)
        val overPredictedList = (predSet - gtSet).sorted()

        val hallucinationCount = hallucinatedList.size
        val overPredictionCount = overPredictedList.size

        // Abstention Detection
        val isAbstentionCase = (predicted.trim().lowercase() == "none")
        val isAbstentionCorrect = (predicted.trim().lowercase() == "none" && groundTruth.trim().lowercase() == "none")

        return Metrics(
            tp = tp,
            fp = fp,
            fn = fn,
            tn = tn,
            precision = precision,
            recall = recall,
            f1Score = f1Score,
            accuracy = accuracy,
            isExactMatch = isExactMatch,
            hammingLoss = hammingLoss,
            fnr = fnr,
            hallucinationCount = hallucinationCount,
            hallucinatedAllergens = hallucinatedList.joinToString(", "),
            overPredictionCount = overPredictionCount,
            overPredictedAllergens = overPredictedList.joinToString(", "),
            isAbstentionCase = isAbstentionCase,
            isAbstentionCorrect = isAbstentionCorrect
        )
    }

    private fun parseAllergens(allergens: String): Set<String> {
        if (allergens.trim().lowercase() == "none") return emptySet()
        return allergens.lowercase()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun checkHallucinations(predicted: Set<String>, ingredients: String): List<String> {
        val ingredientsLower = ingredients.lowercase()
        val hallucinated = mutableListOf<String>()

        for (allergen in predicted) {
            val found = when (allergen) {
                "milk" -> ingredientsLower.contains("milk") ||
                        ingredientsLower.contains("cream") ||
                        ingredientsLower.contains("butter") ||
                        ingredientsLower.contains("whey") ||
                        ingredientsLower.contains("casein") ||
                        ingredientsLower.contains("lactose")

                "egg" -> ingredientsLower.contains("egg") ||
                        ingredientsLower.contains("albumin") ||
                        ingredientsLower.contains("mayonnaise")

                "peanut" -> ingredientsLower.contains("peanut") ||
                        ingredientsLower.contains("groundnut")

                "tree nut" -> ingredientsLower.contains("nut") ||
                        ingredientsLower.contains("almond") ||
                        ingredientsLower.contains("cashew") ||
                        ingredientsLower.contains("walnut") ||
                        ingredientsLower.contains("pecan") ||
                        ingredientsLower.contains("pistachio") ||
                        ingredientsLower.contains("hazelnut") ||
                        ingredientsLower.contains("macadamia")

                "wheat" -> ingredientsLower.contains("wheat") ||
                        ingredientsLower.contains("flour") ||
                        ingredientsLower.contains("gluten") ||
                        ingredientsLower.contains("semolina")

                "soy" -> ingredientsLower.contains("soy") ||
                        ingredientsLower.contains("soya") ||
                        ingredientsLower.contains("lecithin") ||
                        ingredientsLower.contains("tofu") ||
                        ingredientsLower.contains("edamame")

                "fish" -> ingredientsLower.contains("fish") ||
                        ingredientsLower.contains("anchov") ||
                        ingredientsLower.contains("sardine") ||
                        ingredientsLower.contains("tuna") ||
                        ingredientsLower.contains("salmon")

                "shellfish" -> ingredientsLower.contains("shellfish") ||
                        ingredientsLower.contains("shrimp") ||
                        ingredientsLower.contains("crab") ||
                        ingredientsLower.contains("lobster") ||
                        ingredientsLower.contains("prawn") ||
                        ingredientsLower.contains("clam") ||
                        ingredientsLower.contains("oyster")

                "sesame" -> ingredientsLower.contains("sesame") ||
                        ingredientsLower.contains("tahini")

                else -> false
            }

            if (!found) {
                hallucinated.add(allergen)
            }
        }

        return hallucinated
    }
}