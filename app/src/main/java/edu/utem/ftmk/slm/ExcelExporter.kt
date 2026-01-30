package edu.utem.ftmk.slm

import android.content.Context
import android.os.Environment
import android.util.Log
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

class ExcelExporter(private val context: Context) {

    companion object {
        private const val TAG = "EXCEL_EXPORTER"
    }

    fun exportResults(results: List<PredictionResult>): File {
        val workbook = XSSFWorkbook()

        // Group by model
        val byModel = results.groupBy { it.modelName }

        for ((modelName, modelResults) in byModel) {
            // Create sheet (max 31 chars for Excel sheet name)
            val sheetName = modelName.take(31)
            val sheet = workbook.createSheet(sheetName)

            // Create header row
            val headerRow = sheet.createRow(0)
            val headers = listOf(
                "ID", "Product Name", "Ingredients",
                "Ground Truth", "Predicted Allergens", "Model",
                "TP", "FP", "FN", "TN",
                "Precision", "Recall", "F1 Score", "Accuracy",
                "Exact Match", "Hamming Loss", "FNR",
                "Hallucination Count", "Hallucinated Allergens",  // ✅ CHANGED
                "Over-Prediction Count", "Over-Predicted Allergens",  // ✅ CHANGED
                "Abstention Case", "Abstention Correct",
                "Latency (ms)", "TTFT (ms)", "ITPS", "OTPS", "OET (ms)", "Total Time (ms)",
                "Java Heap (KB)", "Native Heap (KB)", "Total PSS (KB)",
                "Device Model", "Android Version"
            )

            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
            }

            // Create data rows
            modelResults.forEachIndexed { index, result ->
                val row = sheet.createRow(index + 1)
                var col = 0

                // Product info
                row.createCell(col++).setCellValue(result.dataId)
                row.createCell(col++).setCellValue(result.name)
                row.createCell(col++).setCellValue(result.ingredients)
                row.createCell(col++).setCellValue(result.allergensMapped)
                row.createCell(col++).setCellValue(result.predictedAllergens)
                row.createCell(col++).setCellValue(result.modelName)

                // Confusion matrix
                row.createCell(col++).setCellValue(result.truePositives.toDouble())
                row.createCell(col++).setCellValue(result.falsePositives.toDouble())
                row.createCell(col++).setCellValue(result.falseNegatives.toDouble())
                row.createCell(col++).setCellValue(result.trueNegatives.toDouble())

                // Metrics
                row.createCell(col++).setCellValue(result.precision)
                row.createCell(col++).setCellValue(result.recall)
                row.createCell(col++).setCellValue(result.f1Score)
                row.createCell(col++).setCellValue(result.accuracy)
                row.createCell(col++).setCellValue(if (result.isExactMatch) "Yes" else "No")
                row.createCell(col++).setCellValue(result.hammingLoss)
                row.createCell(col++).setCellValue(result.falseNegativeRate)

                // Safety metrics - ✅ CHANGED TO NUMBERS
                row.createCell(col++).setCellValue(result.hallucinationCount.toDouble())  // ✅ NUMBER
                row.createCell(col++).setCellValue(result.hallucinatedAllergens)
                row.createCell(col++).setCellValue(result.overPredictionCount.toDouble())  // ✅ NUMBER
                row.createCell(col++).setCellValue(result.overPredictedAllergens)
                row.createCell(col++).setCellValue(if (result.isAbstentionCase) "Yes" else "No")
                row.createCell(col++).setCellValue(if (result.isAbstentionCorrect) "Yes" else "No")

                // Performance metrics
                row.createCell(col++).setCellValue(result.latencyMs.toDouble())
                row.createCell(col++).setCellValue(result.ttftMs.toDouble())
                row.createCell(col++).setCellValue(result.itps.toDouble())
                row.createCell(col++).setCellValue(result.otps.toDouble())
                row.createCell(col++).setCellValue(result.oetMs.toDouble())
                row.createCell(col++).setCellValue(result.totalTimeMs.toDouble())

                // Memory metrics
                row.createCell(col++).setCellValue(result.javaHeapKb.toDouble())
                row.createCell(col++).setCellValue(result.nativeHeapKb.toDouble())
                row.createCell(col++).setCellValue(result.totalPssKb.toDouble())

                // Device info
                row.createCell(col++).setCellValue(result.deviceModel)
                row.createCell(col++).setCellValue(result.androidVersion)
            }

            // Auto-size columns (first 10 only to save time)
            for (i in 0..9) {
                sheet.autoSizeColumn(i)
            }
        }

        // Save to Downloads folder
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val timestamp = System.currentTimeMillis()
        val file = File(downloadsDir, "SLM_All_Metrics_$timestamp.xlsx")

        FileOutputStream(file).use { outputStream ->
            workbook.write(outputStream)
        }

        workbook.close()

        Log.i(TAG, "✓ Excel file created: ${file.absolutePath}")
        return file
    }
}