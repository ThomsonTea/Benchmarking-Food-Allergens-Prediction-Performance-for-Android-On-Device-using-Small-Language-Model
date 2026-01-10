package edu.utem.ftmk.slm

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/**
 * Adapter for displaying prediction results with full metrics
 */
class ResultsAdapter : RecyclerView.Adapter<ResultsAdapter.ResultViewHolder>() {

    private val results = mutableListOf<PredictionResult>()

    class ResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view as MaterialCardView
        val foodNameText: TextView = view.findViewById(R.id.foodNameText)
        val ingredientsText: TextView = view.findViewById(R.id.ingredientsText)
        val expectedText: TextView = view.findViewById(R.id.expectedText)
        val predictedText: TextView = view.findViewById(R.id.predictedText)
        val latencyText: TextView = view.findViewById(R.id.latencyText)
        val ttftText: TextView = view.findViewById(R.id.ttftText)
        val itpsText: TextView = view.findViewById(R.id.itpsText)
        val otpsText: TextView = view.findViewById(R.id.otpsText)
        val oetText: TextView = view.findViewById(R.id.oetText)
        val memoryText: TextView = view.findViewById(R.id.memoryText)
        val statusText: TextView = view.findViewById(R.id.statusText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val result = results[position]

        // Food information
        holder.foodNameText.text = "${result.dataId}. ${result.name}"
        holder.ingredientsText.text = "Ingredients: ${result.ingredients}"

        // Expected allergens
        holder.expectedText.text = result.allergensMapped.ifEmpty { "none" }

        // Predicted allergens (color coded)
        val predicted = result.predictedAllergens.ifEmpty { "none" }
        holder.predictedText.text = predicted

        // Color code based on match
        val isMatch = normalizeAllergens(result.allergensMapped) ==
                normalizeAllergens(result.predictedAllergens)

        if (isMatch) {
            holder.predictedText.setTextColor(Color.parseColor("#4CAF50")) // Green
            holder.cardView.strokeColor = Color.parseColor("#4CAF50")
        } else {
            holder.predictedText.setTextColor(Color.parseColor("#F44336")) // Red
            holder.cardView.strokeColor = Color.parseColor("#F44336")
        }

        // Performance metrics
        val latencySeconds = result.latencyMs / 1000.0
        holder.latencyText.text = "Latency: ${String.format("%.1f", latencySeconds)}s"

        holder.ttftText.text = if (result.ttftMs > 0) {
            "TTFT: ${result.ttftMs}ms"
        } else {
            "TTFT: N/A"
        }

        holder.itpsText.text = if (result.itps > 0) {
            "ITPS: ${result.itps} tok/s"
        } else {
            "ITPS: N/A"
        }

        holder.otpsText.text = if (result.otps >= 0) {
            "OTPS: ${result.otps} tok/s"
        } else {
            "OTPS: N/A"
        }

        holder.oetText.text = if (result.oetMs > 0) {
            "OET: ${result.oetMs}ms"
        } else {
            "OET: N/A"
        }

        // Memory (convert KB to MB)
        val memoryMb = result.nativeHeapKb / 1024.0
        holder.memoryText.text = "Memory: ${String.format("%.1f", memoryMb)}MB"

        // Status
        holder.statusText.text = "✓ Saved to Firebase"
        holder.statusText.setTextColor(Color.parseColor("#4CAF50"))
    }

    override fun getItemCount() = results.size

    fun addResult(result: PredictionResult) {
        results.add(result)
        notifyItemInserted(results.size - 1)
    }

    fun clearResults() {
        results.clear()
        notifyDataSetChanged()
    }

    /**
     * Normalize allergen strings for comparison
     */
    private fun normalizeAllergens(allergens: String): Set<String> {
        return allergens.lowercase()
            .split(",", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "none" }
            .toSet()
    }
}
