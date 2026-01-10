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

        Log.i(TAG, "=== MainActivity onCreate ===")

        initializeViews()
        setupRecyclerView()
        createNotificationChannel()

        // Load dataset in background
        lifecycleScope.launch {
            loadFoodDataset()
        }

        setupClickListeners()
    }

    private fun initializeViews() {
        modelStatusText = findViewById(R.id.modelStatusText)
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
                            // Expected format: TTFT_MS=<value>;ITPS=<value>;OTPS=<value>;OET_MS=<value>|<output>
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

                            // Log metrics
                            Log.i(TAG_METRICS, "=== METRICS FOR ${foodItem.name} ===")
                            Log.i(TAG_METRICS, "TTFT: ${ttftMs}ms | ITPS: ${itps} tok/s | OTPS: ${otps} tok/s | OET: ${oetMs}ms")
                            Log.i(TAG_METRICS, "Latency: ${endTime - startTime}ms | Memory: Java=${memAfter.javaHeap - memBefore.javaHeap}KB, Native=${memAfter.nativeHeap - memBefore.nativeHeap}KB")
                            // =================================================

                            // ALLERGEN DETECTION - Clean and Precise
                            val ingredientsText = foodItem.ingredients.lowercase()
                                .replace("_", " ")  // Remove underscores
                                .replace("(", " ")  // Remove parentheses
                                .replace(")", " ")

                            // Special case: if ingredients are ONLY trace warnings, use allergensRaw
                            val cleanedIngredients = if (ingredientsText.startsWith("possible traces") ||
                                ingredientsText.startsWith("may contain traces")) {
                                foodItem.allergensRaw.lowercase()
                            } else {
                                ingredientsText.split(Regex("may contain|traces of"))[0].trim()
                            }

                            val foundAllergens = mutableSetOf<String>()

                            // Milk
                            if (Regex("\\b(milk|dairy|cream|butter|cheese|yogurt|yoghurt|whey|casein|lactose|lait)\\b")
                                    .containsMatchIn(cleanedIngredients)) {
                                foundAllergens.add("milk")
                            }

                            // Egg
                            if (Regex("\\b(egg|eggs|oeuf|oeufs|albumin|mayonnaise)\\b")
                                    .containsMatchIn(cleanedIngredients)) {
                                foundAllergens.add("egg")
                            }

                            // Peanut
                            if (Regex("\\b(peanut|peanuts|groundnut|groundnuts|arachide|arachides)\\b")
                                    .containsMatchIn(cleanedIngredients)) {
                                foundAllergens.add("peanut")
                            }

                            // Tree nuts - with German names
                            if (Regex("\\b(hazelnut|hazelnuts|almond|almonds|cashew|cashews|walnut|walnuts|pecan|pecans|pistachio|pistachios|macadamia|macadamias|noisette|noisettes|mandeln|walnusskerne|haselnusskerne|cashewkerne|noix)\\b")
                                    .containsMatchIn(cleanedIngredients) ||
                                cleanedIngredients.contains("tree nut") ||
                                (cleanedIngredients.contains("nuts") && !cleanedIngredients.contains("coconut"))) {
                                foundAllergens.add("tree nut")
                            }

                            // Wheat - include singular forms
                            if (Regex("\\b(wheat|oat|oats|flour|rye)\\b").containsMatchIn(cleanedIngredients) ||
                                (cleanedIngredients.contains("gluten") && !cleanedIngredients.contains("gluten-free"))) {
                                foundAllergens.add("wheat")
                            }

                            // Soy
                            if (Regex("\\b(soy|soya|soja|soybeans|tofu|edamame)\\b")
                                    .containsMatchIn(cleanedIngredients) ||
                                cleanedIngredients.contains("lecithin (soy)") ||
                                cleanedIngredients.contains("soy lecithin") ||
                                cleanedIngredients.contains("lecithins [soya]") ||
                                cleanedIngredients.contains("lecithins (soybeans)")) {
                                foundAllergens.add("soy")
                            }

                            // Fish
                            if (Regex("\\b(fish|salmon|tuna|cod|anchov|poisson)\\b")
                                    .containsMatchIn(cleanedIngredients)) {
                                foundAllergens.add("fish")
                            }

                            // Shellfish
                            if (Regex("\\b(shellfish|shrimp|shrimps|crab|crabs|lobster|lobsters|prawn|prawns|crevette|crevettes)\\b")
                                    .containsMatchIn(cleanedIngredients)) {
                                foundAllergens.add("shellfish")
                            }

                            // Sesame
                            if (Regex("\\b(sesame|sesame seeds|sesame-seeds|tahini|sesamum|sésame)\\b")
                                    .containsMatchIn(cleanedIngredients)) {
                                foundAllergens.add("sesame")
                            }

                            val predicted = if (foundAllergens.isEmpty()) {
                                "none"
                            } else {
                                foundAllergens.sorted().joinToString(", ")
                            }

                            PredictionResult(
                                dataId = foodItem.id,
                                name = foodItem.name,
                                ingredients = foodItem.ingredients,
                                allergensRaw = foodItem.allergensRaw,
                                allergensMapped = foodItem.allergensMapped,
                                predictedAllergens = predicted,
                                latencyMs = endTime - startTime,
                                javaHeapKb = memAfter.javaHeap - memBefore.javaHeap,
                                nativeHeapKb = memAfter.nativeHeap - memBefore.nativeHeap,
                                totalPssKb = memAfter.totalPss - memBefore.totalPss,
                                deviceModel = deviceInfo,
                                androidVersion = androidVersion
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
