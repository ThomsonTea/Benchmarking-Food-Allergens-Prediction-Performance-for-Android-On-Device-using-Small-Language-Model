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
<<<<<<< HEAD
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.net.Uri
import android.provider.Settings

=======
>>>>>>> origin/main

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SLM_MAIN"
        private const val TAG_METRICS = "SLM_METRICS"
        private const val MODEL_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        private const val EXCEL_FILE = "foodpreprocessed.xlsx"
        private const val CHANNEL_ID = "allergen_predictions"
        private const val NOTIFICATION_ID = 1

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

    // Native function declarations
    external fun loadModel(assetManager: android.content.res.AssetManager, modelPath: String): Boolean
    external fun predictAllergens(ingredients: String): String
    external fun getModelInfo(): String
    external fun unloadModel()

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
    private lateinit var resultsAdapter: ResultsAdapter

<<<<<<< HEAD
    private lateinit var modelSpinner: Spinner

    private var currentModelName: String = "Qwen 2.5 1.5B"
    private var currentModelFile: String = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    private val resultsByModel = mutableMapOf<String, MutableList<PredictionResult>>()
    private lateinit var dashboardButton: Button  // ← ADD THIS


=======
>>>>>>> origin/main
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

<<<<<<< HEAD
        requestStoragePermission()

=======
>>>>>>> origin/main
        Log.i(TAG, "=== MainActivity onCreate ===")

        initializeViews()
        setupRecyclerView()
        createNotificationChannel()

        // Load dataset in background
        lifecycleScope.launch {
            loadFoodDataset()
        }
<<<<<<< HEAD
        // Setup model spinner
        val modelNames = ModelRegistry.getDisplayNames()
        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelNames)
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = modelAdapter
=======
>>>>>>> origin/main

        setupClickListeners()
    }

<<<<<<< HEAD
    /**
     * Request storage permission (Android 11+ compatible)
     */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - Need to request MANAGE_EXTERNAL_STORAGE
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
            // Android 6-10
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

    /**
     * Handle permission result
     */
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

    /**
     * Handle result from Settings activity (Android 11+)
     */
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
=======
    private fun initializeViews() {
        modelStatusText = findViewById(R.id.modelStatusText)
>>>>>>> origin/main
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
<<<<<<< HEAD
        dashboardButton = findViewById(R.id.dashboardButton)
=======
>>>>>>> origin/main
    }

    private fun setupRecyclerView() {
        resultsAdapter = ResultsAdapter()
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
<<<<<<< HEAD
        dashboardButton.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
        }
=======
>>>>>>> origin/main

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
<<<<<<< HEAD
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
=======
>>>>>>> origin/main
    }

    /**
     * Safely get cell value from Excel, handling formulas
     */
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

    /**
     * Load food dataset from Excel file
     */
    private suspend fun loadFoodDataset() = withContext(Dispatchers.IO) {
        Log.i(TAG, "=== Loading Food Dataset ===")

        try {
            val inputStream = assets.open(EXCEL_FILE)
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)

            Log.i(TAG, "Reading Excel file...")

            // Skip header row (row 0)
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

            // Divide into 20 groups of 10 items each
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

    /**
     * Setup spinner with dataset options
     */
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

    /**
     * Load AI model
     */
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

    /**
<<<<<<< HEAD
     * Load model from external storage (Documents/SLM_Models/)
     */
    private fun copyModelFromAssets(): File? {
        try {
            Log.i(TAG, "Looking for model: $currentModelFile")

            // Option 1: Documents/SLM_Models/
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val modelsDir = File(documentsDir, "SLM_Models")
            val modelFile = File(modelsDir, currentModelFile)

            Log.i(TAG, "Checking: ${modelFile.absolutePath}")

            if (modelFile.exists()) {
                val sizeMB = modelFile.length() / 1024 / 1024
                Log.i(TAG, "✓ Model found! Size: ${sizeMB}MB")
                return modelFile
            }

            // Option 2: Download/SLM_Models/
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val downloadModelsDir = File(downloadDir, "SLM_Models")
            val downloadModelFile = File(downloadModelsDir, currentModelFile)

            Log.i(TAG, "Checking: ${downloadModelFile.absolutePath}")

            if (downloadModelFile.exists()) {
                val sizeMB = downloadModelFile.length() / 1024 / 1024
                Log.i(TAG, "✓ Model found! Size: ${sizeMB}MB")
                return downloadModelFile
            }

            // Model not found
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
=======
     * Copy model from assets to internal storage
     */
    private fun copyModelFromAssets(): File? {
        try {
            val outputFile = File(filesDir, MODEL_NAME)

            if (outputFile.exists()) {
                Log.i(TAG, "Model already exists")
                return outputFile
            }

            Log.i(TAG, "Copying model...")

            assets.open(MODEL_NAME).use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }

            return outputFile

        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model", e)
>>>>>>> origin/main
            return null
        }
    }

    /**
     * Run predictions on a dataset group
     */
    private fun runPredictions(foodItems: List<FoodItem>, setNumber: Int) {
        lifecycleScope.launch {
            try {
                isPredicting = true

                // Clear previous results
                resultsAdapter.clearResults()

                // Show UI elements
                progressCard.visibility = View.VISIBLE
                resultsTitle.visibility = View.VISIBLE
                resultsRecyclerView.visibility = View.VISIBLE

                // Disable controls
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
                        // Run prediction in background
                        val result = withContext(Dispatchers.IO) {
                            val startTime = System.currentTimeMillis()
                            val memBefore = captureMemorySnapshot()

                            // ================= CALL NATIVE PREDICTION =================
                            val rawResult = predictAllergens(foodItem.ingredients)

                            val endTime = System.currentTimeMillis()
                            val memAfter = captureMemorySnapshot()

                            // ================= PARSE METRICS =================
<<<<<<< HEAD
=======
                            // Expected format: TTFT_MS=<value>;ITPS=<value>;OTPS=<value>;OET_MS=<value>|<output>
>>>>>>> origin/main
                            val parts = rawResult.split("|", limit = 2)
                            val metaString = if (parts.isNotEmpty()) parts[0] else ""
                            val modelOutput = if (parts.size > 1) parts[1] else rawResult

                            // Parse individual metrics
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

<<<<<<< HEAD
                            Log.i(TAG_METRICS, "=== METRICS FOR ${foodItem.name} ===")
                            Log.i(TAG_METRICS, "TTFT: ${ttftMs}ms | ITPS: ${itps} tok/s | OTPS: ${otps} tok/s | OET: ${oetMs}ms")
                            Log.i(TAG_METRICS, "Latency: ${endTime - startTime}ms")

                            // Log model output
                            Log.i(TAG, "Model raw output: $modelOutput")

                            // ================= USE MODEL OUTPUT (with validation) =================
=======
                            // Log metrics
                            Log.i(TAG_METRICS, "=== METRICS FOR ${foodItem.name} ===")
                            Log.i(TAG_METRICS, "TTFT: ${ttftMs}ms | ITPS: ${itps} tok/s | OTPS: ${otps} tok/s | OET: ${oetMs}ms")
                            Log.i(TAG_METRICS, "Latency: ${endTime - startTime}ms | Memory: Java=${memAfter.javaHeap - memBefore.javaHeap}KB, Native=${memAfter.nativeHeap - memBefore.nativeHeap}KB")

// Log model output
                            Log.i(TAG, "Model raw output: $modelOutput")

                            // ================= SIMPLIFIED POST-PROCESSING =================
                            // Model now follows prompt rules, so we just validate!

>>>>>>> origin/main
                            val validAllergens = setOf(
                                "milk", "egg", "peanut", "tree nut",
                                "wheat", "soy", "fish", "shellfish", "sesame", "none"
                            )

<<<<<<< HEAD
                            // Clean model output
                            val predicted = modelOutput.lowercase().trim()
                                .replace("tree-nut", "tree nut")
                                .replace("treenut", "tree nut")
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .filter { it in validAllergens }
                                .distinct()
                                .sorted()
=======
                            // Light cleaning - model should already output correctly!
                            val predicted = modelOutput.lowercase().trim()
                                .replace("tree-nut", "tree nut")    // Just in case
                                .replace("treenut", "tree nut")     // Just in case
                                .split(",")                         // Split by comma
                                .map { it.trim() }                  // Trim spaces
                                .filter { it.isNotEmpty() }         // Remove empty
                                .filter { it in validAllergens }    // Only valid allergens
                                .distinct()                         // Remove duplicates
                                .sorted()                           // Alphabetically sort
>>>>>>> origin/main
                                .let { allergens ->
                                    if (allergens.isEmpty() || allergens.contains("none")) {
                                        "none"
                                    } else {
                                        allergens.filter { it != "none" }.joinToString(", ")
                                    }
                                }

                            Log.i(TAG, "Model output (validated): $predicted")

<<<<<<< HEAD
                            // ================= CALCULATE ALL METRICS =================
                            val metrics = MetricsCalculator.calculateMetrics(
                                groundTruth = foodItem.allergensMapped,
                                predicted = predicted,
                                ingredients = foodItem.ingredients
                            )

                            Log.i(TAG_METRICS, "Precision: ${String.format("%.4f", metrics.precision)} | " +
                                    "Recall: ${String.format("%.4f", metrics.recall)} | " +
                                    "F1: ${String.format("%.4f", metrics.f1Score)}")
                            Log.i(TAG_METRICS, "Exact Match: ${metrics.isExactMatch} | " +
                                    "Hallucination: ${metrics.hasHallucination}")
                            // ====================================================================

                            PredictionResult(
                                // Basic info
=======
                            // Calculate accuracy
                            val expected = foodItem.allergensMapped.lowercase()
                                .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                            val predictedSet = predicted.split(",").map { it.trim() }.filter { it != "none" }.toSet()
                            val isCorrect = expected == predictedSet

                            Log.i(TAG_METRICS, "Expected: $expected | Predicted: $predictedSet | Match: $isCorrect")
                            // ====================================================================

                            PredictionResult(
>>>>>>> origin/main
                                dataId = foodItem.id,
                                name = foodItem.name,
                                ingredients = foodItem.ingredients,
                                allergensRaw = foodItem.allergensRaw,
                                allergensMapped = foodItem.allergensMapped,
<<<<<<< HEAD
                                predictedAllergens = predicted,
                                modelName = currentModelName,  // ← Add current model name

                                // Prediction quality metrics
                                truePositives = metrics.tp,
                                falsePositives = metrics.fp,
                                falseNegatives = metrics.fn,
                                trueNegatives = metrics.tn,
                                precision = metrics.precision,
                                recall = metrics.recall,
                                f1Score = metrics.f1Score,
                                isExactMatch = metrics.isExactMatch,
                                hammingLoss = metrics.hammingLoss,
                                falseNegativeRate = metrics.fnr,

                                // Safety metrics
                                hasHallucination = metrics.hasHallucination,
                                hallucinatedAllergens = metrics.hallucinatedAllergens,
                                hasOverPrediction = metrics.hasOverPrediction,
                                overPredictedAllergens = metrics.overPredictedAllergens,
                                isAbstentionCase = metrics.isAbstentionCase,
                                isAbstentionCorrect = metrics.isAbstentionCorrect,

                                // Efficiency metrics
                                latencyMs = endTime - startTime,
                                ttftMs = ttftMs,
                                itps = itps,
                                otps = otps,
                                oetMs = oetMs,
                                totalTimeMs = endTime - startTime,

                                // Memory metrics
                                javaHeapKb = memAfter.javaHeap - memBefore.javaHeap,
                                nativeHeapKb = memAfter.nativeHeap - memBefore.nativeHeap,
                                totalPssKb = memAfter.totalPss - memBefore.totalPss,

                                // Device info
                                deviceModel = deviceInfo,
                                androidVersion = androidVersion
=======
                                predictedAllergens = predicted,  // ← MODEL PREDICTION (validated)
                                latencyMs = endTime - startTime,
                                ttftMs = ttftMs,  // ← METRICS
                                itps = itps,
                                otps = otps,
                                oetMs = oetMs,
                                javaHeapKb = memAfter.javaHeap - memBefore.javaHeap,
                                nativeHeapKb = memAfter.nativeHeap - memBefore.nativeHeap,
                                totalPssKb = memAfter.totalPss - memBefore.totalPss,
                                deviceModel = deviceInfo,
                                androidVersion = androidVersion,
                                isCorrect = isCorrect  // ← ACCURACY
>>>>>>> origin/main
                            )
                        }

                        // Save to Firebase (non-blocking)
                        saveToFirebase(result)

                        // Update UI
                        resultsAdapter.addResult(result)
                        predictionProgress.progress = index + 1

                        successCount++
                        Log.i(TAG, "✓ Predicted ${foodItem.name}: ${result.predictedAllergens}")

                    } catch (e: Exception) {
                        failCount++
                        Log.e(TAG, "✗ Failed to predict ${foodItem.name}", e)
                    }

                    // Scroll to show latest result
                    resultsRecyclerView.smoothScrollToPosition(resultsAdapter.itemCount - 1)
                }

                // Hide progress, re-enable controls
                progressCard.visibility = View.GONE
                predictButton.isEnabled = true
                datasetSpinner.isEnabled = true
                loadModelButton.isEnabled = false
                isPredicting = false

                // Show completion notification
                val message = "Set $setNumber: $successCount/${ foodItems.size} successful"
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

    /**
     * Save prediction result to Firebase
     */
    private fun saveToFirebase(result: PredictionResult) {
        firestore.collection("predictions")
            .add(result.toMap())
            .addOnSuccessListener { documentReference ->
                Log.i(TAG, "Firebase: Saved ${result.name} as ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firebase: Failed to save ${result.name}", e)
            }
    }

    /**
     * Show notification to user
     */
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

    /**
     * Capture memory snapshot
     */
    private fun captureMemorySnapshot(): MemorySnapshot {
        return MemorySnapshot(
            javaHeap = MemoryReader.javaHeapKb(),
            nativeHeap = MemoryReader.nativeHeapKb(),
            totalPss = MemoryReader.totalPssKb()
        )
    }

    data class MemorySnapshot(
        val javaHeap: Long,
        val nativeHeap: Long,
        val totalPss: Long
    )

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
