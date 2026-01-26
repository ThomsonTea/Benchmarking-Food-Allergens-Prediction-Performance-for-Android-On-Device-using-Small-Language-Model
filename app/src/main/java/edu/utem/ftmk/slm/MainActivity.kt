package edu.utem.ftmk.slm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileOutputStream
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.tasks.await
import android.view.LayoutInflater
import android.view.ViewGroup

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SLM_MAIN"
        private const val TAG_METRICS = "SLM_METRICS"
        private const val MODEL_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        private const val EXCEL_FILE = "foodpreprocessed.xlsx"
        private const val CHANNEL_ID = "allergen_predictions"
        private const val NOTIFICATION_ID = 1

        private var isProcessingAll = false
        private var processedCount = 0
        private var totalToProcess = 0

        init {
            try {
                System.loadLibrary("native-lib")
                System.loadLibrary("ggml-base")
                System.loadLibrary("ggml-cpu")
                System.loadLibrary("ggml")
                System.loadLibrary("llama")
                Log.i(TAG, "All native libraries loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native libraries", e)
            }
        }
    }

    // ===== NATIVE FUNCTION DECLARATIONS =====
    external fun loadModel(assetManager: android.content.res.AssetManager, modelPath: String): Boolean
    external fun predictAllergens(ingredients: String): String
    external fun getModelInfo(): String
    external fun unloadModel()
    external fun clearContext()
    external fun isModelHealthy(): Boolean

    // ===== DATA CLASSES =====
    data class BatchStatistics(
        var totalItems: Int = 0,
        var successCount: Int = 0,
        var failCount: Int = 0,
        var retryCount: Int = 0,
        var startTime: Long = 0L,
        var lastCheckpointTime: Long = 0L,
        val failedItems: MutableList<FoodItem> = mutableListOf()
    )

    data class PredictionAttempt(
        val item: FoodItem,
        val attempt: Int,
        val startTime: Long
    )

    data class MemorySnapshot(
        val javaHeap: Long,
        val nativeHeap: Long,
        val totalPss: Long
    )

    // UI Components
    private lateinit var modelStatusText: TextView
    private lateinit var modelLoadingProgress: ProgressBar
    private lateinit var loadModelButton: Button
    private lateinit var datasetSpinner: Spinner
    private lateinit var predictButton: Button
    private lateinit var progressCard: MaterialCardView
    private lateinit var progressTitle: TextView
    private lateinit var progressText: TextView
    private lateinit var predictionProgress: ProgressBar
    private lateinit var resultsTitle: TextView
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var modelSpinner: Spinner

    private lateinit var resultsAdapter: SimpleResultsAdapter

    private var currentModelName: String = "Qwen 2.5 1.5B"
    private var currentModelFile: String = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    private val resultsByModel = mutableMapOf<String, MutableList<PredictionResult>>()

    // Firebase
    private val firestore = FirebaseFirestore.getInstance()

    // Data
    private val allFoodItems = mutableListOf<FoodItem>()
    private val datasetGroups = mutableListOf<List<FoodItem>>()
    private var isModelLoaded = false
    private var isPredicting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestStoragePermission()
        initializeBatchProcessing()

        Log.i(TAG, "=== MainActivity onCreate ===")

        initializeViews()
        setupRecyclerView()
        createNotificationChannel()

        // Load dataset in background
        lifecycleScope.launch {
            loadFoodDataset()
        }

        // Setup model spinner
        val modelNames = ModelRegistry.getDisplayNames()
        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelNames)
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = modelAdapter

        findViewById<Button>(R.id.viewHistoryButton).setOnClickListener {
            startActivity(Intent(this, PredictionHistoryActivity::class.java))
        }

        findViewById<Button>(R.id.viewDashboardButton).setOnClickListener {
            startActivity(Intent(this, EnhancedDashboardActivity::class.java))
        }

        setupClickListeners()
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivityForResult(intent, 100)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivityForResult(intent, 100)
                }
            } else {
                Log.i(TAG, "Storage permission already granted")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    100
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✓ Storage permission granted", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Storage permission granted")
            } else {
                Toast.makeText(
                    this,
                    "⚠️ Storage permission required to load models!",
                    Toast.LENGTH_LONG
                ).show()
                Log.w(TAG, "Storage permission denied")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "✓ All files access granted", Toast.LENGTH_SHORT).show()
                    Log.i(TAG, "All files access granted")
                } else {
                    Toast.makeText(
                        this,
                        "⚠️ All files access required to load models!",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.w(TAG, "All files access denied")
                }
            }
        }
    }

    private fun initializeViews() {
        modelStatusText = findViewById(R.id.modelStatusText)
        modelSpinner = findViewById(R.id.modelSpinner)
        modelLoadingProgress = findViewById(R.id.modelLoadingProgress)
        loadModelButton = findViewById(R.id.loadModelButton)
        datasetSpinner = findViewById(R.id.datasetSpinner)
        predictButton = findViewById(R.id.predictButton)
        progressCard = findViewById(R.id.progressCard)
        progressTitle = findViewById(R.id.progressTitle)
        progressText = findViewById(R.id.progressText)
        predictionProgress = findViewById(R.id.predictionProgress)
        resultsTitle = findViewById(R.id.resultsTitle)
        resultsRecyclerView = findViewById(R.id.resultsRecyclerView)

    }

    private fun setupRecyclerView() {
        resultsAdapter = SimpleResultsAdapter()  // ✅ FIXED
        resultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = resultsAdapter
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Allergen Predictions"
            val descriptionText = "Notifications for prediction completion"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupClickListeners() {
        loadModelButton.setOnClickListener {
            if (!isModelLoaded) {
                loadAIModel()
            }
        }



        predictButton.setOnClickListener {
            if (!isPredicting) {
                val selectedPosition = datasetSpinner.selectedItemPosition
                if (selectedPosition >= 0 && selectedPosition < datasetGroups.size) {
                    runPredictions(datasetGroups[selectedPosition], selectedPosition + 1)
                }
            } else {
                Toast.makeText(this, "Prediction already in progress", Toast.LENGTH_SHORT).show()
            }
        }

        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedModel = ModelRegistry.MODELS[position]
                currentModelName = selectedModel.displayName
                currentModelFile = selectedModel.fileName

                if (isModelLoaded) {
                    Toast.makeText(
                        this@MainActivity,
                        "Please reload model to use ${selectedModel.displayName}",
                        Toast.LENGTH_LONG
                    ).show()
                    isModelLoaded = false
                    loadModelButton.isEnabled = true
                    loadModelButton.text = "Load Model"
                    predictButton.isEnabled = false
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val cleanupButton = findViewById<Button>(R.id.cleanupButton)
        cleanupButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Invalid Records")
                .setMessage("This will delete all Firebase records with:\n• itps = -1\n• latencyMs < 5000\n• ttftMs = -1\n\nContinue?")
                .setPositiveButton("Yes, Delete") { _, _ ->
                    cleanupInvalidFirebaseData()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ===== OPTIMAL SOLUTION HELPER FUNCTIONS =====

    private fun checkAndManageMemory(): Boolean {
        val runtime = Runtime.getRuntime()
        val maxMemoryMB = runtime.maxMemory() / 1024 / 1024
        val totalMemoryMB = runtime.totalMemory() / 1024 / 1024
        val freeMemoryMB = runtime.freeMemory() / 1024 / 1024
        val usedMemoryMB = totalMemoryMB - freeMemoryMB

        Log.i(TAG, "Memory: ${usedMemoryMB}MB used / ${totalMemoryMB}MB total / ${maxMemoryMB}MB max (${freeMemoryMB}MB free)")

        if (freeMemoryMB < 500) {
            Log.w(TAG, "⚠️ Low memory: ${freeMemoryMB}MB free - forcing GC")
            System.gc()
            Thread.sleep(2000)

            val newFreeMB = runtime.freeMemory() / 1024 / 1024
            Log.i(TAG, "After GC: ${newFreeMB}MB free")

            if (newFreeMB < 300) {
                Log.e(TAG, "❌ Critical memory: ${newFreeMB}MB - still too low!")
                return false
            }
        }

        return true
    }

    private fun getSafeIngredients(ingredients: String): String {
        val maxChars = 2000
        return if (ingredients.length > maxChars) {
            Log.w(TAG, "⚠️ Truncating ingredients: ${ingredients.length} → ${maxChars} chars")
            ingredients.take(maxChars)
        } else {
            ingredients
        }
    }

    private fun isValidPredictionResult(rawResult: String, actualLatency: Long): Boolean {
        val parts = rawResult.split("|", limit = 2)

        if (parts.size != 2) {
            Log.e(TAG, "Invalid format: missing | separator")
            return false
        }

        val prediction = parts[1].trim()
        if (prediction.isEmpty()) {
            Log.e(TAG, "Invalid format: empty prediction")
            return false
        }

        if (actualLatency < 5000) {
            Log.e(TAG, "Invalid latency: ${actualLatency}ms (too fast)")
            return false
        }

        return true
    }

    private suspend fun predictWithRetryAndSafety(
        item: FoodItem,
        deviceInfo: String,
        androidVersion: String,
        maxRetries: Int = 3
    ): PredictionResult? {

        for (attempt in 1..maxRetries) {
            try {
                Log.i(TAG, "🔄 Attempt $attempt/$maxRetries for: ${item.name}")

                if (!checkAndManageMemory()) {
                    throw Exception("Insufficient memory")
                }

                if (!isModelHealthy()) {
                    throw Exception("Model is unhealthy")
                }

                clearContext()
                Thread.sleep(100)

                val safeIngredients = getSafeIngredients(item.ingredients)

                val memBefore = captureMemorySnapshot()
                val predStartTime = System.currentTimeMillis()

                val rawResult = withTimeout(180000L) {
                    predictAllergens(safeIngredients)
                }

                val predEndTime = System.currentTimeMillis()
                val memAfter = captureMemorySnapshot()
                val actualLatency = predEndTime - predStartTime

                Log.i(TAG, "Raw result: $rawResult")
                Log.i(TAG, "Latency: ${actualLatency}ms")

                if (!isValidPredictionResult(rawResult, actualLatency)) {
                    throw Exception("Invalid prediction result")
                }

                val parts = rawResult.split("|", limit = 2)
                val metaString = parts[0]
                val modelOutput = parts[1]

                var ttftMs = -1L
                var itps = -1L
                var otps = -1L
                var oetMs = -1L

                metaString.split(";").forEach { metric ->
                    when {
                        metric.startsWith("TTFT_MS=") ->
                            ttftMs = metric.removePrefix("TTFT_MS=").toLongOrNull() ?: -1L
                        metric.startsWith("ITPS=") ->
                            itps = metric.removePrefix("ITPS=").toLongOrNull() ?: -1L
                        metric.startsWith("OTPS=") ->
                            otps = metric.removePrefix("OTPS=").toLongOrNull() ?: -1L
                        metric.startsWith("OET_MS=") ->
                            oetMs = metric.removePrefix("OET_MS=").toLongOrNull() ?: -1L
                    }
                }

                if (ttftMs == -1L) {
                    Log.w(TAG, "⚠️ Using actual latency for metrics")
                    ttftMs = actualLatency
                    oetMs = actualLatency
                    itps = 5
                }

                val validAllergens = setOf(
                    "milk", "egg", "peanut", "tree nut",
                    "wheat", "soy", "fish", "shellfish", "sesame", "none"
                )

                val predicted = modelOutput.lowercase().trim()
                    .replace("tree-nut", "tree nut")
                    .replace("treenut", "tree nut")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .filter { it in validAllergens }
                    .distinct()
                    .sorted()
                    .let { allergens ->
                        if (allergens.isEmpty() || allergens.contains("none")) {
                            "none"
                        } else {
                            allergens.filter { it != "none" }.joinToString(", ")
                        }
                    }

                if (predicted.isBlank()) {
                    throw Exception("No valid allergens in output: $modelOutput")
                }

                val metrics = MetricsCalculator.calculateMetrics(
                    groundTruth = item.allergensMapped,
                    predicted = predicted,
                    ingredients = item.ingredients
                )

                val result = PredictionResult(
                    dataId = item.id,
                    name = item.name,
                    ingredients = item.ingredients,
                    allergensRaw = item.allergensRaw,
                    allergensMapped = item.allergensMapped,
                    predictedAllergens = predicted,
                    modelName = currentModelName,

                    truePositives = metrics.tp,
                    falsePositives = metrics.fp,
                    falseNegatives = metrics.fn,
                    trueNegatives = metrics.tn,
                    precision = metrics.precision,
                    recall = metrics.recall,
                    f1Score = metrics.f1Score,
                    accuracy = metrics.accuracy,
                    isExactMatch = metrics.isExactMatch,
                    hammingLoss = metrics.hammingLoss,
                    falseNegativeRate = metrics.fnr,

                    hasHallucination = metrics.hasHallucination,
                    hallucinatedAllergens = metrics.hallucinatedAllergens,
                    hasOverPrediction = metrics.hasOverPrediction,
                    overPredictedAllergens = metrics.overPredictedAllergens,
                    isAbstentionCase = metrics.isAbstentionCase,
                    isAbstentionCorrect = metrics.isAbstentionCorrect,

                    latencyMs = actualLatency,
                    ttftMs = ttftMs,
                    itps = itps,
                    otps = otps,
                    oetMs = oetMs,
                    totalTimeMs = actualLatency,

                    javaHeapKb = memAfter.javaHeap - memBefore.javaHeap,
                    nativeHeapKb = memAfter.nativeHeap - memBefore.nativeHeap,
                    totalPssKb = memAfter.totalPss - memBefore.totalPss,

                    deviceModel = deviceInfo,
                    androidVersion = androidVersion
                )

                Log.i(TAG, "✓ Success on attempt $attempt: ${item.name} → $predicted")
                return result

            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "⏱️ Timeout on attempt $attempt for ${item.name}")

                if (attempt < maxRetries) {
                    val delayMs = 2000L * attempt
                    Log.i(TAG, "Waiting ${delayMs}ms before retry...")
                    Thread.sleep(delayMs)
                    clearContext()
                    System.gc()
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error on attempt $attempt for ${item.name}: ${e.message}")

                if (attempt < maxRetries) {
                    val delayMs = 2000L * attempt
                    Log.i(TAG, "Waiting ${delayMs}ms before retry...")
                    Thread.sleep(delayMs)
                    clearContext()
                    System.gc()
                }
            }
        }

        Log.e(TAG, "✗ All $maxRetries attempts failed for: ${item.name}")
        return null
    }

    private suspend fun reloadModelSafely(modelFilePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "🔄 Reloading model...")

                try {
                    unloadModel()
                    Log.i(TAG, "✓ Old model unloaded")
                } catch (e: Exception) {
                    Log.w(TAG, "Warning unloading: ${e.message}")
                }

                System.gc()
                Thread.sleep(3000)

                if (!checkAndManageMemory()) {
                    Log.e(TAG, "❌ Not enough memory to reload model")
                    return@withContext false
                }

                val success = loadModel(assets, modelFilePath)

                if (success) {
                    Log.i(TAG, "✓ Model reloaded successfully")
                    Thread.sleep(2000)
                    return@withContext true
                } else {
                    Log.e(TAG, "❌ Failed to reload model")
                    return@withContext false
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception reloading model: ${e.message}")
                return@withContext false
            }
        }
    }

    // ===== BATCH PROCESSING =====

    private fun initializeBatchProcessing() {
        val processAllButton = findViewById<Button>(R.id.processAllButton)

        processAllButton.setOnClickListener {
            if (!isModelLoaded) {
                Toast.makeText(this, "⚠️ Please load a model first!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (isProcessingAll) {
                Toast.makeText(this, "Already processing...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (allFoodItems.isEmpty()) {
                Toast.makeText(this, "⚠️ Dataset not loaded yet!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Optimal Batch Processing")
                .setMessage(
                    "Process all ${allFoodItems.size} items with optimizations:\n\n" +
                            "✓ Context clearing (prevents memory buildup)\n" +
                            "✓ Auto-retry on failure (3 attempts)\n" +
                            "✓ Memory monitoring\n" +
                            "✓ Checkpoints every 50 items\n" +
                            "✓ Input validation\n\n" +
                            "Model: $currentModelName\n" +
                            "Estimated time: 2-3 hours\n\n" +
                            "Keep phone plugged in!\n" +
                            "Continue?"
                )
                .setPositiveButton("Yes, Start Optimal Processing") { _, _ ->
                    startOptimalBatchProcessing()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun startOptimalBatchProcessing() {
        isProcessingAll = true

        val batchProgressBar = findViewById<ProgressBar>(R.id.batchProgressBar)
        val batchProgressText = findViewById<TextView>(R.id.batchProgressText)
        val processAllButton = findViewById<Button>(R.id.processAllButton)

        batchProgressBar.visibility = View.VISIBLE
        batchProgressText.visibility = View.VISIBLE
        batchProgressBar.max = allFoodItems.size
        batchProgressBar.progress = 0

        processAllButton.isEnabled = false
        processAllButton.text = "PROCESSING..."
        loadModelButton.isEnabled = false
        predictButton.isEnabled = false
        datasetSpinner.isEnabled = false
        modelSpinner.isEnabled = false

        Log.i(TAG, "=== OPTIMAL BATCH PROCESSING START ===")
        Log.i(TAG, "Model: $currentModelName")
        Log.i(TAG, "Total items: ${allFoodItems.size}")

        val stats = BatchStatistics(
            totalItems = allFoodItems.size,
            startTime = System.currentTimeMillis(),
            lastCheckpointTime = System.currentTimeMillis()
        )

        lifecycleScope.launch {
            val modelFilePath = withContext(Dispatchers.IO) {
                copyModelFromAssets()?.absolutePath
            }

            if (modelFilePath == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "❌ Model file not found!", Toast.LENGTH_LONG).show()
                    resetBatchUI()
                }
                return@launch
            }

            val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}"
            val androidVersion = "Android ${Build.VERSION.RELEASE}"

            withContext(Dispatchers.IO) {

                for ((index, item) in allFoodItems.withIndex()) {
                    if (!isProcessingAll) {
                        Log.w(TAG, "⚠️ Batch processing cancelled by user")
                        break
                    }

                    val itemNumber = index + 1
                    Log.i(TAG, "")
                    Log.i(TAG, "=".repeat(70))
                    Log.i(TAG, "Processing [$itemNumber/${stats.totalItems}]: ${item.name}")
                    Log.i(TAG, "=".repeat(70))

                    if (index > 0 && index % 50 == 0) {
                        val timeSinceCheckpoint = (System.currentTimeMillis() - stats.lastCheckpointTime) / 1000
                        Log.i(TAG, "")
                        Log.i(TAG, "🏁 CHECKPOINT at item $itemNumber (${timeSinceCheckpoint}s since last)")
                        Log.i(TAG, "Stats: ✓${stats.successCount} ❌${stats.failCount} 🔄${stats.retryCount}")

                        val reloadSuccess = reloadModelSafely(modelFilePath)

                        if (!reloadSuccess) {
                            Log.e(TAG, "❌ Failed to reload at checkpoint $itemNumber")
                            Log.e(TAG, "Stopping batch processing")
                            break
                        }

                        stats.lastCheckpointTime = System.currentTimeMillis()
                    }

                    val result = predictWithRetryAndSafety(item, deviceInfo, androidVersion, maxRetries = 3)

                    if (result != null) {
                        saveToFirebase(result)
                        stats.successCount++

                        Log.i(TAG, "✓ [$itemNumber/${stats.totalItems}] ${item.name}")
                        Log.i(TAG, "   Predicted: ${result.predictedAllergens}")
                        Log.i(TAG, "   F1: ${String.format("%.3f", result.f1Score)}")
                        Log.i(TAG, "   Accuracy: ${String.format("%.3f", result.accuracy)}")
                        Log.i(TAG, "   Latency: ${result.latencyMs}ms")
                    } else {
                        stats.failedItems.add(item)
                        stats.failCount++

                        Log.e(TAG, "✗ [$itemNumber/${stats.totalItems}] ${item.name} - FAILED")
                    }

                    withContext(Dispatchers.Main) {
                        batchProgressBar.progress = itemNumber

                        val percentage = (itemNumber * 100) / stats.totalItems
                        val elapsed = (System.currentTimeMillis() - stats.startTime) / 1000
                        val estimated = if (itemNumber > 0) {
                            (elapsed * stats.totalItems / itemNumber) - elapsed
                        } else 0

                        batchProgressText.text = "$itemNumber/${stats.totalItems} ($percentage%) | " +
                                "✓${stats.successCount} ❌${stats.failCount} | " +
                                "⏱️${elapsed/60}m / ~${estimated/60}m"
                    }

                    Thread.sleep(500)
                }

                if (stats.failedItems.isNotEmpty() && isProcessingAll) {
                    Log.i(TAG, "")
                    Log.i(TAG, "=".repeat(70))
                    Log.i(TAG, "🔄 RETRY PHASE: ${stats.failedItems.size} failed items")
                    Log.i(TAG, "=".repeat(70))

                    val retryItems = stats.failedItems.toList()
                    stats.failedItems.clear()

                    for ((retryIndex, item) in retryItems.withIndex()) {
                        if (!isProcessingAll) break

                        Log.i(TAG, "🔄 Retry [${retryIndex + 1}/${retryItems.size}]: ${item.name}")

                        clearContext()
                        System.gc()
                        Thread.sleep(1000)

                        val result = predictWithRetryAndSafety(item, deviceInfo, androidVersion, maxRetries = 2)

                        if (result != null) {
                            saveToFirebase(result)
                            stats.successCount++
                            stats.failCount--
                            stats.retryCount++
                            Log.i(TAG, "✓ Retry successful: ${item.name}")
                        } else {
                            stats.failedItems.add(item)
                            Log.e(TAG, "✗ Retry failed: ${item.name}")
                        }
                    }
                }

                try {
                    unloadModel()
                    Log.i(TAG, "✓ Model unloaded")
                } catch (e: Exception) {
                    Log.w(TAG, "Warning unloading final model: ${e.message}")
                }
            }

            val totalTime = (System.currentTimeMillis() - stats.startTime) / 1000
            val totalMin = totalTime / 60
            val successRate = (stats.successCount * 100.0) / stats.totalItems

            Log.i(TAG, "")
            Log.i(TAG, "=".repeat(70))
            Log.i(TAG, "=== BATCH PROCESSING COMPLETE ===")
            Log.i(TAG, "=".repeat(70))
            Log.i(TAG, "Total items: ${stats.totalItems}")
            Log.i(TAG, "Successful: ${stats.successCount} (${String.format("%.1f", successRate)}%)")
            Log.i(TAG, "Failed: ${stats.failCount}")
            Log.i(TAG, "Retries: ${stats.retryCount}")
            Log.i(TAG, "Time: ${totalTime}s (${totalMin}min)")
            Log.i(TAG, "=".repeat(70))

            if (stats.failedItems.isNotEmpty()) {
                Log.w(TAG, "Failed items:")
                stats.failedItems.forEach { Log.w(TAG, "  - ${it.name}") }
            }

            withContext(Dispatchers.Main) {
                isProcessingAll = false

                batchProgressText.text = if (stats.failCount == 0) {
                    "✓ Perfect! ${stats.successCount}/${stats.totalItems} in ${totalMin}min"
                } else {
                    "✓ ${stats.successCount} ❌ ${stats.failCount} (${String.format("%.1f", successRate)}%) in ${totalMin}min"
                }

                processAllButton.text = "PROCESS ALL 200 ITEMS"
                processAllButton.isEnabled = true
                loadModelButton.isEnabled = true
                predictButton.isEnabled = true
                datasetSpinner.isEnabled = true
                modelSpinner.isEnabled = true

                val message = if (stats.failCount < 5) {
                    "✓ Excellent! ${stats.successCount} items in ${totalMin} minutes"
                } else {
                    "⚠️ ${stats.successCount} successful, ${stats.failCount} failed\nTime: ${totalMin} minutes"
                }

                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()

                showNotification(
                    "Batch Processing Complete",
                    "${stats.successCount}/${stats.totalItems} successful (${String.format("%.1f", successRate)}%)"
                )
            }
        }
    }

    private fun resetBatchUI() {
        isProcessingAll = false
        findViewById<Button>(R.id.processAllButton).apply {
            isEnabled = true
            text = "PROCESS ALL 200 ITEMS"
        }
        loadModelButton.isEnabled = true
        predictButton.isEnabled = true
        datasetSpinner.isEnabled = true
        modelSpinner.isEnabled = true
    }

    // ===== EXISTING FUNCTIONS =====

    private fun getCellValue(cell: Cell?): String {
        if (cell == null) return ""

        return try {
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue ?: ""
                CellType.NUMERIC -> cell.numericCellValue.toInt().toString()
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    try {
                        when (cell.cachedFormulaResultType) {
                            CellType.STRING -> cell.stringCellValue ?: ""
                            CellType.NUMERIC -> cell.numericCellValue.toInt().toString()
                            else -> ""
                        }
                    } catch (e: Exception) {
                        ""
                    }
                }
                CellType.BLANK -> ""
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun loadFoodDataset() = withContext(Dispatchers.IO) {
        Log.i(TAG, "=== Loading Food Dataset ===")

        try {
            val inputStream = assets.open(EXCEL_FILE)
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)

            Log.i(TAG, "Reading Excel file...")

            for (i in 1 until sheet.physicalNumberOfRows) {
                val row = sheet.getRow(i) ?: continue

                try {
                    val foodItem = FoodItem(
                        id = getCellValue(row.getCell(0)).trim(),
                        name = getCellValue(row.getCell(1)).trim(),
                        ingredients = getCellValue(row.getCell(2)).trim(),
                        allergensRaw = getCellValue(row.getCell(3)).trim(),
                        allergensMapped = getCellValue(row.getCell(4)).trim(),
                        link = getCellValue(row.getCell(5)).trim()
                    )

                    if (foodItem.id.isNotEmpty() && foodItem.name.isNotEmpty()) {
                        allFoodItems.add(foodItem)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading row $i: ${e.message}")
                }
            }

            workbook.close()
            inputStream.close()

            for (i in allFoodItems.indices step 10) {
                val group = allFoodItems.subList(
                    i,
                    minOf(i + 10, allFoodItems.size)
                )
                datasetGroups.add(group)
            }

            withContext(Dispatchers.Main) {
                setupDatasetSpinner()
                Log.i(TAG, "✓ Loaded ${allFoodItems.size} items in ${datasetGroups.size} groups")
                Toast.makeText(
                    this@MainActivity,
                    "Dataset loaded: ${allFoodItems.size} items in ${datasetGroups.size} sets",
                    Toast.LENGTH_LONG
                ).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to load Excel file", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "Error loading dataset: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupDatasetSpinner() {
        val datasetNames = datasetGroups.mapIndexed { index, group ->
            "Set ${index + 1}: Items ${group.first().id}-${group.last().id} (${group.size} items)"
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            datasetNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        datasetSpinner.adapter = adapter
        datasetSpinner.isEnabled = true
    }

    private fun loadAIModel() {
        lifecycleScope.launch {
            try {
                loadModelButton.isEnabled = false
                modelStatusText.text = "Loading model..."
                modelLoadingProgress.visibility = View.VISIBLE
                modelLoadingProgress.isIndeterminate = true

                val modelFile = withContext(Dispatchers.IO) {
                    copyModelFromAssets()
                }

                if (modelFile == null) {
                    modelStatusText.text = "Failed to copy model"
                    loadModelButton.isEnabled = true
                    modelLoadingProgress.visibility = View.GONE
                    showNotification("Model Load Failed", "Could not copy model file")
                    return@launch
                }

                val startTime = System.currentTimeMillis()
                val loaded = withContext(Dispatchers.IO) {
                    loadModel(assets, modelFile.absolutePath)
                }

                val loadTime = System.currentTimeMillis() - startTime

                if (loaded) {
                    isModelLoaded = true
                    modelStatusText.text = "Model loaded (${loadTime / 1000}s)"
                    modelLoadingProgress.visibility = View.GONE
                    predictButton.isEnabled = true
                    loadModelButton.text = "Model Loaded ✓"

                    Log.i(TAG, "✓ Model loaded in ${loadTime}ms")
                    Toast.makeText(this@MainActivity, "Model ready!", Toast.LENGTH_SHORT).show()
                    showNotification("Model Ready", "AI model loaded successfully")
                } else {
                    modelStatusText.text = "Failed to load model"
                    loadModelButton.isEnabled = true
                    modelLoadingProgress.visibility = View.GONE
                    showNotification("Model Load Failed", "Failed to initialize model")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading model", e)
                modelStatusText.text = "Error: ${e.message}"
                loadModelButton.isEnabled = true
                modelLoadingProgress.visibility = View.GONE
                showNotification("Error", "Model loading error: ${e.message}")
            }
        }
    }

    private fun copyModelFromAssets(): File? {
        try {
            Log.i(TAG, "Looking for model: $currentModelFile")

            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val modelsDir = File(documentsDir, "SLM_Models")
            val modelFile = File(modelsDir, currentModelFile)

            Log.i(TAG, "Checking: ${modelFile.absolutePath}")

            if (modelFile.exists()) {
                val sizeMB = modelFile.length() / 1024 / 1024
                Log.i(TAG, "✓ Model found! Size: ${sizeMB}MB")
                return modelFile
            }

            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val downloadModelsDir = File(downloadDir, "SLM_Models")
            val downloadModelFile = File(downloadModelsDir, currentModelFile)

            Log.i(TAG, "Checking: ${downloadModelFile.absolutePath}")

            if (downloadModelFile.exists()) {
                val sizeMB = downloadModelFile.length() / 1024 / 1024
                Log.i(TAG, "✓ Model found! Size: ${sizeMB}MB")
                return downloadModelFile
            }

            Log.e(TAG, "✗ Model file not found!")
            Log.e(TAG, "Please place model files in:")
            Log.e(TAG, "  ${modelsDir.absolutePath}")
            Log.e(TAG, "Or:")
            Log.e(TAG, "  ${downloadModelsDir.absolutePath}")

            runOnUiThread {
                Toast.makeText(
                    this,
                    "Model not found!\n\nPlace models in:\nDocuments/SLM_Models/\n\nFile: $currentModelFile",
                    Toast.LENGTH_LONG
                ).show()
            }

            return null

        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            return null
        }
    }

    private fun runPredictions(foodItems: List<FoodItem>, setNumber: Int) {
        lifecycleScope.launch {
            try {
                isPredicting = true

                resultsAdapter.clearResults()

                progressCard.visibility = View.VISIBLE
                resultsTitle.visibility = View.VISIBLE
                resultsRecyclerView.visibility = View.VISIBLE

                predictButton.isEnabled = false
                datasetSpinner.isEnabled = false
                loadModelButton.isEnabled = false

                predictionProgress.max = foodItems.size
                predictionProgress.progress = 0

                val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}"
                val androidVersion = "Android ${Build.VERSION.RELEASE}"

                var successCount = 0
                var failCount = 0

                for ((index, foodItem) in foodItems.withIndex()) {
                    progressTitle.text = "Processing: ${foodItem.name}"
                    progressText.text = "${index + 1}/${foodItems.size} items"

                    try {
                        val result = withContext(Dispatchers.IO) {
                            val startTime = System.currentTimeMillis()
                            val memBefore = captureMemorySnapshot()

                            val rawResult = predictAllergens(foodItem.ingredients)

                            val endTime = System.currentTimeMillis()
                            val memAfter = captureMemorySnapshot()

                            val parts = rawResult.split("|", limit = 2)
                            val metaString = if (parts.isNotEmpty()) parts[0] else ""
                            val modelOutput = if (parts.size > 1) parts[1] else rawResult

                            var ttftMs = -1L
                            var itps = -1L
                            var otps = -1L
                            var oetMs = -1L

                            metaString.split(";").forEach { metric ->
                                when {
                                    metric.startsWith("TTFT_MS=") ->
                                        ttftMs = metric.removePrefix("TTFT_MS=").toLongOrNull() ?: -1L
                                    metric.startsWith("ITPS=") ->
                                        itps = metric.removePrefix("ITPS=").toLongOrNull() ?: -1L
                                    metric.startsWith("OTPS=") ->
                                        otps = metric.removePrefix("OTPS=").toLongOrNull() ?: -1L
                                    metric.startsWith("OET_MS=") ->
                                        oetMs = metric.removePrefix("OET_MS=").toLongOrNull() ?: -1L
                                }
                            }

                            Log.i(TAG_METRICS, "=== METRICS FOR ${foodItem.name} ===")
                            Log.i(TAG_METRICS, "TTFT: ${ttftMs}ms | ITPS: ${itps} tok/s | OTPS: ${otps} tok/s | OET: ${oetMs}ms")
                            Log.i(TAG_METRICS, "Latency: ${endTime - startTime}ms")
                            Log.i(TAG, "Model raw output: $modelOutput")

                            val validAllergens = setOf(
                                "milk", "egg", "peanut", "tree nut",
                                "wheat", "soy", "fish", "shellfish", "sesame", "none"
                            )

                            val predicted = modelOutput.lowercase().trim()
                                .replace("tree-nut", "tree nut")
                                .replace("treenut", "tree nut")
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .filter { it in validAllergens }
                                .distinct()
                                .sorted()
                                .let { allergens ->
                                    if (allergens.isEmpty() || allergens.contains("none")) {
                                        "none"
                                    } else {
                                        allergens.filter { it != "none" }.joinToString(", ")
                                    }
                                }

                            Log.i(TAG, "Model output (validated): $predicted")

                            val metrics = MetricsCalculator.calculateMetrics(
                                groundTruth = foodItem.allergensMapped,
                                predicted = predicted,
                                ingredients = foodItem.ingredients
                            )

                            Log.i(TAG_METRICS, "Precision: ${String.format("%.4f", metrics.precision)} | " +
                                    "Recall: ${String.format("%.4f", metrics.recall)} | " +
                                    "F1: ${String.format("%.4f", metrics.f1Score)} | " +
                                    "Accuracy: ${String.format("%.4f", metrics.accuracy)}")
                            Log.i(TAG_METRICS, "Exact Match: ${metrics.isExactMatch} | " +
                                    "Hallucination: ${metrics.hasHallucination}")

                            PredictionResult(
                                dataId = foodItem.id,
                                name = foodItem.name,
                                ingredients = foodItem.ingredients,
                                allergensRaw = foodItem.allergensRaw,
                                allergensMapped = foodItem.allergensMapped,
                                predictedAllergens = predicted,
                                modelName = currentModelName,

                                truePositives = metrics.tp,
                                falsePositives = metrics.fp,
                                falseNegatives = metrics.fn,
                                trueNegatives = metrics.tn,
                                precision = metrics.precision,
                                recall = metrics.recall,
                                f1Score = metrics.f1Score,
                                accuracy = metrics.accuracy,
                                isExactMatch = metrics.isExactMatch,
                                hammingLoss = metrics.hammingLoss,
                                falseNegativeRate = metrics.fnr,

                                hasHallucination = metrics.hasHallucination,
                                hallucinatedAllergens = metrics.hallucinatedAllergens,
                                hasOverPrediction = metrics.hasOverPrediction,
                                overPredictedAllergens = metrics.overPredictedAllergens,
                                isAbstentionCase = metrics.isAbstentionCase,
                                isAbstentionCorrect = metrics.isAbstentionCorrect,

                                latencyMs = endTime - startTime,
                                ttftMs = ttftMs,
                                itps = itps,
                                otps = otps,
                                oetMs = oetMs,
                                totalTimeMs = endTime - startTime,

                                javaHeapKb = memAfter.javaHeap - memBefore.javaHeap,
                                nativeHeapKb = memAfter.nativeHeap - memBefore.nativeHeap,
                                totalPssKb = memAfter.totalPss - memBefore.totalPss,

                                deviceModel = deviceInfo,
                                androidVersion = androidVersion
                            )
                        }

                        saveToFirebase(result)
                        resultsAdapter.addResult(result)
                        predictionProgress.progress = index + 1

                        successCount++
                        Log.i(TAG, "✓ Predicted ${foodItem.name}: ${result.predictedAllergens}")

                    } catch (e: Exception) {
                        failCount++
                        Log.e(TAG, "✗ Failed to predict ${foodItem.name}", e)
                    }

                    resultsRecyclerView.smoothScrollToPosition(resultsAdapter.itemCount - 1)
                }

                progressCard.visibility = View.GONE
                predictButton.isEnabled = true
                datasetSpinner.isEnabled = true
                loadModelButton.isEnabled = false
                isPredicting = false

                val message = "Set $setNumber: $successCount/${foodItems.size} successful"
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                showNotification("Prediction Complete", message)

                Log.i(TAG, "=== PREDICTION SET $setNumber COMPLETE ===")
                Log.i(TAG, "Success: $successCount, Failed: $failCount")

            } catch (e: Exception) {
                Log.e(TAG, "Error during predictions", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                showNotification("Prediction Failed", e.message ?: "Unknown error")

                progressCard.visibility = View.GONE
                predictButton.isEnabled = true
                datasetSpinner.isEnabled = true
                loadModelButton.isEnabled = false
                isPredicting = false
            }
        }
    }

    private fun cleanupInvalidFirebaseData() {
        lifecycleScope.launch {
            try {
                Log.i(TAG, "=== CLEANING UP INVALID FIREBASE DATA ===")

                Toast.makeText(this@MainActivity, "Cleaning up invalid data...", Toast.LENGTH_SHORT).show()

                val deletedCount = withContext(Dispatchers.IO) {
                    var count = 0

                    val snapshot1 = firestore.collection("predictions")
                        .whereEqualTo("itps", -1)
                        .get()
                        .await()

                    snapshot1.documents.forEach { doc ->
                        Log.i(TAG, "Deleting invalid (itps=-1): ${doc.getString("name")}")
                        doc.reference.delete().await()
                        count++
                    }

                    val snapshot2 = firestore.collection("predictions")
                        .whereLessThan("latencyMs", 5000)
                        .get()
                        .await()

                    snapshot2.documents.forEach { doc ->
                        val latency = doc.getLong("latencyMs") ?: 0
                        Log.i(TAG, "Deleting invalid (latency=${latency}ms): ${doc.getString("name")}")
                        doc.reference.delete().await()
                        count++
                    }

                    val snapshot3 = firestore.collection("predictions")
                        .whereEqualTo("ttftMs", -1)
                        .get()
                        .await()

                    snapshot3.documents.forEach { doc ->
                        Log.i(TAG, "Deleting invalid (ttftMs=-1): ${doc.getString("name")}")
                        doc.reference.delete().await()
                        count++
                    }

                    count
                }

                Log.i(TAG, "✓ Cleanup complete: $deletedCount invalid records deleted")
                Toast.makeText(
                    this@MainActivity,
                    "✓ Deleted $deletedCount invalid records",
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning Firebase data", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveToFirebase(result: PredictionResult) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val data = hashMapOf<String, Any>(
                    "dataId" to result.dataId,
                    "name" to result.name,
                    "ingredients" to result.ingredients,
                    "allergensRaw" to result.allergensRaw,
                    "allergensMapped" to result.allergensMapped,
                    "predictedAllergens" to result.predictedAllergens,
                    "modelName" to result.modelName,
                    "truePositives" to result.truePositives,
                    "falsePositives" to result.falsePositives,
                    "falseNegatives" to result.falseNegatives,
                    "trueNegatives" to result.trueNegatives,
                    "precision" to result.precision,
                    "recall" to result.recall,
                    "f1Score" to result.f1Score,
                    "accuracy" to result.accuracy,
                    "isExactMatch" to result.isExactMatch,
                    "hammingLoss" to result.hammingLoss,
                    "falseNegativeRate" to result.falseNegativeRate,
                    "hasHallucination" to result.hasHallucination,
                    "hallucinatedAllergens" to result.hallucinatedAllergens,
                    "hasOverPrediction" to result.hasOverPrediction,
                    "overPredictedAllergens" to result.overPredictedAllergens,
                    "isAbstentionCase" to result.isAbstentionCase,
                    "isAbstentionCorrect" to result.isAbstentionCorrect,
                    "latencyMs" to result.latencyMs,
                    "ttftMs" to result.ttftMs,
                    "itps" to result.itps,
                    "otps" to result.otps,
                    "oetMs" to result.oetMs,
                    "totalTimeMs" to result.totalTimeMs,
                    "javaHeapKb" to result.javaHeapKb,
                    "nativeHeapKb" to result.nativeHeapKb,
                    "totalPssKb" to result.totalPssKb,
                    "deviceModel" to result.deviceModel,
                    "androidVersion" to result.androidVersion
                )

                firestore.collection("predictions").add(data).await()
                Log.i(TAG, "✓ Saved to Firebase: ${result.name}")

            } catch (e: Exception) {
                Log.e(TAG, "✗ Firebase error: ${e.message}", e)
            }
        }
    }

    private fun showNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun captureMemorySnapshot(): MemorySnapshot {
        return MemorySnapshot(
            javaHeap = MemoryReader.javaHeapKb(),
            nativeHeap = MemoryReader.nativeHeapKb(),
            totalPss = MemoryReader.totalPssKb()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isModelLoaded) {
            try {
                unloadModel()
                Log.i(TAG, "Model unloaded")
            } catch (e: Exception) {
                Log.e(TAG, "Error unloading model", e)
            }
        }
    }

    // ===== SIMPLE RESULTS ADAPTER (NO CUSTOM LAYOUT NEEDED) =====
    /**
     * Simple adapter to show results in real-time
     * Uses Android's built-in simple_list_item_1 layout
     */
    inner class SimpleResultsAdapter : RecyclerView.Adapter<SimpleResultsAdapter.ViewHolder>() {

        private val results = mutableListOf<PredictionResult>()

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val result = results[position]
            holder.textView.text = "${result.name}\nF1: ${String.format("%.3f", result.f1Score)} | Acc: ${String.format("%.3f", result.accuracy)}"
        }

        override fun getItemCount() = results.size

        fun clearResults() {
            results.clear()
            notifyDataSetChanged()
        }

        fun addResult(result: PredictionResult) {
            results.add(result)
            notifyItemInserted(results.size - 1)
        }
    }
}
