package edu.utem.ftmk.slm

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

class EnhancedDashboardActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ENHANCED_DASHBOARD"
    }

    // Sort State
    private var currentSortColumn: String = "F1 Score"
    private var isAscending: Boolean = false

    // Firebase and UI variables
    private lateinit var firestore: FirebaseFirestore
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView

    // Cards
    private lateinit var bestModelCard: MaterialCardView
    private lateinit var bestModelNameText: TextView
    private lateinit var bestModelF1Text: TextView
    private lateinit var bestModelCountText: TextView
    private lateinit var overallStatsCard: MaterialCardView
    private lateinit var totalPredictionsText: TextView
    private lateinit var totalModelsText: TextView
    private lateinit var avgF1AllText: TextView
    private lateinit var bestF1AllText: TextView
    private lateinit var bestAccuracyModelText: TextView
    private lateinit var bestAccuracyScoreText: TextView
    private lateinit var bestModelText: TextView
    private lateinit var bestF1ScoreText: TextView
    private lateinit var fastestModelText: TextView
    private lateinit var fastestLatencyText: TextView

    // Data
    private val modelStats = mutableListOf<ModelStatistics>()
    private var allPredictions = listOf<PredictionResult>()

    data class ModelStatistics(
        val modelName: String,
        val predictionCount: Int,
        // Quality
        val avgPrecision: Double,
        val avgRecall: Double,
        val avgF1: Double,
        val avgAccuracy: Double,
        val exactMatchRate: Double,
        val avgHammingLoss: Double,
        val avgFNR: Double,
        // Safety
        val hallucinationRate: Double,
        val overPredictionRate: Double,
        val abstentionAccuracy: Double,
        val safetyTestCount: Int, // <--- NEW: Tracks how many safety tests exist
        // Efficiency
        val avgLatency: Double,
        val avgTTFT: Double,
        val avgITPS: Double,
        val avgOTPS: Double,
        val avgOET: Double,
        val avgTotalTime: Double,
        val avgJavaHeap: Double,
        val avgNativeHeap: Double,
        val avgPSS: Double,
        val isBest: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enhanced_dashboard)
        firestore = FirebaseFirestore.getInstance()
        initializeViews()
        loadAllPredictions()
    }

    private fun initializeViews() {
        progressBar = findViewById(R.id.dashboardProgressBar)
        emptyText = findViewById(R.id.emptyText)
        bestModelCard = findViewById(R.id.bestModelCard)
        bestModelNameText = findViewById(R.id.bestModelNameText)
        bestModelF1Text = findViewById(R.id.bestModelF1Text)
        bestModelCountText = findViewById(R.id.bestModelCountText)
        overallStatsCard = findViewById(R.id.overallStatsCard)
        totalPredictionsText = findViewById(R.id.totalPredictionsText)
        totalModelsText = findViewById(R.id.totalModelsText)
        avgF1AllText = findViewById(R.id.avgF1AllText)
        bestF1AllText = findViewById(R.id.bestF1AllText)
        bestAccuracyModelText = findViewById(R.id.bestAccuracyModelText)
        bestAccuracyScoreText = findViewById(R.id.bestAccuracyScoreText)
        bestModelText = findViewById(R.id.bestModelText)
        bestF1ScoreText = findViewById(R.id.bestF1ScoreText)
        fastestModelText = findViewById(R.id.fastestModelText)
        fastestLatencyText = findViewById(R.id.fastestLatencyText)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.exportButton).setOnClickListener { exportToExcel() }
        findViewById<Button>(R.id.viewHistoryButton).setOnClickListener {
            startActivity(Intent(this, PredictionHistoryActivity::class.java))
        }
    }

    // ============================================
    // SORTING LOGIC
    // ============================================
    private fun sortDataBy(columnName: String) {
        if (currentSortColumn == columnName) {
            isAscending = !isAscending
        } else {
            currentSortColumn = columnName
            isAscending = false // Default to descending for most metrics
        }

        val selector: (ModelStatistics) -> Double = when (columnName) {
            "F1 Score" -> { it -> it.avgF1 }
            "Precision" -> { it -> it.avgPrecision }
            "Recall" -> { it -> it.avgRecall }
            "Accuracy" -> { it -> it.avgAccuracy }
            "EMR" -> { it -> it.exactMatchRate }
            "Hamming Loss" -> { it -> it.avgHammingLoss }
            "FNR" -> { it -> it.avgFNR }
            "Hallucination Rate" -> { it -> it.hallucinationRate }
            "Over-Prediction Rate" -> { it -> it.overPredictionRate }
            "Abstention Accuracy" -> { it -> it.abstentionAccuracy }
            "Avg Latency" -> { it -> it.avgLatency }
            "TTFT" -> { it -> it.avgTTFT }
            "ITPS" -> { it -> it.avgITPS }
            "OTPS" -> { it -> it.avgOTPS }
            "OET" -> { it -> it.avgOET }
            "Total Time" -> { it -> it.avgTotalTime }
            "Java Heap" -> { it -> it.avgJavaHeap }
            "Native Heap" -> { it -> it.avgNativeHeap }
            "PSS" -> { it -> it.avgPSS }
            else -> { it -> it.avgF1 }
        }

        if (isAscending) {
            modelStats.sortBy(selector)
        } else {
            modelStats.sortByDescending(selector)
        }

        // REBUILD TABLES
        build3SeparateTables()
    }

    private fun loadAllPredictions() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                emptyText.visibility = View.GONE
                bestModelCard.visibility = View.GONE
                overallStatsCard.visibility = View.GONE

                allPredictions = withContext(Dispatchers.IO) {
                    val snapshot = firestore.collection("predictions").get().await()
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
                        } catch (e: Exception) { null }
                    }
                }

                if (allPredictions.isEmpty()) {
                    progressBar.visibility = View.GONE
                    emptyText.visibility = View.VISIBLE
                    return@launch
                }

                calculateModelStatistics()
                displayOverallStats()
                displayBestModel()
                displayChampionModels()

                progressBar.visibility = View.GONE
                bestModelCard.visibility = View.VISIBLE
                overallStatsCard.visibility = View.VISIBLE

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@EnhancedDashboardActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun calculateModelStatistics() {
        val byModel = allPredictions.groupBy { it.modelName }
        modelStats.clear()
        for ((modelName, preds) in byModel) {
            val avgPrecision = preds.map { it.precision }.average()
            val avgRecall = preds.map { it.recall }.average()
            val avgF1 = preds.map { it.f1Score }.average()
            val avgAccuracy = preds.map { it.accuracy }.average()
            val avgHammingLoss = preds.map { it.hammingLoss }.average()
            val avgFNR = preds.map { it.falseNegativeRate }.average()
            val exactMatchRate = preds.count { it.isExactMatch }.toDouble() / preds.size
            val hallucinationRate = preds.count { it.hallucinationCount > 0 }.toDouble() / preds.size
            val overPredictionRate = preds.count { it.overPredictionCount > 0 }.toDouble() / preds.size

            // --- ABSTENTION LOGIC FIX ---
            val abstentionCases = preds.filter { it.isAbstentionCase }
            val abstentionAccuracy = if (abstentionCases.isNotEmpty()) {
                abstentionCases.count { it.isAbstentionCorrect }.toDouble() / abstentionCases.size
            } else { 0.0 }
            val safetyTestCount = abstentionCases.size
            // ----------------------------

            val avgLatency = preds.map { it.latencyMs.toDouble() }.average()
            val avgTTFT = preds.map { it.ttftMs.toDouble() }.average()
            val avgITPS = preds.map { it.itps.toDouble() }.average()
            val avgOTPS = preds.map { it.otps.toDouble() }.average()
            val avgOET = preds.map { it.oetMs.toDouble() }.average()
            val avgTotalTime = preds.map { it.totalTimeMs.toDouble() }.average()
            val avgJavaHeap = preds.map { it.javaHeapKb.toDouble() }.average()
            val avgNativeHeap = preds.map { it.nativeHeapKb.toDouble() }.average()
            val avgPSS = preds.map { it.totalPssKb.toDouble() }.average()

            modelStats.add(ModelStatistics(
                modelName, preds.size, avgPrecision, avgRecall, avgF1, avgAccuracy, exactMatchRate, avgHammingLoss, avgFNR,
                hallucinationRate, overPredictionRate, abstentionAccuracy, safetyTestCount,
                avgLatency, avgTTFT, avgITPS, avgOTPS, avgOET, avgTotalTime, avgJavaHeap, avgNativeHeap, avgPSS
            ))
        }

        // Initial Sort
        modelStats.sortByDescending { it.avgF1 }
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

    private fun displayBestModel() {
        val bestModel = modelStats.firstOrNull() ?: return

        bestModelNameText.text = "🏆 ${bestModel.modelName}"
        bestModelF1Text.text = "F1: ${String.format("%.3f", bestModel.avgF1)} (${String.format("%.1f", bestModel.avgF1 * 100)}%)"
        bestModelCountText.text = "${bestModel.predictionCount} predictions | Avg latency: ${(bestModel.avgLatency / 1000).toInt()}s"
    }

    private fun displayChampionModels() {
        val bestAccuracyModel = modelStats.maxByOrNull { it.avgAccuracy }
        bestAccuracyModel?.let {
            bestAccuracyModelText.text = it.modelName
            bestAccuracyScoreText.text = "Accuracy: ${String.format("%.3f", it.avgAccuracy)}"
        }

        val bestF1Model = modelStats.maxByOrNull { it.avgF1 }
        bestF1Model?.let {
            bestModelText.text = it.modelName
            bestF1ScoreText.text = "F1: ${String.format("%.3f", it.avgF1)}"
        }

        val fastestModel = modelStats.minByOrNull { it.avgLatency }
        fastestModel?.let {
            fastestModelText.text = it.modelName
            val latencySec = it.avgLatency / 1000.0
            fastestLatencyText.text = "Avg Latency: ${String.format("%.1f", latencySec)}s"
        }
    }

    // ============================================
    // EXCEL EXPORT (Traceability Compliant)
    // ============================================
    private fun exportToExcel() {
        if (allPredictions.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                Toast.makeText(this@EnhancedDashboardActivity, "Exporting to Excel...", Toast.LENGTH_SHORT).show()
                val file = withContext(Dispatchers.IO) { createExcelFile(allPredictions) }
                Toast.makeText(this@EnhancedDashboardActivity, "✓ Exported: ${file.name}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting Excel", e)
                Toast.makeText(this@EnhancedDashboardActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createExcelFile(predictions: List<PredictionResult>): File {
        val workbook = XSSFWorkbook()
        val byModel = predictions.groupBy { it.modelName }

        for ((modelName, modelPredictions) in byModel) {
            val sheet = workbook.createSheet(modelName.take(31))
            val headerRow = sheet.createRow(0)
            val headers = listOf(
                "ID", "Name", "Ingredients", "Ground Truth", "Predicted",
                "TP", "FP", "FN", "TN", "Precision", "Recall", "F1", "Accuracy",
                "Exact Match", "Hamming Loss", "FNR",
                "Hallucination Count", "Hallucinated Allergens",
                "Over-Pred Count", "Over-Pred Allergens",
                "Is Safety Test?", "Safety Check Result",
                "Latency (ms)", "TTFT (ms)", "ITPS", "OTPS", "OET (ms)", "Total (ms)",
                "Java Heap (KB)", "Native Heap (KB)", "PSS (KB)",
                "Device Model", "Model"
            )

            headers.forEachIndexed { index, header -> headerRow.createCell(index).setCellValue(header) }

            modelPredictions.forEachIndexed { index, result ->
                val row = sheet.createRow(index + 1)
                var c = 0
                row.createCell(c++).setCellValue(result.dataId)
                row.createCell(c++).setCellValue(result.name)
                row.createCell(c++).setCellValue(result.ingredients)
                row.createCell(c++).setCellValue(result.allergensMapped)
                row.createCell(c++).setCellValue(result.predictedAllergens)
                row.createCell(c++).setCellValue(result.truePositives.toDouble())
                row.createCell(c++).setCellValue(result.falsePositives.toDouble())
                row.createCell(c++).setCellValue(result.falseNegatives.toDouble())
                row.createCell(c++).setCellValue(result.trueNegatives.toDouble())
                row.createCell(c++).setCellValue(result.precision)
                row.createCell(c++).setCellValue(result.recall)
                row.createCell(c++).setCellValue(result.f1Score)
                row.createCell(c++).setCellValue(result.accuracy)
                row.createCell(c++).setCellValue(if (result.isExactMatch) "Yes" else "No")
                row.createCell(c++).setCellValue(result.hammingLoss)
                row.createCell(c++).setCellValue(result.falseNegativeRate)
                row.createCell(c++).setCellValue(result.hallucinationCount.toDouble())
                row.createCell(c++).setCellValue(result.hallucinatedAllergens)
                row.createCell(c++).setCellValue(result.overPredictionCount.toDouble())
                row.createCell(c++).setCellValue(result.overPredictedAllergens)

                if (result.isAbstentionCase) {
                    row.createCell(c++).setCellValue("Yes")
                    row.createCell(c++).setCellValue(if (result.isAbstentionCorrect) "PASSED" else "FAILED")
                } else {
                    row.createCell(c++).setCellValue("No")
                    row.createCell(c++).setCellValue("N/A")
                }

                row.createCell(c++).setCellValue(result.latencyMs.toDouble())
                row.createCell(c++).setCellValue(result.ttftMs.toDouble())
                row.createCell(c++).setCellValue(result.itps.toDouble())
                row.createCell(c++).setCellValue(result.otps.toDouble())
                row.createCell(c++).setCellValue(result.oetMs.toDouble())
                row.createCell(c++).setCellValue(result.totalTimeMs.toDouble())
                row.createCell(c++).setCellValue(result.javaHeapKb.toDouble())
                row.createCell(c++).setCellValue(result.nativeHeapKb.toDouble())
                row.createCell(c++).setCellValue(result.totalPssKb.toDouble())
                row.createCell(c++).setCellValue(result.deviceModel)
                row.createCell(c++).setCellValue(result.modelName)
            }
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, "SLM_All_Metrics_${System.currentTimeMillis()}.xlsx")
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()
        return file
    }

    private fun build3SeparateTables() {
        buildQualityMetricsTable()
        buildSafetyMetricsTable()
        buildEfficiencyMetricsTable()
    }

    // ============================================
    // TABLE BUILDING (With Sort Keys & N/A Fix)
    // ============================================

    private fun buildQualityMetricsTable() {
        val fixedContainer = findViewById<LinearLayout>(R.id.qualityFixedColumn)
        val scrollContainer = findViewById<LinearLayout>(R.id.qualityScrollableContent)
        if (fixedContainer == null) return
        fixedContainer.removeAllViews(); scrollContainer.removeAllViews()

        if (modelStats.isEmpty()) return

        fixedContainer.addView(createFixedHeaderCell("Model Name", R.color.quality_header))
        val headerRow = createHeaderRowContainer(R.color.quality_header)
        headerRow.addView(createHeaderCell("F1 Score", 90, "F1 Score"))
        headerRow.addView(createHeaderCell("Precision", 90, "Precision"))
        headerRow.addView(createHeaderCell("Recall", 90, "Recall"))
        headerRow.addView(createHeaderCell("Accuracy", 90, "Accuracy"))
        headerRow.addView(createHeaderCell("EMR", 80, "EMR"))
        headerRow.addView(createHeaderCell("Hamming\nLoss", 90, "Hamming Loss"))
        headerRow.addView(createHeaderCell("FNR", 80, "FNR"))
        scrollContainer.addView(headerRow)

        for ((index, model) in modelStats.withIndex()) {
            val bgColor = if (index % 2 == 0) R.color.row_even else R.color.row_odd
            fixedContainer.addView(createFixedDataCell(model.modelName, bgColor))
            val dataRow = createDataRowContainer(bgColor)
            dataRow.addView(createDataCell(String.format("%.3f", model.avgF1), 90, "#6200EA", true))
            dataRow.addView(createDataCell(String.format("%.3f", model.avgPrecision), 90))
            dataRow.addView(createDataCell(String.format("%.3f", model.avgRecall), 90))
            dataRow.addView(createDataCell(String.format("%.3f", model.avgAccuracy), 90))
            dataRow.addView(createDataCell("${(model.exactMatchRate * 100).toInt()}%", 80))
            dataRow.addView(createDataCell(String.format("%.3f", model.avgHammingLoss), 90))
            dataRow.addView(createDataCell("${(model.avgFNR * 100).toInt()}%", 80, "#F44336"))
            scrollContainer.addView(dataRow)
        }
    }

    private fun buildSafetyMetricsTable() {
        val fixedContainer = findViewById<LinearLayout>(R.id.safetyFixedColumn)
        val scrollContainer = findViewById<LinearLayout>(R.id.safetyScrollableContent)
        if (fixedContainer == null) return
        fixedContainer.removeAllViews(); scrollContainer.removeAllViews()

        if (modelStats.isEmpty()) return
        fixedContainer.addView(createFixedHeaderCell("Model Name", R.color.safety_header))
        val headerRow = createHeaderRowContainer(R.color.safety_header)
        headerRow.addView(createHeaderCell("Hallucination\nRate", 120, "Hallucination Rate"))
        headerRow.addView(createHeaderCell("Over-Prediction\nRate", 130, "Over-Prediction Rate"))
        headerRow.addView(createHeaderCell("Abstention\nAccuracy", 120, "Abstention Accuracy"))
        scrollContainer.addView(headerRow)

        for ((index, model) in modelStats.withIndex()) {
            val bgColor = if (index % 2 == 0) R.color.row_even else R.color.row_odd
            fixedContainer.addView(createFixedDataCell(model.modelName, bgColor))
            val dataRow = createDataRowContainer(bgColor)
            dataRow.addView(createDataCell("${(model.hallucinationRate * 100).toInt()}%", 120, "#F44336", true))
            dataRow.addView(createDataCell("${(model.overPredictionRate * 100).toInt()}%", 130, "#FF9800", true))

            // =================================================================
            // THIS IS THE CRITICAL PART THAT FIXES THE "0%" ISSUE
            // =================================================================
            if (model.safetyTestCount > 0) {
                // CASE 1: We have tests. Show the score.
                val color = if (model.abstentionAccuracy > 0.8) "#4CAF50" else "#F44336"
                dataRow.addView(createDataCell("${(model.abstentionAccuracy * 100).toInt()}%", 120, color, true))
            } else {
                // CASE 2: No tests found. Show N/A (Gray).
                dataRow.addView(createDataCell("N/A", 120, "#9E9E9E", false))
            }
            // =================================================================

            scrollContainer.addView(dataRow)
        }
    }

    private fun buildEfficiencyMetricsTable() {
        val fixedContainer = findViewById<LinearLayout>(R.id.efficiencyFixedColumn)
        val scrollContainer = findViewById<LinearLayout>(R.id.efficiencyScrollableContent)
        if (fixedContainer == null) return
        fixedContainer.removeAllViews(); scrollContainer.removeAllViews()

        if (modelStats.isEmpty()) return
        fixedContainer.addView(createFixedHeaderCell("Model Name", R.color.efficiency_header))
        val headerRow = createHeaderRowContainer(R.color.efficiency_header)
        headerRow.addView(createHeaderCell("Avg\nLatency", 90, "Avg Latency"))
        headerRow.addView(createHeaderCell("TTFT", 90, "TTFT"))
        headerRow.addView(createHeaderCell("ITPS", 80, "ITPS"))
        headerRow.addView(createHeaderCell("OTPS", 80, "OTPS"))
        headerRow.addView(createHeaderCell("OET", 90, "OET"))
        headerRow.addView(createHeaderCell("Total\nTime", 90, "Total Time"))
        headerRow.addView(createHeaderCell("Java\nHeap", 90, "Java Heap"))
        headerRow.addView(createHeaderCell("Native\nHeap", 90, "Native Heap"))
        headerRow.addView(createHeaderCell("PSS", 90, "PSS"))
        scrollContainer.addView(headerRow)

        for ((index, model) in modelStats.withIndex()) {
            val bgColor = if (index % 2 == 0) R.color.row_even else R.color.row_odd
            fixedContainer.addView(createFixedDataCell(model.modelName, bgColor))
            val dataRow = createDataRowContainer(bgColor)
            dataRow.addView(createDataCell("${(model.avgLatency / 1000).toInt()}s", 90, "#2196F3", true))
            dataRow.addView(createDataCell("${model.avgTTFT.toInt()}ms", 90))
            dataRow.addView(createDataCell("${model.avgITPS.toInt()}", 80))
            dataRow.addView(createDataCell("${model.avgOTPS.toInt()}", 80))
            dataRow.addView(createDataCell("${model.avgOET.toInt()}ms", 90))
            dataRow.addView(createDataCell("${model.avgTotalTime.toInt()}ms", 90))
            dataRow.addView(createDataCell("${(model.avgJavaHeap/1024).toInt()}MB", 90))
            dataRow.addView(createDataCell("${(model.avgNativeHeap/1024).toInt()}MB", 90))
            dataRow.addView(createDataCell("${(model.avgPSS/1024).toInt()}MB", 90))
            scrollContainer.addView(dataRow)
        }
    }

    // ============================================
    // HELPER FUNCTIONS (UI CREATION)
    // ============================================

    private fun createHeaderRowContainer(colorResId: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(45))
            setBackgroundColor(getColor(colorResId))
        }
    }

    private fun createDataRowContainer(colorResId: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(55))
            setBackgroundColor(getColor(colorResId))
        }
    }

    private fun createFixedHeaderCell(text: String, colorResId: Int): TextView {
        return TextView(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(45))
            setBackgroundColor(getColor(colorResId))
            setPadding(12, 0, 12, 0)
            textSize = 13f
            setTextColor(getColor(R.color.white))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
        }
    }

    private fun createFixedDataCell(text: String, colorResId: Int): TextView {
        return TextView(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(55))
            setBackgroundColor(getColor(colorResId))
            setPadding(12, 0, 12, 0)
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
    }

    private fun createHeaderCell(text: String, widthDp: Int, sortKey: String): TextView {
        return TextView(this).apply {
            var displayText = text
            if (currentSortColumn == sortKey) {
                displayText += if (isAscending) " ⬆️" else " ⬇️"
            }
            this.text = displayText
            layoutParams = LinearLayout.LayoutParams(dpToPx(widthDp), LinearLayout.LayoutParams.MATCH_PARENT)
            setPadding(12, 0, 12, 0)
            textSize = 13f
            setTextColor(getColor(R.color.white))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setOnClickListener { sortDataBy(sortKey) }
        }
    }

    private fun createDataCell(text: String, widthDp: Int, colorHex: String? = null, bold: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(dpToPx(widthDp), LinearLayout.LayoutParams.MATCH_PARENT)
            setPadding(12, 0, 12, 0)
            textSize = 13f
            val color = if (colorHex != null) Color.parseColor(colorHex) else getColor(R.color.text_primary)
            setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}