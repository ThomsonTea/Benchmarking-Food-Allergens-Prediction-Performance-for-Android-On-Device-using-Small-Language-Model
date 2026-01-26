package edu.utem.ftmk.slm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/**
 * Adapter for displaying model comparison statistics
 * UPDATED: Now includes Accuracy metric
 */
class ModelComparisonAdapter(
    private var models: MutableList<EnhancedDashboardActivity.ModelStatistics>
) : RecyclerView.Adapter<ModelComparisonAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.modelCard)
        val modelName: TextView = view.findViewById(R.id.modelNameText)
        val bestBadge: TextView = view.findViewById(R.id.bestBadge)
        val rankText: TextView = view.findViewById(R.id.rankText)
        val accuracyText: TextView = view.findViewById(R.id.accuracyText)  // ← NEW
        val f1Score: TextView = view.findViewById(R.id.f1ScoreText)
        val precision: TextView = view.findViewById(R.id.precisionText)
        val recall: TextView = view.findViewById(R.id.recallText)
        val latency: TextView = view.findViewById(R.id.latencyText)
        val predictionCount: TextView = view.findViewById(R.id.predictionCountText)
        val exactMatchRate: TextView = view.findViewById(R.id.exactMatchRateText)
        val hallucinationRate: TextView = view.findViewById(R.id.hallucinationRateText)
        val fnrText: TextView = view.findViewById(R.id.fnrText)
        val detailsContainer: View = view.findViewById(R.id.detailsContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model_comparison, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = models[position]

        // Model name and rank
        holder.modelName.text = model.modelName
        holder.rankText.text = "#${position + 1}"

        // Best badge
        if (model.isBest) {
            holder.bestBadge.visibility = View.VISIBLE
            holder.bestBadge.text = "🏆 BEST"
            holder.card.strokeWidth = 4
            holder.card.strokeColor = holder.itemView.context.getColor(R.color.gold)
        } else {
            holder.bestBadge.visibility = View.GONE
            holder.card.strokeWidth = 0
        }

        // ✅ NEW: Show Accuracy
        holder.accuracyText.text = "Accuracy: ${String.format("%.3f", model.avgAccuracy)} (${String.format("%.1f", model.avgAccuracy * 100)}%)"

        // Main metrics
        holder.f1Score.text = "F1: ${String.format("%.3f", model.avgF1)} (${String.format("%.1f", model.avgF1 * 100)}%)"
        holder.precision.text = "P: ${String.format("%.3f", model.avgPrecision)}"
        holder.recall.text = "R: ${String.format("%.3f", model.avgRecall)}"
        holder.latency.text = "⏱ ${model.avgLatency / 1000}s avg"
        holder.predictionCount.text = "${model.predictionCount} predictions"

        // Additional metrics
        holder.exactMatchRate.text = "Exact Match: ${String.format("%.1f", model.exactMatchRate * 100)}%"
        holder.hallucinationRate.text = "Hallucination: ${String.format("%.1f", model.hallucinationRate * 100)}%"
        holder.fnrText.text = "FNR: ${String.format("%.3f", model.avgFNR)} (${String.format("%.1f", model.avgFNR * 100)}%)"

        // Card color based on ranking
        val cardColor = when (position) {
            0 -> R.color.gold_light // Best
            1 -> R.color.silver_light // Second
            2 -> R.color.bronze_light // Third
            else -> R.color.white
        }
        holder.card.setCardBackgroundColor(holder.itemView.context.getColor(cardColor))

        // Accuracy color (green if > 90%, yellow if > 80%, red otherwise)
        val accuracyColor = when {
            model.avgAccuracy >= 0.90 -> R.color.success
            model.avgAccuracy >= 0.80 -> R.color.warning
            else -> R.color.danger
        }
        holder.accuracyText.setTextColor(holder.itemView.context.getColor(accuracyColor))

        // F1 score color
        val f1Color = when {
            model.avgF1 >= 0.85 -> R.color.success
            model.avgF1 >= 0.75 -> R.color.warning
            else -> R.color.danger
        }
        holder.f1Score.setTextColor(holder.itemView.context.getColor(f1Color))

        // Details (initially hidden)
        holder.detailsContainer.visibility = View.GONE

        // Expand/collapse on click
        var isExpanded = false
        holder.card.setOnClickListener {
            isExpanded = !isExpanded
            holder.detailsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
        }
    }

    override fun getItemCount() = models.size

    fun updateData(newModels: MutableList<EnhancedDashboardActivity.ModelStatistics>) {
        models = newModels
        notifyDataSetChanged()
    }
}
