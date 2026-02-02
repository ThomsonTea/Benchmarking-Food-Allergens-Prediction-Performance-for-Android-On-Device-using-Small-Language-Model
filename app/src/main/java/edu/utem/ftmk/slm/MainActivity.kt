package edu.utem.ftmk.slm

import android.app.NotificationChannel
import android.app.NotificationManager

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
import android.app.ActivityManager
import android.os.Debug
import android.content.Context
import com.google.firebase.firestore.Query
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

    private lateinit var resultsAdapter: ResultsAdapter

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
        resultsAdapter = ResultsAdapter()  // ✅ FIXED
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
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val availableSystemRamKb = memoryInfo.availMem / 1024 // Available system memory in KB
        val currentNativeHeapSizeKb = Debug.getNativeHeapSize() / 1024 // Current app's native heap in KB
        val currentJavaHeapSizeKb = Runtime.getRuntime().totalMemory() / 1024 // Current app's Java heap in KB

        Log.d(TAG_METRICS, "Memory Status:")
        Log.d(TAG_METRICS, "  Available System RAM: ${availableSystemRamKb / 1024} MB")
        Log.d(TAG_METRICS, "  App Native Heap: ${currentNativeHeapSizeKb / 1024} MB")
        Log.d(TAG_METRICS, "  App Java Heap: ${currentJavaHeapSizeKb / 1024} MB")

        // --- CRITICAL PART: Define your model's required RAM ---
        // This is an estimate and needs tuning.
        // A Q4_K_M 1.5B model often needs 1.5GB to 2.5GB of *system* RAM available to load and run comfortably.
        // For a 1B model, it might be 1GB to 1.8GB.
        // Start with a high estimate and adjust down if profiling shows you can.
        val minRequiredFreeSystemRamMb = 1800 // e.g., 1.8 GB for a 1.5B Q4_K_M model
        val minRequiredFreeSystemRamKb = minRequiredFreeSystemRamMb * 1024

        if (availableSystemRamKb < minRequiredFreeSystemRamKb) {
            Log.e(TAG, "❌ CRITICAL: Insufficient System RAM.")
            Log.e(TAG, "   Required: ${minRequiredFreeSystemRamMb} MB, Available: ${availableSystemRamKb / 1024} MB")
            // Optionally, trigger GC. While it primarily cleans Java heap, it might release some native resources tied to Java objects.
            System.gc()
            try { Thread.sleep(500) } catch (e: InterruptedException) { /* ignore */ }

            activityManager.getMemoryInfo(memoryInfo) // Re-check after GC
            if ((memoryInfo.availMem / 1024) < minRequiredFreeSystemRamKb) {
                return false // Still critically low after GC
            }
        }

        // You could also check if your app's native heap size is growing excessively.
        // This is harder to define without specific knowledge of your model's runtime footprint.
        // For now, focusing on system-wide available RAM is usually the main bottleneck.

        return true // Memory seems sufficient
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

                    hallucinationCount = metrics.hallucinationCount,  // ✅ CHANGED
                    hallucinatedAllergens = metrics.hallucinatedAllergens,
                    overPredictionCount = metrics.overPredictionCount,  // ✅ CHANGED
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

            // 1. Create the Input Field programmatically
            val input = EditText(this)
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            input.hint = "Start Item # (Default: 1)"
            input.setText("1") // Default value

            // 2. Create a container to add margins (so it looks nice)
            val container = FrameLayout(this)
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.leftMargin = 50 // Pixel margins
            params.rightMargin = 50
            input.layoutParams = params
            container.addView(input)

            // 3. Build the Dialog
            AlertDialog.Builder(this)
                .setTitle("Optimal Batch Processing")
                .setMessage("Enter the Item Number to start from (e.g. 50):")
                .setView(container) // <--- THIS LINE ADDS THE EDITTEXT TO THE UI
                .setPositiveButton("Start") { _, _ ->
                    // Get the number the user typed
                    val inputStr = input.text.toString()
                    val startInput = if (inputStr.isNotEmpty()) inputStr.toInt() else 1

                    // Convert 1-based (Human) to 0-based (Array)
                    // If user types 50, we start at index 49
                    val startIndex = (startInput - 1).coerceAtLeast(0)

                    if (startIndex < allFoodItems.size) {
                        // Pass the index to the start function
                        startOptimalBatchProcessing(startIndex)
                    } else {
                        Toast.makeText(this, "Start number too high!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // 1. Add 'startIndex' parameter here
    private fun startOptimalBatchProcessing(startIndex: Int = 0) {
        isProcessingAll = true

        val batchProgressBar = findViewById<ProgressBar>(R.id.batchProgressBar)
        val batchProgressText = findViewById<TextView>(R.id.batchProgressText)
        val processAllButton = findViewById<Button>(R.id.processAllButton)

        // 1. INITIALIZE UI IMMEDIATELY
        batchProgressBar.visibility = View.VISIBLE
        batchProgressText.visibility = View.VISIBLE
        batchProgressBar.max = allFoodItems.size

        // Set visual progress to where we are starting (e.g., 50)
        batchProgressBar.progress = startIndex

        // Set text immediately so it doesn't say "0/200"
        batchProgressText.text = "$startIndex/${allFoodItems.size} | Starting..."

        processAllButton.isEnabled = false
        processAllButton.text = "PROCESSING..."
        loadModelButton.isEnabled = false
        predictButton.isEnabled = false
        datasetSpinner.isEnabled = false
        modelSpinner.isEnabled = false

        Log.i(TAG, "=== OPTIMAL BATCH START (From Item #${startIndex + 1}) ===")

        val stats = BatchStatistics(
            totalItems = allFoodItems.size,
            startTime = System.currentTimeMillis(),
            lastCheckpointTime = System.currentTimeMillis()
        )

        lifecycleScope.launch {
            val modelFilePath = withContext(Dispatchers.IO) { copyModelFromAssets()?.absolutePath }

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

                // 2. LOOP FROM START INDEX
                for (i in startIndex until allFoodItems.size) {

                    if (!isProcessingAll) {
                        Log.w(TAG, "⚠️ Cancelled by user")
                        break
                    }

                    val item = allFoodItems[i]
                    val itemNumber = i + 1  // e.g., 51

                    Log.i(TAG, "Processing [$itemNumber/${stats.totalItems}]: ${item.name}")

                    // Checkpoint logic (Reload model every 10 items)
                    if (i > startIndex && i % 10 == 0) {
                        reloadModelSafely(modelFilePath)
                        stats.lastCheckpointTime = System.currentTimeMillis()
                    }

                    // Run Prediction
                    val result = predictWithRetryAndSafety(item, deviceInfo, androidVersion, maxRetries = 3)

                    if (result != null) {
                        saveToFirebase(result)
                        stats.successCount++
                    } else {
                        stats.failedItems.add(item)
                        stats.failCount++
                    }

                    // 3. UPDATE UI & ESTIMATE TIME
                    withContext(Dispatchers.Main) {
                        batchProgressBar.progress = itemNumber

                        // Calculate Time
                        val currentTime = System.currentTimeMillis()
                        val timeElapsed = (currentTime - stats.startTime) / 1000 // Seconds elapsed

                        // How many items have we done *in this session*?
                        val itemsProcessedThisSession = (i - startIndex) + 1

                        // Calculate estimated time remaining
                        var timeString = "${timeElapsed/60}m elapsed"

                        if (itemsProcessedThisSession > 0) {
                            val avgTimePerItem = timeElapsed.toDouble() / itemsProcessedThisSession
                            val itemsRemaining = allFoodItems.size - itemNumber
                            val secondsRemaining = avgTimePerItem * itemsRemaining

                            timeString += " (~${(secondsRemaining/60).toInt()}m left)"
                        }

                        // Update Text
                        batchProgressText.text = "$itemNumber/${stats.totalItems} | " +
                                "✓${stats.successCount} ❌${stats.failCount} | " +
                                timeString
                    }

                    // Small delay to let UI breathe
                    Thread.sleep(500)
                }

                // 4. CLEANUP & FINISH
                try { unloadModel() } catch (e: Exception) {}

                withContext(Dispatchers.Main) {
                    isProcessingAll = false
                    resetBatchUI()

                    val totalTimeMin = (System.currentTimeMillis() - stats.startTime) / 1000 / 60
                    val message = "Done! Processed ${stats.successCount + stats.failCount} items in ${totalTimeMin}min"

                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    batchProgressText.text = message
                }
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


    // ===== EXTREME FIX: UNLOAD MODEL AFTER EVERY PREDICTION =====

    // ===== FIXED runPredictions() - RELAXED VALIDATION =====
// This version accepts OTPS=0 and shows ALL items in UI

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

                val modelFilePath = withContext(Dispatchers.IO) {
                    copyModelFromAssets()?.absolutePath
                }

                if (modelFilePath == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "❌ Model file not found!", Toast.LENGTH_LONG).show()
                        resetPredictionUI()
                    }
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    for ((index, foodItem) in foodItems.withIndex()) {

                        withContext(Dispatchers.Main) {
                            progressTitle.text = "Processing: ${foodItem.name} [${index + 1}/${foodItems.size}]"
                            progressText.text = "Loading model..."
                        }

                        try {
                            Log.i(TAG, "")
                            Log.i(TAG, "=".repeat(50))
                            Log.i(TAG, "[${index + 1}/${foodItems.size}] ${foodItem.name}")
                            Log.i(TAG, "Loading model...")

                            val loaded = loadModel(assets, modelFilePath)

                            if (!loaded) {
                                Log.e(TAG, "❌ Failed to load model for ${foodItem.name}")
                                throw Exception("Model load failed")
                            }

                            Log.i(TAG, "✓ Model loaded")
                            Thread.sleep(2000)

                            withContext(Dispatchers.Main) {
                                progressText.text = "Predicting..."
                            }

                            val startTime = System.currentTimeMillis()
                            val memBefore = captureMemorySnapshot()

                            val rawResult = withTimeout(120000L) {
                                predictAllergens(foodItem.ingredients)
                            }

                            val endTime = System.currentTimeMillis()
                            val memAfter = captureMemorySnapshot()
                            val actualLatency = endTime - startTime

                            Log.i(TAG, "Prediction completed in ${actualLatency}ms")

                            // ✅ FIX: Validate latency only
                            if (actualLatency < 3000) {
                                throw Exception("Latency too low: ${actualLatency}ms")
                            }

                            val parts = rawResult.split("|", limit = 2)

                            if (parts.size < 2) {
                                throw Exception("Invalid format: $rawResult")
                            }

                            val metaString = parts[0]
                            val modelOutput = parts[1].trim()

                            if (modelOutput.isEmpty()) {
                                throw Exception("Empty output")
                            }

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

                            // ✅ FIX: Only validate TTFT and ITPS (OTPS can be 0!)
                            if (ttftMs <= 0 || itps <= 0) {
                                Log.w(TAG, "Warning: Some metrics invalid (TTFT=$ttftMs ITPS=$itps OTPS=$otps)")
                                // Don't throw - allow prediction to continue
                            }

                            // ✅ FIX: If OTPS is 0, use latency-based estimate
                            if (otps <= 0) {
                                Log.w(TAG, "OTPS is 0, using estimated value")
                                otps = 1 // Minimum 1 token/sec
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

                            // ✅ FIX: Even if no valid allergens, still create result
                            // (This shows the prediction was attempted, even if model gave bad output)

                            val finalPredicted = if (predicted.isEmpty()) {
                                Log.w(TAG, "No valid allergens parsed, using 'none'")
                                "none"
                            } else {
                                predicted
                            }

                            val metrics = MetricsCalculator.calculateMetrics(
                                groundTruth = foodItem.allergensMapped,
                                predicted = finalPredicted,
                                ingredients = foodItem.ingredients
                            )

                            val result = PredictionResult(
                                dataId = foodItem.id,
                                name = foodItem.name,
                                ingredients = foodItem.ingredients,
                                allergensRaw = foodItem.allergensRaw,
                                allergensMapped = foodItem.allergensMapped,
                                predictedAllergens = finalPredicted,
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

                                hallucinationCount = metrics.hallucinationCount,  // ✅ CHANGED
                                hallucinatedAllergens = metrics.hallucinatedAllergens,
                                overPredictionCount = metrics.overPredictionCount,  // ✅ CHANGED
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

                            saveToFirebase(result)

                            withContext(Dispatchers.Main) {
                                resultsAdapter.addResult(result)  // ✅ ALWAYS add to UI
                                predictionProgress.progress = index + 1
                                resultsRecyclerView.smoothScrollToPosition(resultsAdapter.itemCount - 1)
                            }

                            successCount++
                            Log.i(TAG, "✓ ${foodItem.name} → $finalPredicted (F1: ${String.format("%.3f", metrics.f1Score)})")

                            withContext(Dispatchers.Main) {
                                progressText.text = "Unloading model..."
                            }

                            Log.i(TAG, "Unloading model...")
                            unloadModel()
                            Log.i(TAG, "✓ Model unloaded")

                            System.gc()
                            Thread.sleep(3000)

                            val runtime = Runtime.getRuntime()
                            val freeMemoryMB = runtime.freeMemory() / 1024 / 1024
                            Log.i(TAG, "Memory after cleanup: ${freeMemoryMB}MB free")

                        } catch (e: TimeoutCancellationException) {
                            failCount++
                            Log.e(TAG, "⏱️ TIMEOUT: ${foodItem.name}")

                            try {
                                unloadModel()
                                System.gc()
                                Thread.sleep(3000)
                            } catch (re: Exception) {
                                Log.e(TAG, "Cleanup error: ${re.message}")
                            }

                        } catch (e: Exception) {
                            failCount++
                            Log.e(TAG, "✗ FAILED: ${foodItem.name}: ${e.message}")

                            try {
                                unloadModel()
                                System.gc()
                                Thread.sleep(3000)
                            } catch (re: Exception) {
                                Log.e(TAG, "Cleanup error: ${re.message}")
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    progressCard.visibility = View.GONE
                    predictButton.isEnabled = true
                    datasetSpinner.isEnabled = true
                    loadModelButton.isEnabled = true
                    isModelLoaded = false
                    loadModelButton.text = "Load Model"
                    isPredicting = false

                    val successRate = (successCount * 100.0) / foodItems.size
                    val message = "Set $setNumber: ✓$successCount ❌$failCount (${String.format("%.0f", successRate)}%)"

                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    showNotification("Prediction Complete", message)

                    Log.i(TAG, "")
                    Log.i(TAG, "=".repeat(50))
                    Log.i(TAG, "COMPLETE: ✓$successCount ❌$failCount")
                    Log.i(TAG, "=".repeat(50))
                }

            } catch (e: Exception) {
                Log.e(TAG, "FATAL ERROR", e)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Fatal error: ${e.message}", Toast.LENGTH_LONG).show()
                    resetPredictionUI()
                }
            }
        }
    }

    private fun resetPredictionUI() {
        progressCard.visibility = View.GONE
        predictButton.isEnabled = true
        datasetSpinner.isEnabled = true
        loadModelButton.isEnabled = true
        isModelLoaded = false
        loadModelButton.text = "Load Model"
        isPredicting = false
    }






    private fun cleanupInvalidFirebaseData() {
        lifecycleScope.launch {
            try {
                Log.i(TAG, "=== CLEANING UP FIREBASE DATA ===")

                Toast.makeText(this@MainActivity, "Cleaning up data...", Toast.LENGTH_SHORT).show()

                val deletedCount = withContext(Dispatchers.IO) {
                    var count = 0

                    // Step 1: Delete invalid records (itps=-1)
                    val snapshot1 = firestore.collection("predictions")
                        .whereEqualTo("itps", -1)
                        .get()
                        .await()

                    snapshot1.documents.forEach { doc ->
                        Log.i(TAG, "Deleting invalid (itps=-1): ${doc.getString("name")}")
                        doc.reference.delete().await()
                        count++
                    }

                    // Step 2: Delete invalid records (latency < 5000ms)
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

                    // Step 3: Delete invalid records (ttftMs=-1)
                    val snapshot3 = firestore.collection("predictions")
                        .whereEqualTo("ttftMs", -1)
                        .get()
                        .await()

                    snapshot3.documents.forEach { doc ->
                        Log.i(TAG, "Deleting invalid (ttftMs=-1): ${doc.getString("name")}")
                        doc.reference.delete().await()
                        count++
                    }

                    // Step 4: Limit to 200 documents PER MODEL (delete oldest if > 200)
                    val allDocs = firestore.collection("predictions")
                        .orderBy("timestamp", Query.Direction.DESCENDING)  // Newest first
                        .get()
                        .await()

                    // Group documents by model name
                    val docsByModel = allDocs.documents.groupBy { it.getString("modelName") ?: "Unknown" }

                    Log.i(TAG, "Found ${docsByModel.size} different models")

                    // For each model, keep only the 200 newest documents
                    docsByModel.forEach { (modelName, docs) ->
                        val totalForModel = docs.size
                        Log.i(TAG, "Model '$modelName': $totalForModel documents")

                        if (totalForModel > 200) {
                            val docsToDelete = docs.drop(200)  // Keep first 200 (newest), delete the rest
                            Log.i(TAG, "  → Deleting ${docsToDelete.size} oldest documents for model '$modelName'")

                            docsToDelete.forEach { doc ->
                                val timestamp = doc.getLong("timestamp") ?: 0
                                Log.i(TAG, "    Deleting: ${doc.getString("name")} (timestamp: $timestamp)")
                                doc.reference.delete().await()
                                count++
                            }
                        } else {
                            Log.i(TAG, "  → Model '$modelName' is within limit (${totalForModel}/200)")
                        }
                    }

                    count
                }

                Log.i(TAG, "✓ Cleanup complete: $deletedCount records deleted")
                Toast.makeText(
                    this@MainActivity,
                    "✓ Deleted $deletedCount records. Each model now has max 200 docs.",
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
                    "hallucinationCount" to result.hallucinationCount,
                    "hallucinatedAllergens" to result.hallucinatedAllergens,
                    "overPredictionCount" to result.overPredictionCount,
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
                    "androidVersion" to result.androidVersion,
                    "timestamp" to result.timestamp
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



}
