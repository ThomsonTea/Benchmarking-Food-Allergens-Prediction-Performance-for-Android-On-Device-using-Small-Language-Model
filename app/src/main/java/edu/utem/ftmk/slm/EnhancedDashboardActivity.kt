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
 * Enhanced Dashboard showing accuracy for all models with best model highlighted
 * FIXED VERSION - All errors resolved
 */
class EnhancedDashboardActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ENHANCED_DASHBOARD"
    }

    private lateinit var firestore: FirebaseFirestore
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: ModelComparisonAdapter
    private lateinit var bestModelCard: MaterialCardView
    private lateinit var bestModelNameText: TextView
    private lateinit var bestModelF1Text: TextView
    private lateinit var bestModelCountText: TextView
    private lateinit var overallStatsCard: MaterialCardView
    private lateinit var totalPredictionsText: TextView
    private lateinit var totalModelsText: TextView
    private lateinit var avgF1AllText: TextView
    private lateinit var bestF1AllText: TextView

    private val modelStats = mutableListOf<ModelStatistics>()
    private var allPredictions = listOf<PredictionResult>()

    data class ModelStatistics(
        val modelName: String,
        val predictionCount: Int,
        val avgF1: Double,
        val avgAccuracy: Double,  // ← NEW: Accuracy
        val avgPrecision: Double,
        val avgRecall: Double,
        val avgLatency: Long,
        val exactMatchRate: Double,
        val hallucinationRate: Double,
        val avgFNR: Double,
        val isBest: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enhanced_dashboard)

        firestore = FirebaseFirestore.getInstance()

        initializeViews()
        setupRecyclerView()
        loadAllPredictions()
    }

    private fun initializeViews() {
        progressBar = findViewById(R.id.dashboardProgressBar)
        recyclerView = findViewById(R.id.modelComparisonRecyclerView)
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

    private fun setupRecyclerView() {
        adapter = ModelComparisonAdapter(modelStats)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadAllPredictions() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                emptyText.visibility = View.GONE
                bestModelCard.visibility = View.GONE
                overallStatsCard.visibility = View.GONE

                Log.i(TAG, "Loading all predictions from Firebase...")

                // ✅ FIX 1: Specify type explicitly
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
                                accuracy = doc.getDouble("accuracy") ?: 0.0,  // ← NEW
                                isExactMatch = doc.getBoolean("isExactMatch") ?: false,
                                hammingLoss = doc.getDouble("hammingLoss") ?: 0.0,
                                falseNegativeRate = doc.getDouble("falseNegativeRate") ?: 0.0,

                                hasHallucination = doc.getBoolean("hasHallucination") ?: false,
                                hallucinatedAllergens = doc.getString("hallucinatedAllergens") ?: "",
                                hasOverPrediction = doc.getBoolean("hasOverPrediction") ?: false,
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
                                androidVersion = doc.getString("androidVersion") ?: ""
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing document: ${e.message}")
                            null
                        }
                    }
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

                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
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
        for ((modelName, predictions) in byModel) {
            val avgF1 = predictions.map { it.f1Score }.average()
            val avgAccuracy = predictions.map { it.accuracy }.average()  // ← NEW
            val avgPrecision = predictions.map { it.precision }.average()
            val avgRecall = predictions.map { it.recall }.average()
            val avgLatency = predictions.map { it.latencyMs }.average().toLong()
            val exactMatchRate = predictions.count { it.isExactMatch }.toDouble() / predictions.size
            val hallucinationRate = predictions.count { it.hasHallucination }.toDouble() / predictions.size
            val avgFNR = predictions.map { it.falseNegativeRate }.average()

            modelStats.add(
                ModelStatistics(
                    modelName = modelName,
                    predictionCount = predictions.size,
                    avgF1 = avgF1,
                    avgAccuracy = avgAccuracy,  // ← NEW
                    avgPrecision = avgPrecision,
                    avgRecall = avgRecall,
                    avgLatency = avgLatency,
                    exactMatchRate = exactMatchRate,
                    hallucinationRate = hallucinationRate,
                    avgFNR = avgFNR
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
        adapter.updateData(modelStats)
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
        bestModelCountText.text = "${bestModel.predictionCount} predictions | Avg latency: ${bestModel.avgLatency / 1000}s"
    }

    // ✅ FIX 2: Create our own export function (simple version)
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

                // ✅ FIX 3 & 4: Use file.name instead of name, file.absolutePath instead of absolutePath
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

    // ✅ FIX 2: Simple Excel export function
    private fun createExcelFile(predictions: List<PredictionResult>): File {
        val workbook = XSSFWorkbook()

        // Group by model
        val byModel = predictions.groupBy { it.modelName }

        for ((modelName, modelPredictions) in byModel) {
            val sheet = workbook.createSheet(modelName.take(31)) // Excel sheet name limit

            // Create header row
            val headerRow = sheet.createRow(0)
            val headers = listOf(
                "ID", "Name", "Ingredients", "Ground Truth", "Predicted",
                "TP", "FP", "FN", "TN",
                "Precision", "Recall", "F1", "Accuracy",
                "Exact Match", "Hamming Loss", "FNR",
                "Hallucination", "Over-Prediction",
                "Latency (ms)", "TTFT (ms)", "Model"
            )

            headers.forEachIndexed { index, header ->
                headerRow.createCell(index).setCellValue(header)
            }

            // Create data rows
            modelPredictions.forEachIndexed { index, result ->
                val row = sheet.createRow(index + 1)
                var colIndex = 0

                row.createCell(colIndex++).setCellValue(result.dataId)
                row.createCell(colIndex++).setCellValue(result.name)
                row.createCell(colIndex++).setCellValue(result.ingredients)
                row.createCell(colIndex++).setCellValue(result.allergensMapped)
                row.createCell(colIndex++).setCellValue(result.predictedAllergens)

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

                row.createCell(colIndex++).setCellValue(if (result.hasHallucination) result.hallucinatedAllergens else "No")
                row.createCell(colIndex++).setCellValue(if (result.hasOverPrediction) result.overPredictedAllergens else "No")

                row.createCell(colIndex++).setCellValue(result.latencyMs.toDouble())
                row.createCell(colIndex++).setCellValue(result.ttftMs.toDouble())
                row.createCell(colIndex++).setCellValue(result.modelName)
            }
        }

        // Save to file
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val timestamp = System.currentTimeMillis()
        val file = File(downloadsDir, "SLM_Predictions_$timestamp.xlsx")

        FileOutputStream(file).use { outputStream ->
            workbook.write(outputStream)
        }

        workbook.close()

        return file
    }
}
