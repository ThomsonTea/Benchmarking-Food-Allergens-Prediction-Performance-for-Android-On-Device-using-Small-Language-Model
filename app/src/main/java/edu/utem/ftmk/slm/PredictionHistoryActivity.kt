package edu.utem.ftmk.slm

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Activity to display history of all past predictions
 * Shows searchable, filterable list of predictions from Firebase
 * FIXED: Now uses timestamp instead of latencyMs for sorting newest/oldest
 */
class PredictionHistoryActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PRED_HISTORY"
    }

    private lateinit var firestore: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PredictionHistoryAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var searchView: SearchView
    private lateinit var modelFilterSpinner: Spinner
    private lateinit var sortSpinner: Spinner
    private lateinit var statsCard: View
    private lateinit var totalCountText: TextView
    private lateinit var avgF1Text: TextView
    private lateinit var avgPrecisionText: TextView
    private lateinit var avgRecallText: TextView

    private val allPredictions = mutableListOf<PredictionResult>()
    private var filteredPredictions = mutableListOf<PredictionResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prediction_history)

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance()

        // Initialize views
        initializeViews()
        setupRecyclerView()
        setupFilters()

        // Load predictions
        loadPredictions()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.historyRecyclerView)
        progressBar = findViewById(R.id.historyProgressBar)
        emptyText = findViewById(R.id.emptyText)
        searchView = findViewById(R.id.searchView)
        modelFilterSpinner = findViewById(R.id.modelFilterSpinner)
        sortSpinner = findViewById(R.id.sortSpinner)
        statsCard = findViewById(R.id.statsCard)
        totalCountText = findViewById(R.id.totalCountText)
        avgF1Text = findViewById(R.id.avgF1Text)
        avgPrecisionText = findViewById(R.id.avgPrecisionText)
        avgRecallText = findViewById(R.id.avgRecallText)

        // Back button
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // Refresh button
        findViewById<ImageButton>(R.id.refreshButton).setOnClickListener {
            loadPredictions()
        }
    }

    private fun setupRecyclerView() {
        adapter = PredictionHistoryAdapter(filteredPredictions)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupFilters() {
        // Search functionality
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterPredictions()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterPredictions()
                return true
            }
        })

        // Model filter will be populated after loading predictions
        modelFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterPredictions()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Sort options
        val sortOptions = arrayOf(
            "Newest First",
            "Oldest First",
            "Highest F1",
            "Lowest F1",
            "Product Name A-Z",
            "Product Name Z-A"
        )
        val sortAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions)
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sortSpinner.adapter = sortAdapter
        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterPredictions()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadPredictions() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                emptyText.visibility = View.GONE
                statsCard.visibility = View.GONE

                Log.i(TAG, "Loading predictions from Firebase...")

                val predictions = withContext(Dispatchers.IO) {
                    val snapshot = firestore.collection("predictions")
                        .orderBy("timestamp", Query.Direction.DESCENDING)  // ✅ FIXED: Use timestamp instead of latencyMs
                        .get()
                        .await()

                    val results = mutableListOf<PredictionResult>()
                    for (doc in snapshot.documents) {
                        try {
                            val result = PredictionResult(
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
                                androidVersion = doc.getString("androidVersion") ?: "",

                                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()  // ✅ Load timestamp
                            )
                            results.add(result)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing document: ${e.message}")
                        }
                    }
                    results
                }

                predictions.forEach { prediction ->
                    Log.d("FIREBASE_LOAD", """
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        Loaded: ${prediction.name}
        Model: ${prediction.modelName}
        Ground Truth: ${prediction.allergensMapped}
        Predicted: ${prediction.predictedAllergens}
        ───────────────────────────────────────────
        F1 Score: ${prediction.f1Score}
        Precision: ${prediction.precision}
        Recall: ${prediction.recall}
        Accuracy: ${prediction.accuracy}
        ───────────────────────────────────────────
        TP=${prediction.truePositives} FP=${prediction.falsePositives}
        FN=${prediction.falseNegatives} TN=${prediction.trueNegatives}
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    """.trimIndent())
                }

                allPredictions.clear()
                allPredictions.addAll(predictions)

                Log.i(TAG, "✓ Loaded ${allPredictions.size} predictions")

                // Setup model filter options
                setupModelFilter()

                // Initial filter
                filterPredictions()

                // Calculate and display stats
                updateStats()

                progressBar.visibility = View.GONE

                if (allPredictions.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    statsCard.visibility = View.GONE
                } else {
                    emptyText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    statsCard.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading predictions", e)
                progressBar.visibility = View.GONE
                emptyText.text = "Error loading predictions: ${e.message}"
                emptyText.visibility = View.VISIBLE
                Toast.makeText(this@PredictionHistoryActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupModelFilter() {
        val models = allPredictions.map { it.modelName }.distinct().sorted().toMutableList()
        models.add(0, "All Models")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, models)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelFilterSpinner.adapter = adapter
    }

    private fun filterPredictions() {
        val searchQuery = searchView.query.toString().lowercase()
        val selectedModel = modelFilterSpinner.selectedItem?.toString() ?: "All Models"
        val sortOption = sortSpinner.selectedItemPosition

        // Filter by model and search query
        filteredPredictions = allPredictions.filter { prediction ->
            val matchesModel = selectedModel == "All Models" || prediction.modelName == selectedModel
            val matchesSearch = searchQuery.isEmpty() ||
                    prediction.name.lowercase().contains(searchQuery) ||
                    prediction.predictedAllergens.lowercase().contains(searchQuery) ||
                    prediction.allergensMapped.lowercase().contains(searchQuery)
            matchesModel && matchesSearch
        }.toMutableList()

        // ✅ FIXED: Sort using timestamp for newest/oldest
        when (sortOption) {
            0 -> filteredPredictions.sortByDescending { it.timestamp } // Newest First (highest timestamp = most recent)
            1 -> filteredPredictions.sortBy { it.timestamp } // Oldest First (lowest timestamp = oldest)
            2 -> filteredPredictions.sortByDescending { it.f1Score } // Highest F1
            3 -> filteredPredictions.sortBy { it.f1Score } // Lowest F1
            4 -> filteredPredictions.sortBy { it.name } // A-Z
            5 -> filteredPredictions.sortByDescending { it.name } // Z-A
        }

        // Update adapter
        adapter.updateData(filteredPredictions)

        // Update stats for filtered data
        updateStats()
    }

    private fun updateStats() {
        if (filteredPredictions.isEmpty()) {
            totalCountText.text = "0 predictions"
            avgF1Text.text = "F1: --"
            avgPrecisionText.text = "Precision: --"
            avgRecallText.text = "Recall: --"
            return
        }

        val avgF1 = filteredPredictions.map { it.f1Score }.average()
        val avgPrecision = filteredPredictions.map { it.precision }.average()
        val avgRecall = filteredPredictions.map { it.recall }.average()

        totalCountText.text = "${filteredPredictions.size} predictions"
        avgF1Text.text = "F1: ${String.format("%.3f", avgF1)}"
        avgPrecisionText.text = "Precision: ${String.format("%.3f", avgPrecision)}"
        avgRecallText.text = "Recall: ${String.format("%.3f", avgRecall)}"
    }
}
