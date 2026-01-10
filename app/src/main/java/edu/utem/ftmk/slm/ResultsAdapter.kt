package edu.utem.ftmk.slm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ResultsAdapter : RecyclerView.Adapter<ResultsAdapter.ResultViewHolder>() {

    private val results = mutableListOf<PredictionResult>()

    fun addResult(result: PredictionResult) {
        results.add(result)
        notifyItemInserted(results.size - 1)
    }

    fun clearResults() {
        results.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(results[position])
    }

    override fun getItemCount() = results.size

    class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val foodNameText: TextView = itemView.findViewById(R.id.foodNameText)
        private val ingredientsText: TextView = itemView.findViewById(R.id.ingredientsText)
        private val expectedText: TextView = itemView.findViewById(R.id.expectedText)
        private val predictedText: TextView = itemView.findViewById(R.id.predictedText)
        private val metricsText: TextView = itemView.findViewById(R.id.metricsText)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)

        fun bind(result: PredictionResult) {
            foodNameText.text = "${result.dataId}. ${result.name}"
            ingredientsText.text = "Ingredients: ${result.ingredients}"
            
            val expected = if (result.allergensMapped.isEmpty() || result.allergensMapped == "none") {
                "none"
            } else {
                result.allergensMapped
            }
            expectedText.text = expected
            
            val predicted = if (result.predictedAllergens.isEmpty() || result.predictedAllergens.contains("none")) {
                "none"
            } else {
                result.predictedAllergens
            }
            predictedText.text = predicted
            
            // Set color based on match
            val isMatch = compareAllergens(expected, predicted)
            predictedText.setTextColor(
                itemView.context.getColor(
                    if (isMatch) android.R.color.holo_green_dark 
                    else android.R.color.holo_red_dark
                )
            )
            
            // Format metrics
            val latencySec = result.latencyMs / 1000.0
            val memoryMB = result.nativeHeapKb / 1024.0
            metricsText.text = String.format(
                "Latency: %.1fs | Memory: %.1fMB",
                latencySec,
                memoryMB
            )
            
            statusText.text = "✓ Saved to Firebase"
            statusText.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
        }
        
        private fun compareAllergens(expected: String, predicted: String): Boolean {
            val exp = expected.lowercase().trim().split(",").map { it.trim() }.sorted()
            val pred = predicted.lowercase().trim().split(",").map { it.trim() }.sorted()
            return exp == pred
        }
    }
}
