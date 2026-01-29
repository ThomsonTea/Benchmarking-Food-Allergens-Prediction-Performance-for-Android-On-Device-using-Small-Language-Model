package edu.utem.ftmk.slm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log

/**
 * Adapter for displaying prediction history in a list
 * UPDATED: Now includes all metrics (Quality, Safety, Efficiency) with proper binding
 */
class PredictionHistoryAdapter(
    private var predictions: MutableList<PredictionResult>
) : RecyclerView.Adapter<PredictionHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.predictionCard)
        val productName: TextView = view.findViewById(R.id.productNameText)
        val modelName: TextView = view.findViewById(R.id.modelNameText)
        val groundTruth: TextView = view.findViewById(R.id.groundTruthText)
        val predicted: TextView = view.findViewById(R.id.predictedText)

        // Quality Metrics
        val f1Score: TextView = view.findViewById(R.id.f1ScoreText)
        val precision: TextView = view.findViewById(R.id.precisionText)
        val recall: TextView = view.findViewById(R.id.recallText)
        val accuracy: TextView = view.findViewById(R.id.accuracyText)
        val exactMatchText: TextView = view.findViewById(R.id.exactMatchText)
        val hammingLossText: TextView = view.findViewById(R.id.hammingLossText)
        val fnrText: TextView = view.findViewById(R.id.fnrText)

        // Confusion Matrix (individual components)
        val tpText: TextView = view.findViewById(R.id.tpText)
        val fpText: TextView = view.findViewById(R.id.fpText)
        val fnText: TextView = view.findViewById(R.id.fnText)
        val tnText: TextView = view.findViewById(R.id.tnText)

        // Safety Metrics
        val hallucinationText: TextView = view.findViewById(R.id.hallucinationText)
        val overPredictionText: TextView = view.findViewById(R.id.overPredictionText)
        val abstentionText: TextView = view.findViewById(R.id.abstentionText)

        // Efficiency Metrics
        val latency: TextView = view.findViewById(R.id.latencyText)
        val ttftText: TextView = view.findViewById(R.id.ttftText)
        val itpsText: TextView = view.findViewById(R.id.itpsText)
        val otpsText: TextView = view.findViewById(R.id.otpsText)
        val oetText: TextView = view.findViewById(R.id.oetText)
        val totalTimeText: TextView = view.findViewById(R.id.totalTimeText)
        val memoryText: TextView = view.findViewById(R.id.memoryText)

        val statusBadge: TextView = view.findViewById(R.id.statusBadge)
        val detailsContainer: View = view.findViewById(R.id.detailsContainer)
        val ingredientsText: TextView = view.findViewById(R.id.ingredientsText)
        val confusionMatrix: TextView = view.findViewById(R.id.confusionMatrixText)
        val safetyMetrics: TextView = view.findViewById(R.id.safetyMetricsText)

        // Timestamp
        val timestampText: TextView? = try {
            view.findViewById(R.id.timestampText)
        } catch (e: Exception) {
            null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prediction_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val prediction = predictions[position]

        // 🔍 DEBUG: Log what we're trying to display
        Log.d("ADAPTER_BIND", """
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        Position: $position
        Name: ${prediction.name}
        F1: ${prediction.f1Score}
        Precision: ${prediction.precision}
        Recall: ${prediction.recall}
        Accuracy: ${prediction.accuracy}
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    """.trimIndent())

        // Basic info
        holder.productName.text = prediction.name
        holder.modelName.text = prediction.modelName
        holder.groundTruth.text = "Actual: ${prediction.allergensMapped}"
        holder.predicted.text = "Predicted: ${prediction.predictedAllergens}"

        // === QUALITY METRICS (Table 2) ===
        holder.f1Score.text = "F1: ${String.format("%.3f", prediction.f1Score)}"
        holder.precision.text = "P: ${String.format("%.3f", prediction.precision)}"
        holder.recall.text = "R: ${String.format("%.3f", prediction.recall)}"
        holder.accuracy.text = "Acc: ${String.format("%.3f", prediction.accuracy)}"

        holder.exactMatchText.text = if (prediction.isExactMatch) "EM: Yes" else "EM: No"
        holder.hammingLossText.text = "HL: ${String.format("%.3f", prediction.hammingLoss)}"
        holder.fnrText.text = "FNR: ${String.format("%.3f", prediction.falseNegativeRate)}"

        // Confusion Matrix (individual components)
        holder.tpText.text = "TP: ${prediction.truePositives}"
        holder.fpText.text = "FP: ${prediction.falsePositives}"
        holder.fnText.text = "FN: ${prediction.falseNegatives}"
        holder.tnText.text = "TN: ${prediction.trueNegatives}"

        // === SAFETY METRICS (Table 3) ===
        holder.hallucinationText.text = if (prediction.hasHallucination) {
            "Hallucination: Yes (${prediction.hallucinatedAllergens})"
        } else {
            "Hallucination: No"
        }

        holder.overPredictionText.text = if (prediction.hasOverPrediction) {
            "Over-Prediction: Yes (${prediction.overPredictedAllergens})"
        } else {
            "Over-Prediction: No"
        }

        holder.abstentionText.text = when {
            prediction.isAbstentionCase && prediction.isAbstentionCorrect -> "Abstention: ✓ Correct"
            prediction.isAbstentionCase && !prediction.isAbstentionCorrect -> "Abstention: ✗ Wrong"
            else -> "Abstention: N/A"
        }

        // === EFFICIENCY METRICS (Table 4) ===
        holder.latency.text = "${prediction.latencyMs / 1000}s"
        holder.ttftText.text = "TTFT: ${prediction.ttftMs}ms"
        holder.itpsText.text = "ITPS: ${prediction.itps}"
        holder.otpsText.text = "OTPS: ${prediction.otps}"
        holder.oetText.text = "OET: ${prediction.oetMs}ms"
        holder.totalTimeText.text = "Tot: ${prediction.totalTimeMs}ms"

        // Memory (combined display)
        val totalMemoryMB = (prediction.javaHeapKb + prediction.nativeHeapKb + prediction.totalPssKb) / 1024
        holder.memoryText.text = "Mem: ${totalMemoryMB}MB"

        // Timestamp
        holder.timestampText?.let {
            val timestamp = prediction.timestamp
            val sdf = SimpleDateFormat("🕐 MMM dd, yyyy hh:mm:ss a", Locale.getDefault())
            it.text = sdf.format(Date(timestamp))
        }

        // Status badge
        when {
            prediction.isExactMatch -> {
                holder.statusBadge.text = "✓ PERFECT"
                holder.statusBadge.setBackgroundResource(R.drawable.bg_badge_success)
                holder.statusBadge.visibility = View.VISIBLE
            }
            prediction.hasHallucination -> {
                holder.statusBadge.text = "⚠ HALLUCINATION"
                holder.statusBadge.setBackgroundResource(R.drawable.bg_badge_danger)
                holder.statusBadge.visibility = View.VISIBLE
            }
            prediction.falseNegatives > 0 -> {
                holder.statusBadge.text = "⚠ MISSED ${prediction.falseNegatives}"
                holder.statusBadge.setBackgroundResource(R.drawable.bg_badge_warning)
                holder.statusBadge.visibility = View.VISIBLE
            }
            prediction.f1Score >= 0.8 -> {
                holder.statusBadge.text = "✓ GOOD"
                holder.statusBadge.setBackgroundResource(R.drawable.bg_badge_success)
                holder.statusBadge.visibility = View.VISIBLE
            }
            prediction.accuracy >= 80.0 -> {
                holder.statusBadge.text = "✓ GOOD"
                holder.statusBadge.setBackgroundResource(R.drawable.bg_badge_success)
                holder.statusBadge.visibility = View.VISIBLE
            }
            else -> {
                holder.statusBadge.visibility = View.GONE
            }
        }

        // Card color based on quality
        val cardColor = when {
            prediction.isExactMatch -> R.color.success_light
            prediction.f1Score >= 0.8 -> R.color.success_very_light
            prediction.f1Score >= 0.6 -> R.color.warning_very_light
            else -> R.color.danger_very_light
        }
        holder.card.setCardBackgroundColor(holder.itemView.context.getColor(cardColor))

        // Details (initially hidden, for expandable content)
        holder.detailsContainer.visibility = View.GONE
        holder.ingredientsText.text = "Ingredients: ${prediction.ingredients}"
        holder.confusionMatrix.text = buildConfusionMatrixText(prediction)
        holder.safetyMetrics.text = buildSafetyMetricsText(prediction)

        // Expand/collapse on click
        var isExpanded = false
        holder.card.setOnClickListener {
            isExpanded = !isExpanded
            holder.detailsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
        }
    }

    override fun getItemCount() = predictions.size

    fun updateData(newPredictions: MutableList<PredictionResult>) {
        predictions = newPredictions
        notifyDataSetChanged()
    }

    private fun buildConfusionMatrixText(prediction: PredictionResult): String {
        return """
            TP: ${prediction.truePositives} | FP: ${prediction.falsePositives}
            FN: ${prediction.falseNegatives} | TN: ${prediction.trueNegatives}
            Hamming Loss: ${String.format("%.3f", prediction.hammingLoss)}
            FNR: ${String.format("%.3f", prediction.falseNegativeRate)}
        """.trimIndent()
    }

    private fun buildSafetyMetricsText(prediction: PredictionResult): String {
        val parts = mutableListOf<String>()

        if (prediction.hasHallucination) {
            parts.add("⚠️ Hallucination: ${prediction.hallucinatedAllergens}")
        }

        if (prediction.hasOverPrediction) {
            parts.add("⚠️ Over-predicted: ${prediction.overPredictedAllergens}")
        }

        if (prediction.isAbstentionCase) {
            val status = if (prediction.isAbstentionCorrect) "✓" else "✗"
            parts.add("$status Abstention (predicted 'none')")
        }

        return if (parts.isEmpty()) {
            "✓ No safety issues detected"
        } else {
            parts.joinToString("\n")
        }
    }
}
