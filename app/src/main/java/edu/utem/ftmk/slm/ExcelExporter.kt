package edu.utem.ftmk.slm

import android.content.Context
import android.os.Environment
import android.util.Log
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility for exporting prediction results to Excel
 */
object ExcelExporter {
    
    private const val TAG = "ExcelExporter"
    
    /**
     * Export results grouped by model to Excel file
     * Returns the file path if successful, null otherwise
     */
    fun exportResults(
        context: Context,
        resultsByModel: Map<String, List<PredictionResult>>,
        aggregateMetrics: Map<String, AggregateMetrics>
    ): String? {
        
        try {
            // Create workbook
            val workbook = XSSFWorkbook()
            
            // Create styles
            val headerStyle = createHeaderStyle(workbook)
            val dataStyle = createDataStyle(workbook)
            val metricStyle = createMetricStyle(workbook)
            
            // Create sheet for each model
            for ((modelName, results) in resultsByModel) {
                createModelSheet(
                    workbook,
                    modelName,
                    results,
                    aggregateMetrics[modelName],
                    headerStyle,
                    dataStyle,
                    metricStyle
                )
            }
            
            // Create summary sheet comparing all models
            createSummarySheet(
                workbook,
                aggregateMetrics,
                headerStyle,
                metricStyle
            )
            
            // Save to file
            val fileName = "AllergenPrediction_${getTimestamp()}.xlsx"
            val file = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                fileName
            )
            
            FileOutputStream(file).use { outputStream ->
                workbook.write(outputStream)
            }
            
            workbook.close()
            
            Log.i(TAG, "Excel file saved: ${file.absolutePath}")
            return file.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export Excel", e)
            return null
        }
    }
    
    /**
     * Create sheet for a single model
     */
    private fun createModelSheet(
        workbook: Workbook,
        modelName: String,
        results: List<PredictionResult>,
        metrics: AggregateMetrics?,
        headerStyle: CellStyle,
        dataStyle: CellStyle,
        metricStyle: CellStyle
    ) {
        
        // Create sheet
        val sheet = workbook.createSheet(sanitizeSheetName(modelName))
        
        var rowNum = 0
        
        // Add model info header
        val infoRow = sheet.createRow(rowNum++)
        createCell(infoRow, 0, "Model: $modelName", headerStyle)
        rowNum++
        
        // Add aggregate metrics
        if (metrics != null) {
            createMetricsSection(sheet, metrics, rowNum, metricStyle)
            rowNum += 20  // Space for metrics
        }
        
        // Add data headers
        val headerRow = sheet.createRow(rowNum++)
        val headers = PredictionResult.getExcelHeaders()
        headers.forEachIndexed { index, header ->
            createCell(headerRow, index, header, headerStyle)
        }
        
        // Add data rows
        for (result in results) {
            val dataRow = sheet.createRow(rowNum++)
            val rowData = result.toExcelRow()
            rowData.forEachIndexed { index, value ->
                createCell(dataRow, index, value.toString(), dataStyle)
            }
        }
        
        // Auto-size columns
        for (i in 0 until headers.size) {
            sheet.autoSizeColumn(i)
        }
    }
    
    /**
     * Create metrics section in sheet
     */
    private fun createMetricsSection(
        sheet: Sheet,
        metrics: AggregateMetrics,
        startRow: Int,
        style: CellStyle
    ) {
        var row = startRow
        
        // Sample count
        createMetricRow(sheet, row++, "Sample Count", metrics.sampleCount.toString(), style)
        row++
        
        // Prediction Quality Metrics
        createMetricRow(sheet, row++, "=== PREDICTION QUALITY ===", "", style)
        createMetricRow(sheet, row++, "Micro Precision", String.format("%.4f", metrics.microPrecision), style)
        createMetricRow(sheet, row++, "Micro Recall", String.format("%.4f", metrics.microRecall), style)
        createMetricRow(sheet, row++, "Micro F1", String.format("%.4f", metrics.microF1), style)
        createMetricRow(sheet, row++, "Macro Precision", String.format("%.4f", metrics.macroPrecision), style)
        createMetricRow(sheet, row++, "Macro Recall", String.format("%.4f", metrics.macroRecall), style)
        createMetricRow(sheet, row++, "Macro F1", String.format("%.4f", metrics.macroF1), style)
        createMetricRow(sheet, row++, "Exact Match Ratio", String.format("%.2f%%", metrics.exactMatchRatio * 100), style)
        createMetricRow(sheet, row++, "Hamming Loss", String.format("%.4f", metrics.avgHammingLoss), style)
        createMetricRow(sheet, row++, "False Negative Rate", String.format("%.4f", metrics.avgFnr), style)
        row++
        
        // Safety Metrics
        createMetricRow(sheet, row++, "=== SAFETY METRICS ===", "", style)
        createMetricRow(sheet, row++, "Hallucination Rate", String.format("%.2f%%", metrics.hallucinationRate * 100), style)
        createMetricRow(sheet, row++, "Over-Prediction Rate", String.format("%.2f%%", metrics.overPredictionRate * 100), style)
        createMetricRow(sheet, row++, "Abstention Accuracy", String.format("%.2f%%", metrics.abstentionAccuracy * 100), style)
        row++
        
        // Efficiency Metrics
        createMetricRow(sheet, row++, "=== EFFICIENCY METRICS ===", "", style)
        createMetricRow(sheet, row++, "Avg Latency (ms)", String.format("%.0f", metrics.avgLatency), style)
        createMetricRow(sheet, row++, "Avg TTFT (ms)", String.format("%.0f", metrics.avgTtft), style)
        createMetricRow(sheet, row++, "Avg ITPS (tok/s)", String.format("%.2f", metrics.avgItps), style)
        createMetricRow(sheet, row++, "Avg OTPS (tok/s)", String.format("%.2f", metrics.avgOtps), style)
        createMetricRow(sheet, row++, "Avg OET (ms)", String.format("%.0f", metrics.avgOet), style)
        createMetricRow(sheet, row++, "Avg Java Heap (KB)", String.format("%.0f", metrics.avgJavaHeap), style)
        createMetricRow(sheet, row++, "Avg Native Heap (KB)", String.format("%.0f", metrics.avgNativeHeap), style)
        createMetricRow(sheet, row++, "Avg Total PSS (KB)", String.format("%.0f", metrics.avgTotalPss), style)
    }
    
    /**
     * Create summary comparison sheet
     */
    private fun createSummarySheet(
        workbook: Workbook,
        aggregateMetrics: Map<String, AggregateMetrics>,
        headerStyle: CellStyle,
        metricStyle: CellStyle
    ) {
        
        val sheet = workbook.createSheet("Summary")
        var rowNum = 0
        
        // Title
        val titleRow = sheet.createRow(rowNum++)
        createCell(titleRow, 0, "Model Comparison Summary", headerStyle)
        rowNum++
        
        // Headers
        val headerRow = sheet.createRow(rowNum++)
        createCell(headerRow, 0, "Metric", headerStyle)
        var col = 1
        for (modelName in aggregateMetrics.keys) {
            createCell(headerRow, col++, modelName, headerStyle)
        }
        
        // Add each metric as a row
        val metricsList = listOf(
            "Sample Count" to { m: AggregateMetrics -> m.sampleCount.toString() },
            "Micro Precision" to { m: AggregateMetrics -> String.format("%.4f", m.microPrecision) },
            "Micro Recall" to { m: AggregateMetrics -> String.format("%.4f", m.microRecall) },
            "Micro F1" to { m: AggregateMetrics -> String.format("%.4f", m.microF1) },
            "Exact Match Ratio" to { m: AggregateMetrics -> String.format("%.2f%%", m.exactMatchRatio * 100) },
            "Hallucination Rate" to { m: AggregateMetrics -> String.format("%.2f%%", m.hallucinationRate * 100) },
            "Avg Latency (ms)" to { m: AggregateMetrics -> String.format("%.0f", m.avgLatency) },
            "Avg TTFT (ms)" to { m: AggregateMetrics -> String.format("%.0f", m.avgTtft) }
        )
        
        for ((metricName, extractor) in metricsList) {
            val dataRow = sheet.createRow(rowNum++)
            createCell(dataRow, 0, metricName, metricStyle)
            col = 1
            for ((_, metrics) in aggregateMetrics) {
                createCell(dataRow, col++, extractor(metrics), metricStyle)
            }
        }
        
        // Auto-size columns
        for (i in 0..aggregateMetrics.size) {
            sheet.autoSizeColumn(i)
        }
    }
    
    /**
     * Helper to create metric row
     */
    private fun createMetricRow(
        sheet: Sheet,
        rowNum: Int,
        label: String,
        value: String,
        style: CellStyle
    ) {
        val row = sheet.createRow(rowNum)
        createCell(row, 0, label, style)
        createCell(row, 1, value, style)
    }
    
    /**
     * Helper to create cell with style
     */
    private fun createCell(row: Row, column: Int, value: String, style: CellStyle) {
        val cell = row.createCell(column)
        cell.setCellValue(value)
        cell.cellStyle = style
    }
    
    /**
     * Create header cell style
     */
    private fun createHeaderStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.fontHeightInPoints = 12
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        return style
    }
    
    /**
     * Create data cell style
     */
    private fun createDataStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        style.wrapText = false
        return style
    }
    
    /**
     * Create metric cell style
     */
    private fun createMetricStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        style.setFont(font)
        return style
    }
    
    /**
     * Sanitize sheet name (Excel has restrictions)
     */
    private fun sanitizeSheetName(name: String): String {
        return name
            .replace(Regex("[:\\\\/*?\\[\\]]"), "")
            .take(31)  // Excel limit
    }
    
    /**
     * Get timestamp for filename
     */
    private fun getTimestamp(): String {
        val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return format.format(Date())
    }
}
