package edu.utem.ftmk.slm

import android.content.Context
import android.util.Log
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

/**
 * ExcelExporter - Export prediction results to Excel file
 * FIXED VERSION: Removed autoSizeColumn() which crashes on Android
 */
class ExcelExporter(private val context: Context) {

    companion object {
        private const val TAG = "ExcelExporter"
    }

    /**
     * Export all prediction results to Excel file
     * One sheet per model + summary sheet
     */
    fun exportResults(
        groupedResults: Map<String, List<PredictionResult>>,
        outputPath: String
    ): Boolean {
        return try {
            Log.i(TAG, "Starting Excel export to: $outputPath")

            val workbook = XSSFWorkbook()

            // Create a sheet for each model
            groupedResults.forEach { (modelName, results) ->
                if (results.isNotEmpty()) {
                    Log.i(TAG, "Creating sheet for model: $modelName (${results.size} items)")
                    createModelSheet(workbook, modelName, results)
                }
            }

            // Create summary sheet
            if (groupedResults.isNotEmpty()) {
                Log.i(TAG, "Creating summary sheet")
                createSummarySheet(workbook, groupedResults)
            }

            // Write to file
            val file = File(outputPath)
            file.parentFile?.mkdirs()

            FileOutputStream(file).use { outputStream ->
                workbook.write(outputStream)
            }

            workbook.close()

            Log.i(TAG, "✓ Excel export completed successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "✗ Excel export failed", e)
            false
        }
    }

    /**
     * Create a sheet for one model's results
     */
    private fun createModelSheet(
        workbook: Workbook,
        modelName: String,
        results: List<PredictionResult>
    ) {
        // Sanitize sheet name (max 31 chars, no special chars)
        val sheetName = modelName
            .replace(" ", "_")
            .replace(".", "_")
            .take(31)

        val sheet = workbook.createSheet(sheetName)

        // Create header row
        val headerRow = sheet.createRow(0)
        val headers = PredictionResult.getExcelHeaders()

        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }

        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        // Add data rows
        results.forEachIndexed { rowIndex, result ->
            val row = sheet.createRow(rowIndex + 1)
            val data = result.toExcelRow()

            data.forEachIndexed { colIndex, value ->
                val cell = row.createCell(colIndex)
                when (value) {
                    is Number -> cell.setCellValue(value.toDouble())
                    is Boolean -> cell.setCellValue(value)
                    else -> cell.setCellValue(value.toString())
                }
            }
        }

        // ========== FIXED: Use setColumnWidth instead of autoSizeColumn ==========
        // Android doesn't support autoSizeColumn() because it uses AWT classes
        for (i in 0 until headers.size) {
            when (i) {
                0 -> sheet.setColumnWidth(i, 4000)   // Name
                1 -> sheet.setColumnWidth(i, 8000)   // Ingredients
                2 -> sheet.setColumnWidth(i, 4000)   // Ground Truth
                3 -> sheet.setColumnWidth(i, 4000)   // Predicted
                4, 5, 6, 7 -> sheet.setColumnWidth(i, 3000)  // TP, FP, FN, TN
                8, 9, 10 -> sheet.setColumnWidth(i, 3500)    // Precision, Recall, F1
                11 -> sheet.setColumnWidth(i, 2500)  // Exact Match
                12 -> sheet.setColumnWidth(i, 3000)  // Hamming Loss
                13 -> sheet.setColumnWidth(i, 3000)  // FNR
                14 -> sheet.setColumnWidth(i, 3000)  // Hallucination
                15 -> sheet.setColumnWidth(i, 5000)  // Hallucinated Allergens
                16 -> sheet.setColumnWidth(i, 3000)  // Over-prediction
                17 -> sheet.setColumnWidth(i, 5000)  // Over-predicted Allergens
                18 -> sheet.setColumnWidth(i, 3000)  // Latency
                19 -> sheet.setColumnWidth(i, 3000)  // TTFT
                20 -> sheet.setColumnWidth(i, 3000)  // ITPS
                21 -> sheet.setColumnWidth(i, 3000)  // OTPS
                22 -> sheet.setColumnWidth(i, 3000)  // Memory
                else -> sheet.setColumnWidth(i, 3000)  // Default
            }
        }
        // =========================================================================

        Log.i(TAG, "Created sheet: $sheetName with ${results.size} rows")
    }

    /**
     * Create summary comparison sheet
     */
    private fun createSummarySheet(
        workbook: Workbook,
        groupedResults: Map<String, List<PredictionResult>>
    ) {
        val sheet = workbook.createSheet("Summary")

        // Headers
        val headerRow = sheet.createRow(0)
        val headers = listOf(
            "Model",
            "Items",
            "Avg Precision",
            "Avg Recall",
            "Avg F1",
            "Exact Match %",
            "Avg Hamming Loss",
            "Avg FNR",
            "Hallucination Rate %",
            "Over-prediction Rate %",
            "Avg Latency (ms)",
            "Avg TTFT (ms)",
            "Avg ITPS",
            "Avg OTPS",
            "Avg Memory (MB)"
        )

        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }

        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        // Calculate aggregates for each model
        groupedResults.entries.forEachIndexed { rowIndex, (modelName, results) ->
            if (results.isEmpty()) return@forEachIndexed

            val row = sheet.createRow(rowIndex + 1)

            val avgPrecision = results.map { it.precision }.average()
            val avgRecall = results.map { it.recall }.average()
            val avgF1 = results.map { it.f1Score }.average()
            val exactMatchRate = results.count { it.isExactMatch }.toDouble() / results.size * 100
            val avgHammingLoss = results.map { it.hammingLoss }.average()
            val avgFNR = results.map { it.falseNegativeRate }.average()
            val hallucinationRate = results.count { it.hasHallucination }.toDouble() / results.size * 100
            val overPredictionRate = results.count { it.hasOverPrediction }.toDouble() / results.size * 100
            val avgLatency = results.map { it.latencyMs }.average()
            val avgTTFT = results.mapNotNull { if (it.ttftMs > 0) it.ttftMs else null }.average()
            val avgITPS = results.mapNotNull { if (it.itps > 0) it.itps else null }.average()
            val avgOTPS = results.mapNotNull { if (it.otps > 0) it.otps else null }.average()
            val avgMemory = results.map { it.totalPssKb / 1024.0 }.average()

            row.createCell(0).setCellValue(modelName)
            row.createCell(1).setCellValue(results.size.toDouble())
            row.createCell(2).setCellValue(avgPrecision)
            row.createCell(3).setCellValue(avgRecall)
            row.createCell(4).setCellValue(avgF1)
            row.createCell(5).setCellValue(exactMatchRate)
            row.createCell(6).setCellValue(avgHammingLoss)
            row.createCell(7).setCellValue(avgFNR)
            row.createCell(8).setCellValue(hallucinationRate)
            row.createCell(9).setCellValue(overPredictionRate)
            row.createCell(10).setCellValue(avgLatency)
            row.createCell(11).setCellValue(avgTTFT)
            row.createCell(12).setCellValue(avgITPS)
            row.createCell(13).setCellValue(avgOTPS)
            row.createCell(14).setCellValue(avgMemory)
        }

        // Set column widths for summary sheet
        for (i in 0 until headers.size) {
            when (i) {
                0 -> sheet.setColumnWidth(i, 5000)   // Model name
                1 -> sheet.setColumnWidth(i, 2500)   // Items
                else -> sheet.setColumnWidth(i, 4000) // Metrics
            }
        }

        Log.i(TAG, "Created summary sheet with ${groupedResults.size} models")
    }
}
