package edu.utem.ftmk.slm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/**
 * Complete ResultsAdapter showing ALL metrics from Table 2, 3, and 4
 * For real-time prediction display in MainActivity
 */
class ResultsAdapter : RecyclerView.Adapter<ResultsAdapter.ViewHolder>() {

    private val results = mutableListOf<PredictionResult>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view as MaterialCardView
        
        // Basic info
        val foodNameText: TextView = view.findViewById(R.id.foodNameText)
        val expectedText: TextView = view.findViewById(R.id.expectedText)
        val predictedText: TextView = view.findViewById(R.id.predictedText)
        
        // TABLE 2: Prediction Quality Metrics
        val precisionText: TextView = view.findViewById(R.id.precisionText)
        val recallText: TextView = view.findViewById(R.id.recallText)
        val f1Text: TextView = view.findViewById(R.id.f1Text)
        val accuracyText: TextView = view.findViewById(R.id.accuracyText)
        val exactMatchText: TextView = view.findViewById(R.id.exactMatchText)
        val hammingLossText: TextView = view.findViewById(R.id.hammingLossText)
        val fnrText: TextView = view.findViewById(R.id.fnrText)
        val confusionText: TextView = view.findViewById(R.id.confusionText)
        
        // TABLE 3: Safety-Oriented Metrics
        val hallucinationText: TextView = view.findViewById(R.id.hallucinationText)
        val overPredictionText: TextView = view.findViewById(R.id.overPredictionText)
        val abstentionText: TextView = view.findViewById(R.id.abstentionText)
        
        // TABLE 4: On-Device Efficiency Metrics
        val latencyText: TextView = view.findViewById(R.id.latencyText)
        val ttftText: TextView = view.findViewById(R.id.ttftText)
        val itpsText: TextView = view.findViewById(R.id.itpsText)
        val otpsText: TextView = view.findViewById(R.id.otpsText)
        val oetText: TextView = view.findViewById(R.id.oetText)
        val totalTimeText: TextView = view.findViewById(R.id.totalTimeText)
        val javaHeapText: TextView = view.findViewById(R.id.javaHeapText)
        val nativeHeapText: TextView = view.findViewById(R.id.nativeHeapText)
        val pssText: TextView = view.findViewById(R.id.pssText)
        
        val statusText: TextView = view.findViewById(R.id.statusText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = results[position]

        // Basic info
        holder.foodNameText.text = result.name
        holder.expectedText.text = result.allergensMapped
        holder.predictedText.text = result.predictedAllergens
        
        // === TABLE 2: PREDICTION QUALITY METRICS ===
        holder.precisionText.text = "P: ${String.format("%.3f", result.precision)}"
        holder.recallText.text = "R: ${String.format("%.3f", result.recall)}"
        holder.f1Text.text = "F1: ${String.format("%.3f", result.f1Score)}"
        holder.accuracyText.text = "Acc: ${String.format("%.3f", result.accuracy)}"
        
        // Exact Match
        holder.exactMatchText.text = if (result.isExactMatch) "EM: Yes" else "EM: No"
        
        // Hamming Loss
        holder.hammingLossText.text = "HL: ${String.format("%.3f", result.hammingLoss)}"
        
        // False Negative Rate
        holder.fnrText.text = "FNR: ${String.format("%.3f", result.falseNegativeRate)}"
        
        // Confusion Matrix (compact format)
        holder.confusionText.text = "TP:${result.truePositives} FP:${result.falsePositives} FN:${result.falseNegatives} TN:${result.trueNegatives}"
        
        // === TABLE 3: SAFETY-ORIENTED METRICS ===
        
        // Hallucination
        holder.hallucinationText.text = if (result.hasHallucination) {
            "Hallucination: Yes (${result.hallucinatedAllergens})"
        } else {
            "Hallucination: No"
        }
        
        // Over-Prediction
        holder.overPredictionText.text = if (result.hasOverPrediction) {
            "Over-Prediction: Yes (${result.overPredictedAllergens})"
        } else {
            "Over-Prediction: No"
        }
        
        // Abstention (for no-allergen cases)
        holder.abstentionText.text = when {
            result.isAbstentionCase && result.isAbstentionCorrect -> "Abstention: ✓ Correct"
            result.isAbstentionCase && !result.isAbstentionCorrect -> "Abstention: ✗ Wrong"
            else -> "Abstention: N/A"
        }
        
        // === TABLE 4: ON-DEVICE EFFICIENCY METRICS ===
        
        // Latency (convert ms to seconds)
        val latencySec = result.latencyMs / 1000.0
        holder.latencyText.text = "Latency: ${String.format("%.1f", latencySec)}s"
        
        // TTFT (Time to First Token)
        holder.ttftText.text = "TTFT: ${result.ttftMs}ms"
        
        // ITPS (Input Tokens Per Second)
        holder.itpsText.text = "ITPS: ${result.itps}"
        
        // OTPS (Output Tokens Per Second)
        holder.otpsText.text = "OTPS: ${result.otps}"
        
        // OET (Output Evaluation Time)
        holder.oetText.text = "OET: ${result.oetMs}ms"
        
        // Total Time
        holder.totalTimeText.text = "Total: ${result.totalTimeMs}ms"
        
        // Memory Metrics (convert KB to MB)
        val javaHeapMB = result.javaHeapKb / 1024
        val nativeHeapMB = result.nativeHeapKb / 1024
        val pssMB = result.totalPssKb / 1024
        
        holder.javaHeapText.text = "Java: ${javaHeapMB}MB"
        holder.nativeHeapText.text = "Native: ${nativeHeapMB}MB"
        holder.pssText.text = "PSS: ${pssMB}MB"
        
        // Status
        holder.statusText.text = "✓ Saved to Firebase"
        
        // Color-code card border based on F1 score
        val context = holder.itemView.context
        val strokeColor = when {
            result.f1Score >= 0.9 -> context.getColor(R.color.success)
            result.f1Score >= 0.7 -> context.getColor(R.color.warning)
            else -> context.getColor(R.color.error)
        }
        holder.card.strokeColor = strokeColor
    }

    override fun getItemCount() = results.size

    fun clearResults() {
        results.clear()
        notifyDataSetChanged()
    }

    fun addResult(result: PredictionResult) {
        results.add(result)
        notifyItemInserted(results.size - 1)
    }
}
