package edu.utem.ftmk.slm

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

/**
 * Enhanced Dashboard with ALL metrics from Table 2, 3, and 4
 * Aggregates and compares model performance
 */
class EnhancedDashboardActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ENHANCED_DASHBOARD"
    }

    // Firebase
    private lateinit var firestore: FirebaseFirestore
    
    // UI Components
    private lateinit var progressBar: ProgressBar
   // private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
   // private lateinit var adapter: ModelComparisonAdapter
    
    // Best Model Card
    private lateinit var bestModelCard: MaterialCardView
    private lateinit var bestModelNameText: TextView
    private lateinit var bestModelF1Text: TextView
    private lateinit var bestModelCountText: TextView
    
    // Overall Stats Card
    private lateinit var overallStatsCard: MaterialCardView
    private lateinit var totalPredictionsText: TextView
    private lateinit var totalModelsText: TextView
    private lateinit var avgF1AllText: TextView
    private lateinit var bestF1AllText: TextView
    
    // Champion Models
    private lateinit var bestAccuracyModelText: TextView
    private lateinit var bestAccuracyScoreText: TextView
    private lateinit var bestModelText: TextView
    private lateinit var bestF1ScoreText: TextView
    private lateinit var fastestModelText: TextView
    private lateinit var fastestLatencyText: TextView

    // Data
    private val modelStats = mutableListOf<ModelStatistics>()
    private var allPredictions = listOf<PredictionResult>()

    /**
     * Complete ModelStatistics with ALL metrics from Table 2, 3, and 4
     */
    data class ModelStatistics(
        val modelName: String,
        val predictionCount: Int,
        
        // TABLE 2: Prediction Quality Metrics (AVERAGED)
        val avgPrecision: Double,
        val avgRecall: Double,
        val avgF1: Double,
        val avgAccuracy: Double,
        val exactMatchRate: Double,      // % of exact matches
        val avgHammingLoss: Double,
        val avgFNR: Double,
        
        // TABLE 3: Safety-Oriented Metrics (RATES)
        val hallucinationRate: Double,   // % with hallucinations
        val overPredictionRate: Double,   // % with over-predictions
        val abstentionAccuracy: Double,   // % correct abstentions
        
        // TABLE 4: On-Device Efficiency Metrics (AVERAGED)
        val avgLatency: Double,
        val avgTTFT: Double,
        val avgITPS: Double,
        val avgOTPS: Double,
        val avgOET: Double,
        val avgTotalTime: Double,
        val avgJavaHeap: Double,
        val avgNativeHeap: Double,
        val avgPSS: Double,
        
        // Display flag
        val isBest: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enhanced_dashboard)

        firestore = FirebaseFirestore.getInstance()

        initializeViews()
        //setupRecyclerView()
        loadAllPredictions()
    }

    private fun initializeViews() {
        // Main views
        progressBar = findViewById(R.id.dashboardProgressBar)
        //recyclerView = findViewById(R.id.modelComparisonRecyclerView)
        emptyText = findViewById(R.id.emptyText)
        
        // Best Model Card
        bestModelCard = findViewById(R.id.bestModelCard)
        bestModelNameText = findViewById(R.id.bestModelNameText)
        bestModelF1Text = findViewById(R.id.bestModelF1Text)
        bestModelCountText = findViewById(R.id.bestModelCountText)
        
        // Overall Stats Card
        overallStatsCard = findViewById(R.id.overallStatsCard)
        totalPredictionsText = findViewById(R.id.totalPredictionsText)
        totalModelsText = findViewById(R.id.totalModelsText)
        avgF1AllText = findViewById(R.id.avgF1AllText)
        bestF1AllText = findViewById(R.id.bestF1AllText)
        
        // Champion Models
        bestAccuracyModelText = findViewById(R.id.bestAccuracyModelText)
        bestAccuracyScoreText = findViewById(R.id.bestAccuracyScoreText)
        bestModelText = findViewById(R.id.bestModelText)
        bestF1ScoreText = findViewById(R.id.bestF1ScoreText)
        fastestModelText = findViewById(R.id.fastestModelText)
        fastestLatencyText = findViewById(R.id.fastestLatencyText)

        // Back button
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // Export button
        findViewById<Button>(R.id.exportButton).setOnClickListener {
            exportToExcel()
        }

        // View history button
        findViewById<Button>(R.id.viewHistoryButton).setOnClickListener {
            startActivity(Intent(this, PredictionHistoryActivity::class.java))
        }
    }

   /* private fun setupRecyclerView() {
        adapter = ModelComparisonAdapter(modelStats)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }*/

    private fun loadAllPredictions() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
               // recyclerView.visibility = View.GONE
                emptyText.visibility = View.GONE
                bestModelCard.visibility = View.GONE
                overallStatsCard.visibility = View.GONE

                Log.i(TAG, "Loading all predictions from Firebase...")

                // Load predictions from Firebase
                allPredictions = withContext(Dispatchers.IO) {
                    val snapshot = firestore.collection("predictions")
                        .get()
                        .await()

                    // Map documents to PredictionResult objects
                    snapshot.documents.mapNotNull { doc ->
                        try {
                            PredictionResult(
                                dataId = doc.getString("dataId") ?: "",
                                name = doc.getString("name") ?: "",
                                ingredients = doc.getString("ingredients") ?: "",
                                allergensRaw = doc.getString("allergensRaw") ?: "",
                                allergensMapped = doc.getString("allergensMapped") ?: "",
                                predictedAllergens = doc.getString("predictedAllergens") ?: "",
                                modelName = doc.getString("modelName") ?: "Unknown",

                                truePositives = doc.getLong("truePositives")?.toInt() ?: 0,
                                falsePositives = doc.getLong("falsePositives")?.toInt() ?: 0,
                                falseNegatives = doc.getLong("falseNegatives")?.toInt() ?: 0,
                                trueNegatives = doc.getLong("trueNegatives")?.toInt() ?: 0,
                                precision = doc.getDouble("precision") ?: 0.0,
                                recall = doc.getDouble("recall") ?: 0.0,
                                f1Score = doc.getDouble("f1Score") ?: 0.0,
                                accuracy = doc.getDouble("accuracy") ?: 0.0,
                                isExactMatch = doc.getBoolean("isExactMatch") ?: false,
                                hammingLoss = doc.getDouble("hammingLoss") ?: 0.0,
                                falseNegativeRate = doc.getDouble("falseNegativeRate") ?: 0.0,

                                hallucinationCount = doc.getLong("hallucinationCount")?.toInt() ?: 0,
                                hallucinatedAllergens = doc.getString("hallucinatedAllergens") ?: "",
                                overPredictionCount = doc.getLong("overPredictionCount")?.toInt() ?: 0,
                                overPredictedAllergens = doc.getString("overPredictedAllergens") ?: "",
                                isAbstentionCase = doc.getBoolean("isAbstentionCase") ?: false,
                                isAbstentionCorrect = doc.getBoolean("isAbstentionCorrect") ?: false,

                                latencyMs = doc.getLong("latencyMs") ?: 0L,
                                ttftMs = doc.getLong("ttftMs") ?: 0L,
                                itps = doc.getLong("itps") ?: 0L,
                                otps = doc.getLong("otps") ?: 0L,
                                oetMs = doc.getLong("oetMs") ?: 0L,
                                totalTimeMs = doc.getLong("totalTimeMs") ?: 0L,

                                javaHeapKb = doc.getLong("javaHeapKb") ?: 0L,
                                nativeHeapKb = doc.getLong("nativeHeapKb") ?: 0L,
                                totalPssKb = doc.getLong("totalPssKb") ?: 0L,

                                deviceModel = doc.getString("deviceModel") ?: "",
                                androidVersion = doc.getString("androidVersion") ?: "",
                                
                                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing document: ${e.message}")
                            null
                        }
                    }
                }
                allPredictions.forEach { pred ->
                    Log.d("DASHBOARD_LOAD", """
        ${pred.name}: F1=${pred.f1Score}, P=${pred.precision}, R=${pred.recall}
    """.trimIndent())
                }

                Log.i(TAG, "✓ Loaded ${allPredictions.size} predictions")

                if (allPredictions.isEmpty()) {
                    progressBar.visibility = View.GONE
                    emptyText.visibility = View.VISIBLE
                    return@launch
                }

                // Calculate statistics by model
                calculateModelStatistics()

                // Display overall stats
                displayOverallStats()

                // Display best model
                displayBestModel()

                // Display champion models
                displayChampionModels()

                progressBar.visibility = View.GONE
              //  recyclerView.visibility = View.VISIBLE
                bestModelCard.visibility = View.VISIBLE
                overallStatsCard.visibility = View.VISIBLE

            } catch (e: Exception) {
                Log.e(TAG, "Error loading predictions", e)
                progressBar.visibility = View.GONE
                emptyText.text = "Error loading data: ${e.message}"
                emptyText.visibility = View.VISIBLE
                Toast.makeText(this@EnhancedDashboardActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun calculateModelStatistics() {
        // Group by model
        val byModel = allPredictions.groupBy { it.modelName }

        modelStats.clear()

        // Calculate stats for each model
        for ((modelName, preds) in byModel) {
            
            // TABLE 2: Quality Metrics (AVERAGES)
            val avgPrecision = preds.map { it.precision }.average()
            val avgRecall = preds.map { it.recall }.average()
            val avgF1 = preds.map { it.f1Score }.average()
            val avgAccuracy = preds.map { it.accuracy }.average()
            val avgHammingLoss = preds.map { it.hammingLoss }.average()
            val avgFNR = preds.map { it.falseNegativeRate }.average()
            val exactMatchRate = preds.count { it.isExactMatch }.toDouble() / preds.size
            
            // TABLE 3: Safety Metrics (RATES as percentages)
            val hallucinationRate = preds.count { it.hallucinationCount > 0 }.toDouble() / preds.size
            val overPredictionRate = preds.count { it.overPredictionCount > 0 }.toDouble() / preds.size
            
            val abstentionCases = preds.filter { it.isAbstentionCase }
            val abstentionAccuracy = if (abstentionCases.isNotEmpty()) {
                abstentionCases.count { it.isAbstentionCorrect }.toDouble() / abstentionCases.size
            } else {
                0.0
            }
            
            // TABLE 4: Efficiency Metrics (AVERAGES)
            val avgLatency = preds.map { it.latencyMs.toDouble() }.average()
            val avgTTFT = preds.map { it.ttftMs.toDouble() }.average()
            val avgITPS = preds.map { it.itps.toDouble() }.average()
            val avgOTPS = preds.map { it.otps.toDouble() }.average()
            val avgOET = preds.map { it.oetMs.toDouble() }.average()
            val avgTotalTime = preds.map { it.totalTimeMs.toDouble() }.average()
            val avgJavaHeap = preds.map { it.javaHeapKb.toDouble() }.average()
            val avgNativeHeap = preds.map { it.nativeHeapKb.toDouble() }.average()
            val avgPSS = preds.map { it.totalPssKb.toDouble() }.average()

            for ((modelName, stats) in modelStats.withIndex()) {
                Log.d("DASHBOARD_STATS", """
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        Model: ${stats.modelName}
        Count: ${stats.predictionCount}
        Avg F1: ${stats.avgF1}
        Avg Precision: ${stats.avgPrecision}
        Avg Recall: ${stats.avgRecall}
        Avg Accuracy: ${stats.avgAccuracy}
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    """.trimIndent())
            }

            modelStats.add(
                ModelStatistics(
                    modelName = modelName,
                    predictionCount = preds.size,
                    
                    // Table 2
                    avgPrecision = avgPrecision,
                    avgRecall = avgRecall,
                    avgF1 = avgF1,
                    avgAccuracy = avgAccuracy,
                    exactMatchRate = exactMatchRate,
                    avgHammingLoss = avgHammingLoss,
                    avgFNR = avgFNR,
                    
                    // Table 3
                    hallucinationRate = hallucinationRate,
                    overPredictionRate = overPredictionRate,
                    abstentionAccuracy = abstentionAccuracy,
                    
                    // Table 4
                    avgLatency = avgLatency,
                    avgTTFT = avgTTFT,
                    avgITPS = avgITPS,
                    avgOTPS = avgOTPS,
                    avgOET = avgOET,
                    avgTotalTime = avgTotalTime,
                    avgJavaHeap = avgJavaHeap,
                    avgNativeHeap = avgNativeHeap,
                    avgPSS = avgPSS,
                    
                    isBest = false
                )
            )
        }

        // Sort by F1 score (descending)
        modelStats.sortByDescending { it.avgF1 }

        // Mark best model
        if (modelStats.isNotEmpty()) {
            modelStats[0] = modelStats[0].copy(isBest = true)
        }

        // Update adapter
        build3SeparateTables()
    }

    private fun displayOverallStats() {
        totalPredictionsText.text = "${allPredictions.size} predictions"
        totalModelsText.text = "${modelStats.size} models tested"

        val overallAvgF1 = allPredictions.map { it.f1Score }.average()
        avgF1AllText.text = "Avg F1: ${String.format("%.3f", overallAvgF1)}"

        val bestF1 = modelStats.maxByOrNull { it.avgF1 }?.avgF1 ?: 0.0
        bestF1AllText.text = "Best F1: ${String.format("%.3f", bestF1)}"
    }

    // Add these methods to EnhancedDashboardActivity.kt

    private fun build3SeparateTables() {
        buildQualityMetricsTable()
        buildSafetyMetricsTable()
        buildEfficiencyMetricsTable()
    }

    // ============================================
// TABLE 1: PREDICTION QUALITY METRICS (Table 2)
// ============================================
    private fun buildQualityMetricsTable() {
        val container = findViewById<LinearLayout>(R.id.qualityTableContainer)
        container.removeAllViews()

        if (modelStats.isEmpty()) return

        // Header Row
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(getColor(R.color.quality_header))  // Purple
            setPadding(0, 12, 0, 12)
        }

        headerRow.addView(createHeaderCell("Model Name", 200))
        headerRow.addView(createDivider())
        headerRow.addView(createHeaderCell("F1 Score", 90))
        headerRow.addView(createHeaderCell("Precision", 90))
        headerRow.addView(createHeaderCell("Recall", 90))
        headerRow.addView(createHeaderCell("Accuracy", 90))
        headerRow.addView(createHeaderCell("EMR", 80))
        headerRow.addView(createHeaderCell("Hamming\nLoss", 90))
        headerRow.addView(createHeaderCell("FNR", 80))

        container.addView(headerRow)
        container.addView(createHorizontalDivider())

        // Data Rows
        for ((index, model) in modelStats.withIndex()) {
            val dataRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val bgColor = if (index % 2 == 0) R.color.row_even else R.color.row_odd
                setBackgroundColor(getColor(bgColor))
                setPadding(0, 16, 0, 16)
            }

            // Model Name
            dataRow.addView(createModelNameCell(model.modelName, 200))
            dataRow.addView(createDivider())

            // Quality Metrics
            dataRow.addView(createDataCell(String.format("%.3f", model.avgF1), 90, "#6200EA", bold = true))
            dataRow.addView(createDataCell(String.format("%.3f", model.avgPrecision), 90))
            dataRow.addView(createDataCell(String.format("%.3f", model.avgRecall), 90))
            dataRow.addView(createDataCell(String.format("%.3f", model.avgAccuracy), 90))
            dataRow.addView(createDataCell("${(model.exactMatchRate * 100).toInt()}%", 80))
            dataRow.addView(createDataCell(String.format("%.3f", model.avgHammingLoss), 90))
            dataRow.addView(createDataCell("${(model.avgFNR * 100).toInt()}%", 80, "#F44336"))

            container.addView(dataRow)
            container.addView(createHorizontalDivider())
        }
    }

    // ============================================
// TABLE 2: SAFETY METRICS (Table 3)
// ============================================
    private fun buildSafetyMetricsTable() {
        val container = findViewById<LinearLayout>(R.id.safetyTableContainer)
        container.removeAllViews()

        if (modelStats.isEmpty()) return

        // Header Row
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(getColor(R.color.safety_header))  // Orange
            setPadding(0, 12, 0, 12)
        }

        headerRow.addView(createHeaderCell("Model Name", 200))
        headerRow.addView(createDivider())
        headerRow.addView(createHeaderCell("Hallucination\nRate", 120))
        headerRow.addView(createHeaderCell("Over-Prediction\nRate", 130))
        headerRow.addView(createHeaderCell("Abstention\nAccuracy", 120))

        container.addView(headerRow)
        container.addView(createHorizontalDivider())

        // Data Rows
        for ((index, model) in modelStats.withIndex()) {
            val dataRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val bgColor = if (index % 2 == 0) R.color.row_even else R.color.row_odd
                setBackgroundColor(getColor(bgColor))
                setPadding(0, 16, 0, 16)
            }

            // Model Name
            dataRow.addView(createModelNameCell(model.modelName, 200))
            dataRow.addView(createDivider())

            // Safety Metrics
            dataRow.addView(createDataCell("${(model.hallucinationRate * 100).toInt()}%", 120, "#F44336", bold = true))
            dataRow.addView(createDataCell("${(model.overPredictionRate * 100).toInt()}%", 130, "#FF9800", bold = true))
            dataRow.addView(createDataCell("${(model.abstentionAccuracy * 100).toInt()}%", 120, "#4CAF50", bold = true))

            container.addView(dataRow)
            container.addView(createHorizontalDivider())
        }
    }

    // ============================================
// TABLE 3: EFFICIENCY METRICS (Table 4)
// ============================================
    private fun buildEfficiencyMetricsTable() {
        val container = findViewById<LinearLayout>(R.id.efficiencyTableContainer)
        container.removeAllViews()

        if (modelStats.isEmpty()) return

        // Header Row
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(getColor(R.color.efficiency_header))  // Blue
            setPadding(0, 12, 0, 12)
        }

        headerRow.addView(createHeaderCell("Model Name", 200))
        headerRow.addView(createDivider())
        headerRow.addView(createHeaderCell("Avg\nLatency", 90))
        headerRow.addView(createHeaderCell("TTFT", 90))
        headerRow.addView(createHeaderCell("ITPS", 80))
        headerRow.addView(createHeaderCell("OTPS", 80))
        headerRow.addView(createHeaderCell("OET", 90))
        headerRow.addView(createHeaderCell("Total\nTime", 90))
        headerRow.addView(createHeaderCell("Java\nHeap", 90))
        headerRow.addView(createHeaderCell("Native\nHeap", 90))
        headerRow.addView(createHeaderCell("PSS", 90))

        container.addView(headerRow)
        container.addView(createHorizontalDivider())

        // Data Rows
        for ((index, model) in modelStats.withIndex()) {
            val dataRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val bgColor = if (index % 2 == 0) R.color.row_even else R.color.row_odd
                setBackgroundColor(getColor(bgColor))
                setPadding(0, 16, 0, 16)
            }

            // Model Name
            dataRow.addView(createModelNameCell(model.modelName, 200))
            dataRow.addView(createDivider())

            // Efficiency Metrics
            dataRow.addView(createDataCell("${(model.avgLatency / 1000).toInt()}s", 90, "#2196F3", bold = true))
            dataRow.addView(createDataCell("${model.avgTTFT.toInt()}ms", 90))
            dataRow.addView(createDataCell("${model.avgITPS.toInt()}", 80))
            dataRow.addView(createDataCell("${model.avgOTPS.toInt()}", 80))
            dataRow.addView(createDataCell("${model.avgOET.toInt()}ms", 90))
            dataRow.addView(createDataCell("${model.avgTotalTime.toInt()}ms", 90))

            val javaMB = (model.avgJavaHeap / 1024).toInt()
            val nativeMB = (model.avgNativeHeap / 1024).toInt()
            val pssMB = (model.avgPSS / 1024).toInt()

            dataRow.addView(createDataCell("${javaMB}MB", 90))
            dataRow.addView(createDataCell("${nativeMB}MB", 90))
            dataRow.addView(createDataCell("${pssMB}MB", 90))

            container.addView(dataRow)
            container.addView(createHorizontalDivider())
        }
    }

// ============================================
// HELPER FUNCTIONS
// ============================================

    private fun createHeaderCell(text: String, widthDp: Int): TextView {
        return TextView(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(dpToPx(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(12, 0, 12, 0)
            textSize = 13f
            setTextColor(getColor(R.color.white))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
    }

    private fun createModelNameCell(text: String, widthDp: Int): TextView {
        return TextView(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(dpToPx(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(12, 0, 12, 0)
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
        }
    }

    private fun createDataCell(
        text: String,
        widthDp: Int,
        colorHex: String? = null,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(dpToPx(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(12, 0, 12, 0)
            textSize = 13f
            val color = if (colorHex != null) {
                android.graphics.Color.parseColor(colorHex)
            } else {
                getColor(R.color.text_primary)
            }
            setTextColor(color)
            if (bold) {
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            gravity = android.view.Gravity.CENTER
        }
    }

    private fun createDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(2), LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(getColor(R.color.divider))
        }
    }

    private fun createHorizontalDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1))
            setBackgroundColor(getColor(R.color.divider))
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun displayBestModel() {
        val bestModel = modelStats.firstOrNull() ?: return

        bestModelNameText.text = "🏆 ${bestModel.modelName}"
        bestModelF1Text.text = "F1: ${String.format("%.3f", bestModel.avgF1)} (${String.format("%.1f", bestModel.avgF1 * 100)}%)"
        bestModelCountText.text = "${bestModel.predictionCount} predictions | Avg latency: ${(bestModel.avgLatency / 1000).toInt()}s"
    }

    private fun displayChampionModels() {
        // Best Accuracy Model
        val bestAccuracyModel = modelStats.maxByOrNull { it.avgAccuracy }
        bestAccuracyModel?.let {
            bestAccuracyModelText.text = it.modelName
            bestAccuracyScoreText.text = "Accuracy: ${String.format("%.3f", it.avgAccuracy)}"
        }

        // Best F1 Model
        val bestF1Model = modelStats.maxByOrNull { it.avgF1 }
        bestF1Model?.let {
            bestModelText.text = it.modelName
            bestF1ScoreText.text = "F1: ${String.format("%.3f", it.avgF1)}"
        }

        // Fastest Model (lowest avg latency)
        val fastestModel = modelStats.minByOrNull { it.avgLatency }
        fastestModel?.let {
            fastestModelText.text = it.modelName
            val latencySec = it.avgLatency / 1000.0
            fastestLatencyText.text = "Avg Latency: ${String.format("%.1f", latencySec)}s"
        }
    }

    private fun exportToExcel() {
        if (allPredictions.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                Toast.makeText(this@EnhancedDashboardActivity, "Exporting to Excel...", Toast.LENGTH_SHORT).show()

                val file = withContext(Dispatchers.IO) {
                    createExcelFile(allPredictions)
                }

                Toast.makeText(
                    this@EnhancedDashboardActivity,
                    "✓ Exported to: ${file.name}",
                    Toast.LENGTH_LONG
                ).show()

                Log.i(TAG, "Excel exported: ${file.absolutePath}")

            } catch (e: Exception) {
                Log.e(TAG, "Error exporting Excel", e)
                Toast.makeText(
                    this@EnhancedDashboardActivity,
                    "Error exporting: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun createExcelFile(predictions: List<PredictionResult>): File {
        val workbook = XSSFWorkbook()

        // Group by model
        val byModel = predictions.groupBy { it.modelName }

        for ((modelName, modelPredictions) in byModel) {
            val sheet = workbook.createSheet(modelName.take(31))

            // Create header row with ALL metrics
            val headerRow = sheet.createRow(0)
            val headers = listOf(
                // Basic
                "ID", "Name", "Ingredients", "Ground Truth", "Predicted",
                // Table 2: Quality
                "TP", "FP", "FN", "TN",
                "Precision", "Recall", "F1", "Accuracy",
                "Exact Match", "Hamming Loss", "FNR",
                // Table 3: Safety
                "Hallucination", "Over-Prediction",
                // Table 4: Efficiency
                "Latency (ms)", "TTFT (ms)", "ITPS", "OTPS", "OET (ms)", "Total (ms)",
                "Java Heap (KB)", "Native Heap (KB)", "PSS (KB)",
                "Model"
            )

            headers.forEachIndexed { index, header ->
                headerRow.createCell(index).setCellValue(header)
            }

            // Create data rows
            modelPredictions.forEachIndexed { index, result ->
                val row = sheet.createRow(index + 1)
                var colIndex = 0

                // Basic
                row.createCell(colIndex++).setCellValue(result.dataId)
                row.createCell(colIndex++).setCellValue(result.name)
                row.createCell(colIndex++).setCellValue(result.ingredients)
                row.createCell(colIndex++).setCellValue(result.allergensMapped)
                row.createCell(colIndex++).setCellValue(result.predictedAllergens)

                // Table 2: Quality
                row.createCell(colIndex++).setCellValue(result.truePositives.toDouble())
                row.createCell(colIndex++).setCellValue(result.falsePositives.toDouble())
                row.createCell(colIndex++).setCellValue(result.falseNegatives.toDouble())
                row.createCell(colIndex++).setCellValue(result.trueNegatives.toDouble())
                row.createCell(colIndex++).setCellValue(result.precision)
                row.createCell(colIndex++).setCellValue(result.recall)
                row.createCell(colIndex++).setCellValue(result.f1Score)
                row.createCell(colIndex++).setCellValue(result.accuracy)
                row.createCell(colIndex++).setCellValue(if (result.isExactMatch) "Yes" else "No")
                row.createCell(colIndex++).setCellValue(result.hammingLoss)
                row.createCell(colIndex++).setCellValue(result.falseNegativeRate)

                // Table 3: Safety
                row.createCell(colIndex++).setCellValue(result.hallucinationCount.toDouble())
                row.createCell(colIndex++).setCellValue(result.overPredictionCount.toDouble())
                // Table 4: Efficiency
                row.createCell(colIndex++).setCellValue(result.latencyMs.toDouble())
                row.createCell(colIndex++).setCellValue(result.ttftMs.toDouble())
                row.createCell(colIndex++).setCellValue(result.itps.toDouble())
                row.createCell(colIndex++).setCellValue(result.otps.toDouble())
                row.createCell(colIndex++).setCellValue(result.oetMs.toDouble())
                row.createCell(colIndex++).setCellValue(result.totalTimeMs.toDouble())
                row.createCell(colIndex++).setCellValue(result.javaHeapKb.toDouble())
                row.createCell(colIndex++).setCellValue(result.nativeHeapKb.toDouble())
                row.createCell(colIndex++).setCellValue(result.totalPssKb.toDouble())
                row.createCell(colIndex++).setCellValue(result.modelName)
            }
        }

        // Save to file
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val timestamp = System.currentTimeMillis()
        val file = File(downloadsDir, "SLM_All_Metrics_$timestamp.xlsx")

        FileOutputStream(file).use { outputStream ->
            workbook.write(outputStream)
        }

        workbook.close()

        return file
    }
}
