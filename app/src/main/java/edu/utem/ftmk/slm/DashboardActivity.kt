package edu.utem.ftmk.slm

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Dashboard Activity showing aggregated metrics for all models
 */
class DashboardActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "Dashboard"
    }
    
    private lateinit var metricsContainer: LinearLayout
    private lateinit var exportButton: Button
    private lateinit var refreshButton: Button
    
    private val firestore = FirebaseFirestore.getInstance()
    private val resultsByModel = mutableMapOf<String, MutableList<PredictionResult>>()
    private val aggregateMetrics = mutableMapOf<String, AggregateMetrics>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        
        // Enable back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Model Performance Dashboard"
        
        initializeViews()
        loadMetrics()
    }
    
    private fun initializeViews() {
        metricsContainer = findViewById(R.id.metricsContainer)
        exportButton = findViewById(R.id.exportButton)
        refreshButton = findViewById(R.id.refreshButton)
        
        refreshButton.setOnClickListener {
            loadMetrics()
        }
        
        exportButton.setOnClickListener {
            exportToExcel()
        }
    }
    
    /**
     * Load all prediction results from Firebase
     */
    private fun loadMetrics() {
        lifecycleScope.launch {
            try {
                refreshButton.isEnabled = false
                metricsContainer.removeAllViews()
                
                // Add loading indicator
                val loadingText = TextView(this@DashboardActivity).apply {
                    text = "Loading metrics from Firebase..."
                    setPadding(16, 16, 16, 16)
                }
                metricsContainer.addView(loadingText)
                
                // Fetch from Firebase
                val results = withContext(Dispatchers.IO) {
                    fetchResultsFromFirebase()
                }
                
                // Group by model
                resultsByModel.clear()
                for (result in results) {
                    val modelName = result.modelName.ifEmpty { "Unknown" }
                    resultsByModel.getOrPut(modelName) { mutableListOf() }.add(result)
                }
                
                // Calculate aggregate metrics
                aggregateMetrics.clear()
                for ((modelName, modelResults) in resultsByModel) {
                    aggregateMetrics[modelName] = MetricsCalculator.calculateAggregateMetrics(modelResults)
                }
                
                // Display metrics
                metricsContainer.removeAllViews()
                displayMetrics()
                
                refreshButton.isEnabled = true
                exportButton.isEnabled = resultsByModel.isNotEmpty()
                
                Toast.makeText(
                    this@DashboardActivity,
                    "Loaded ${results.size} predictions for ${resultsByModel.size} models",
                    Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load metrics", e)
                Toast.makeText(
                    this@DashboardActivity,
                    "Error loading metrics: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                refreshButton.isEnabled = true
            }
        }
    }
    
    /**
     * Fetch all results from Firebase
     */
    private suspend fun fetchResultsFromFirebase(): List<PredictionResult> {
        val snapshot = firestore.collection("predictions")
            .get()
            .await()
        
        return snapshot.documents.mapNotNull { doc ->
            try {
                PredictionResult(
                    dataId = doc.getString("dataId") ?: "",
                    name = doc.getString("name") ?: "",
                    ingredients = doc.getString("ingredients") ?: "",
                    allergensRaw = doc.getString("allergensRaw") ?: "",
                    allergensMapped = doc.getString("allergensMapped") ?: "",
                    predictedAllergens = doc.getString("predictedAllergens") ?: "",
                    modelName = doc.getString("modelName") ?: "Unknown",
                    
                    // Metrics
                    truePositives = doc.getLong("truePositives")?.toInt() ?: 0,
                    falsePositives = doc.getLong("falsePositives")?.toInt() ?: 0,
                    falseNegatives = doc.getLong("falseNegatives")?.toInt() ?: 0,
                    trueNegatives = doc.getLong("trueNegatives")?.toInt() ?: 0,
                    precision = doc.getDouble("precision") ?: 0.0,
                    recall = doc.getDouble("recall") ?: 0.0,
                    f1Score = doc.getDouble("f1Score") ?: 0.0,
                    isExactMatch = doc.getBoolean("isExactMatch") ?: false,
                    hammingLoss = doc.getDouble("hammingLoss") ?: 0.0,
                    falseNegativeRate = doc.getDouble("falseNegativeRate") ?: 0.0,
                    
                    hasHallucination = doc.getBoolean("hasHallucination") ?: false,
                    hallucinatedAllergens = doc.getString("hallucinatedAllergens") ?: "",
                    hasOverPrediction = doc.getBoolean("hasOverPrediction") ?: false,
                    overPredictedAllergens = doc.getString("overPredictedAllergens") ?: "",
                    isAbstentionCase = doc.getBoolean("isAbstentionCase") ?: false,
                    isAbstentionCorrect = doc.getBoolean("isAbstentionCorrect") ?: false,
                    
                    latencyMs = doc.getLong("latencyMs") ?: 0,
                    ttftMs = doc.getLong("ttftMs") ?: -1,
                    itps = doc.getLong("itps") ?: -1,
                    otps = doc.getLong("otps") ?: -1,
                    oetMs = doc.getLong("oetMs") ?: -1,
                    totalTimeMs = doc.getLong("totalTimeMs") ?: 0,
                    
                    javaHeapKb = doc.getLong("javaHeapKb") ?: 0,
                    nativeHeapKb = doc.getLong("nativeHeapKb") ?: 0,
                    totalPssKb = doc.getLong("totalPssKb") ?: 0,
                    
                    deviceModel = doc.getString("deviceModel") ?: "",
                    androidVersion = doc.getString("androidVersion") ?: ""
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse document ${doc.id}", e)
                null
            }
        }
    }
    
    /**
     * Display metrics in UI
     */
    private fun displayMetrics() {
        for ((modelName, metrics) in aggregateMetrics) {
            val sampleCount = resultsByModel[modelName]?.size ?: 0
            
            // Create card for each model
            val card = createModelCard(modelName, metrics, sampleCount)
            metricsContainer.addView(card)
        }
        
        // Add comparison summary
        if (aggregateMetrics.size > 1) {
            val summaryCard = createSummaryCard()
            metricsContainer.addView(summaryCard)
        }
    }
    
    /**
     * Create card for single model
     */
    private fun createModelCard(
        modelName: String,
        metrics: AggregateMetrics,
        sampleCount: Int
    ): LinearLayout {
        
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundResource(R.drawable.card_background)
            
            // Model name header
            addView(TextView(this@DashboardActivity).apply {
                text = modelName
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 16)
            })
            
            // Sample count
            addView(createMetricText("Samples: $sampleCount"))
            addView(createDivider())
            
            // Quality metrics
            addView(createSectionHeader("Prediction Quality"))
            addView(createMetricText("Micro F1: ${String.format("%.4f", metrics.microF1)}"))
            addView(createMetricText("Macro F1: ${String.format("%.4f", metrics.macroF1)}"))
            addView(createMetricText("Exact Match: ${String.format("%.2f%%", metrics.exactMatchRatio * 100)}"))
            addView(createMetricText("FNR: ${String.format("%.4f", metrics.avgFnr)}"))
            addView(createDivider())
            
            // Safety metrics
            addView(createSectionHeader("Safety"))
            addView(createMetricText("Hallucination: ${String.format("%.2f%%", metrics.hallucinationRate * 100)}"))
            addView(createMetricText("Over-Prediction: ${String.format("%.2f%%", metrics.overPredictionRate * 100)}"))
            addView(createMetricText("Abstention Acc: ${String.format("%.2f%%", metrics.abstentionAccuracy * 100)}"))
            addView(createDivider())
            
            // Efficiency metrics
            addView(createSectionHeader("Efficiency"))
            addView(createMetricText("Avg Latency: ${String.format("%.1fs", metrics.avgLatency / 1000)}"))
            addView(createMetricText("Avg TTFT: ${String.format("%.1fs", metrics.avgTtft / 1000)}"))
            addView(createMetricText("Avg ITPS: ${String.format("%.1f", metrics.avgItps)} tok/s"))
            addView(createMetricText("Avg Native Heap: ${String.format("%.1f", metrics.avgNativeHeap / 1024)} MB"))
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 32)
            layoutParams = params
        }
    }
    
    /**
     * Create summary comparison card
     */
    private fun createSummaryCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundResource(R.drawable.card_background)
            
            addView(TextView(this@DashboardActivity).apply {
                text = "Best Performers"
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 16)
            })
            
            // Find best models for each metric
            val bestF1 = aggregateMetrics.maxByOrNull { it.value.microF1 }
            val bestLatency = aggregateMetrics.minByOrNull { it.value.avgLatency }
            val bestSafety = aggregateMetrics.minByOrNull { it.value.hallucinationRate }
            
            addView(createMetricText("Best F1: ${bestF1?.key ?: "N/A"}"))
            addView(createMetricText("Fastest: ${bestLatency?.key ?: "N/A"}"))
            addView(createMetricText("Safest: ${bestSafety?.key ?: "N/A"}"))
        }
    }
    
    /**
     * Helper to create section header
     */
    private fun createSectionHeader(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }
    }
    
    /**
     * Helper to create metric text
     */
    private fun createMetricText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setPadding(0, 4, 0, 4)
        }
    }
    
    /**
     * Helper to create divider
     */
    private fun createDivider(): android.view.View {
        return android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.LTGRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                setMargins(0, 16, 0, 16)
            }
        }
    }
    
    /**
     * Export results to Excel
     */
    private fun exportToExcel() {
        lifecycleScope.launch {
            try {
                exportButton.isEnabled = false
                
                val filePath = withContext(Dispatchers.IO) {
                    ExcelExporter.exportResults(
                        this@DashboardActivity,
                        resultsByModel,
                        aggregateMetrics
                    )
                }
                
                if (filePath != null) {
                    Toast.makeText(
                        this@DashboardActivity,
                        "Exported to: $filePath",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@DashboardActivity,
                        "Export failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                exportButton.isEnabled = true
                
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                Toast.makeText(
                    this@DashboardActivity,
                    "Export error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                exportButton.isEnabled = true
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
