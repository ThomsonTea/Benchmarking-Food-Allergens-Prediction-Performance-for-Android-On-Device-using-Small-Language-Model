package edu.utem.ftmk.slm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying aggregated model statistics in the dashboard
 * Shows ALL metrics from Table 2, 3, and 4 as averages/rates
 */
class ModelComparisonAdapter(
    private var modelStats: MutableList<EnhancedDashboardActivity.ModelStatistics>
) : RecyclerView.Adapter<ModelComparisonAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.modelCard)
        val rankText: TextView = view.findViewById(R.id.rankText)
        val modelNameText: TextView = view.findViewById(R.id.modelNameText)
        val predictionCountText: TextView = view.findViewById(R.id.predictionCountText)
        val bestBadge: TextView = view.findViewById(R.id.bestBadge)
        val timestampText: TextView = view.findViewById(R.id.timestampText)

        // Table 2: Quality Metrics (Averages)
        val accuracyText: TextView = view.findViewById(R.id.accuracyText)
        val f1ScoreText: TextView = view.findViewById(R.id.f1ScoreText)
        val precisionText: TextView = view.findViewById(R.id.precisionText)
        val recallText: TextView = view.findViewById(R.id.recallText)
        val exactMatchRateText: TextView = view.findViewById(R.id.exactMatchRateText)
        val hammingLossText: TextView = view.findViewById(R.id.hammingLossText)
        val fnrText: TextView = view.findViewById(R.id.fnrText)

        // Table 3: Safety Metrics (Rates)
        val hallucinationRateText: TextView = view.findViewById(R.id.hallucinationRateText)
        val overPredictionRateText: TextView = view.findViewById(R.id.overPredictionRateText)
        val abstentionAccText: TextView = view.findViewById(R.id.abstentionAccText)

        // Table 4: Efficiency Metrics (Averages)
        val latencyText: TextView = view.findViewById(R.id.latencyText)
        val ttftText: TextView = view.findViewById(R.id.ttftText)
        val itpsText: TextView = view.findViewById(R.id.itpsText)
        val otpsText: TextView = view.findViewById(R.id.otpsText)
        val oetText: TextView = view.findViewById(R.id.oetText)
        val totalTimeText: TextView = view.findViewById(R.id.totalTimeText)
        val javaHeapText: TextView = view.findViewById(R.id.javaHeapText)
        val nativeHeapText: TextView = view.findViewById(R.id.nativeHeapText)
        val pssText: TextView = view.findViewById(R.id.pssText)

        val detailsContainer: View = view.findViewById(R.id.detailsContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model_comparison, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stats = modelStats[position]
        val rank = position + 1

        // Header
        holder.rankText.text = "#$rank"
        holder.modelNameText.text = stats.modelName
        holder.predictionCountText.text = "${stats.predictionCount} predictions"

        // Best badge
        holder.bestBadge.visibility = if (stats.isBest) View.VISIBLE else View.GONE

        // Timestamp
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        holder.timestampText.text = "Updated: ${sdf.format(Date())}"

        // === TABLE 2: QUALITY METRICS (AVERAGES) ===
        holder.accuracyText.text = String.format("%.3f", stats.avgAccuracy)
        holder.f1ScoreText.text = String.format("%.3f", stats.avgF1)
        holder.precisionText.text = "P: ${String.format("%.3f", stats.avgPrecision)}"
        holder.recallText.text = "R: ${String.format("%.3f", stats.avgRecall)}"
        holder.exactMatchRateText.text = "EMR: ${String.format("%.1f", stats.exactMatchRate * 100)}%"
        holder.hammingLossText.text = "HL: ${String.format("%.3f", stats.avgHammingLoss)}"
        holder.fnrText.text = "FNR: ${String.format("%.1f", stats.avgFNR * 100)}%"

        // === TABLE 3: SAFETY METRICS (RATES as percentages) ===
        holder.hallucinationRateText.text = "Hallucination: ${String.format("%.1f", stats.hallucinationRate * 100)}%"
        holder.overPredictionRateText.text = "Over-Pred: ${String.format("%.1f", stats.overPredictionRate * 100)}%"
        holder.abstentionAccText.text = "Abst: ${String.format("%.1f", stats.abstentionAccuracy * 100)}%"

        // === TABLE 4: EFFICIENCY METRICS (AVERAGES) ===
        val latencySec = stats.avgLatency / 1000.0
        holder.latencyText.text = "⏱ ${String.format("%.1f", latencySec)}s avg"
        holder.ttftText.text = "TTFT: ${stats.avgTTFT.toInt()}ms"
        holder.itpsText.text = "ITPS: ${stats.avgITPS.toInt()}"
        holder.otpsText.text = "OTPS: ${stats.avgOTPS.toInt()}"
        holder.oetText.text = "OET: ${stats.avgOET.toInt()}ms"
        holder.totalTimeText.text = "Total: ${stats.avgTotalTime.toInt()}ms"

        // Memory (convert KB to MB)
        val javaHeapMB = (stats.avgJavaHeap / 1024).toInt()
        val nativeHeapMB = (stats.avgNativeHeap / 1024).toInt()
        val pssMB = (stats.avgPSS / 1024).toInt()

        holder.javaHeapText.text = "Java: ${javaHeapMB}MB"
        holder.nativeHeapText.text = "Native: ${nativeHeapMB}MB"
        holder.pssText.text = "PSS: ${pssMB}MB"

        // Card styling
        val context = holder.itemView.context
        val rankColor = when (rank) {
            1 -> context.getColor(R.color.gold)
            2 -> context.getColor(R.color.silver)
            3 -> context.getColor(R.color.bronze)
            else -> context.getColor(R.color.purple_500)
        }
        holder.rankText.setBackgroundColor(rankColor)

        // Card border color based on F1 score
        val borderColor = when {
            stats.avgF1 >= 0.8 -> context.getColor(R.color.success)
            stats.avgF1 >= 0.6 -> context.getColor(R.color.warning)
            else -> context.getColor(R.color.error)
        }
        holder.card.strokeColor = borderColor

        // Details container (optional expandable section)
        holder.detailsContainer.visibility = View.GONE

        // Optional: Add click listener to expand details
        var isExpanded = false
        holder.card.setOnClickListener {
            isExpanded = !isExpanded
            holder.detailsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
        }
    }

    override fun getItemCount() = modelStats.size

    fun updateData(newStats: MutableList<EnhancedDashboardActivity.ModelStatistics>) {
        modelStats = newStats
        notifyDataSetChanged()
    }
}
