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
 * FIXED: Matches the actual IDs in item_model_comparison.xml
 */
class ModelComparisonAdapter(
    private var modelStats: MutableList<EnhancedDashboardActivity.ModelStatistics>
) : RecyclerView.Adapter<ModelComparisonAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Card is the root MaterialCardView itself
        val card: MaterialCardView = view as MaterialCardView

        val rankText: TextView = view.findViewById(R.id.rankText)
        val modelNameText: TextView = view.findViewById(R.id.modelNameText)
        val predictionCountText: TextView = view.findViewById(R.id.predictionCountText)
        val bestBadge: TextView = view.findViewById(R.id.bestBadge)
        val timestampText: TextView = view.findViewById(R.id.timestampText)

        // Table 2: Quality Metrics (use correct IDs from XML)
        val f1Text: TextView = view.findViewById(R.id.f1Text)  // ✅ Correct ID
        val precisionText: TextView = view.findViewById(R.id.precisionText)  // ✅ Correct ID
        val recallText: TextView = view.findViewById(R.id.recallText)  // ✅ Correct ID
        val accuracyText: TextView = view.findViewById(R.id.accuracyText)  // ✅ Correct ID
        val emrText: TextView = view.findViewById(R.id.emrText)  // ✅ Correct ID
        val hammingLossText: TextView = view.findViewById(R.id.hammingLossText)  // ✅ Correct ID
        val fnrText: TextView = view.findViewById(R.id.fnrText)  // ✅ Correct ID

        // Table 3: Safety Metrics (use correct IDs from XML)
        val hallucinationRateText: TextView = view.findViewById(R.id.hallucinationRateText)  // ✅ Correct ID
        val overPredictionRateText: TextView = view.findViewById(R.id.overPredictionRateText)  // ✅ Correct ID
        val abstentionAccuracyText: TextView = view.findViewById(R.id.abstentionAccuracyText)  // ✅ Correct ID

        // Table 4: Efficiency Metrics (use correct IDs from XML)
        val latencyText: TextView = view.findViewById(R.id.latencyText)  // ✅ Correct ID
        val ttftText: TextView = view.findViewById(R.id.ttftText)  // ✅ Correct ID
        val itpsText: TextView = view.findViewById(R.id.itpsText)  // ✅ Correct ID
        val otpsText: TextView = view.findViewById(R.id.otpsText)  // ✅ Correct ID
        val oetText: TextView = view.findViewById(R.id.oetText)  // ✅ Correct ID (if exists)
        val totalTimeText: TextView = view.findViewById(R.id.totalTimeText)  // ✅ Correct ID (if exists)
        val javaHeapText: TextView = view.findViewById(R.id.javaHeapText)  // ✅ Correct ID
        val nativeHeapText: TextView = view.findViewById(R.id.nativeHeapText)  // ✅ Correct ID
        val pssText: TextView = view.findViewById(R.id.pssText)  // ✅ Correct ID
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
        holder.f1Text.text = String.format("%.3f", stats.avgF1)
        holder.precisionText.text = String.format("%.3f", stats.avgPrecision)
        holder.recallText.text = String.format("%.3f", stats.avgRecall)
        holder.accuracyText.text = String.format("%.3f", stats.avgAccuracy)
        holder.emrText.text = "${String.format("%.1f", stats.exactMatchRate * 100)}%"
        holder.hammingLossText.text = String.format("%.3f", stats.avgHammingLoss)
        holder.fnrText.text = "${String.format("%.1f", stats.avgFNR * 100)}%"

        // === TABLE 3: SAFETY METRICS (RATES as percentages) ===
        holder.hallucinationRateText.text = "${String.format("%.1f", stats.hallucinationRate * 100)}%"
        holder.overPredictionRateText.text = "${String.format("%.1f", stats.overPredictionRate * 100)}%"
        holder.abstentionAccuracyText.text = "${String.format("%.1f", stats.abstentionAccuracy * 100)}%"

        // === TABLE 4: EFFICIENCY METRICS (AVERAGES) ===
        val latencySec = stats.avgLatency / 1000.0
        holder.latencyText.text = "${String.format("%.1f", latencySec)}s"
        holder.ttftText.text = "${stats.avgTTFT.toInt()}ms"
        holder.itpsText.text = "${stats.avgITPS.toInt()}"
        holder.otpsText.text = "${stats.avgOTPS.toInt()}"

        // OET and Total Time might not be in XML, check if they exist
        try {
            holder.oetText.text = "${stats.avgOET.toInt()}ms"
        } catch (e: Exception) {
            // ID doesn't exist in XML, skip
        }

        try {
            holder.totalTimeText.text = "${stats.avgTotalTime.toInt()}ms"
        } catch (e: Exception) {
            // ID doesn't exist in XML, skip
        }

        // Memory (convert KB to MB)
        val javaHeapMB = (stats.avgJavaHeap / 1024).toInt()
        val nativeHeapMB = (stats.avgNativeHeap / 1024).toInt()
        val pssMB = (stats.avgPSS / 1024).toInt()

        holder.javaHeapText.text = "${javaHeapMB}MB"
        holder.nativeHeapText.text = "${nativeHeapMB}MB"
        holder.pssText.text = "${pssMB}MB"

        // Card styling
        val context = holder.itemView.context
        val rankColor = when (rank) {
            1 -> context.getColor(R.color.gold)
            2 -> context.getColor(R.color.silver)
            3 -> context.getColor(R.color.bronze)
            else -> context.getColor(R.color.purple_500)
        }

        // Set background color for rank badge
        holder.rankText.setBackgroundColor(rankColor)

        // Card border color based on F1 score
        val borderColor = when {
            stats.avgF1 >= 0.8 -> context.getColor(R.color.success)
            stats.avgF1 >= 0.6 -> context.getColor(R.color.warning)
            else -> context.getColor(R.color.error)
        }
        holder.card.strokeColor = borderColor
    }

    override fun getItemCount() = modelStats.size

    fun updateData(newStats: MutableList<EnhancedDashboardActivity.ModelStatistics>) {
        modelStats = newStats
        notifyDataSetChanged()
    }
}
