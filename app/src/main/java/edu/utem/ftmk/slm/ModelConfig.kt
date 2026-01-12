package edu.utem.ftmk.slm

/**
 * Configuration for available models
 */
data class ModelConfig(
    val id: String,
    val displayName: String,
    val fileName: String,
    val parameters: String,
    val quantization: String,
    val sizeGB: Double
)

/**
 * All models required for benchmarking
 */
object ModelRegistry {
    
    val MODELS = listOf(
        ModelConfig(
            id = "llama-3.2-1b",
            displayName = "Llama 3.2 1B",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            parameters = "1B",
            quantization = "Q4_K_M",
            sizeGB = 0.8
        ),
        ModelConfig(
            id = "llama-3.2-3b",
            displayName = "Llama 3.2 3B",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            parameters = "3B",
            quantization = "Q4_K_M",
            sizeGB = 2.0
        ),
        ModelConfig(
            id = "qwen2.5-1.5b",
            displayName = "Qwen 2.5 1.5B (Baseline)",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            parameters = "1.5B",
            quantization = "Q4_K_M",
            sizeGB = 1.0
        ),
        ModelConfig(
            id = "qwen2.5-3b",
            displayName = "Qwen 2.5 3B",
            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            parameters = "3B",
            quantization = "Q4_K_M",
            sizeGB = 2.0
        ),
        ModelConfig(
            id = "phi-3-mini",
            displayName = "Phi-3 Mini 4K",
            fileName = "Phi-3-mini-4k-instruct-q4.gguf",
            parameters = "3.8B",
            quantization = "Q4",
            sizeGB = 2.4
        ),
        ModelConfig(
            id = "phi-3.5-mini",
            displayName = "Phi-3.5 Mini",
            fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
            parameters = "3.8B",
            quantization = "Q4_K_M",
            sizeGB = 2.4
        ),
        ModelConfig(
            id = "gemma-2b",
            displayName = "Gemma 2B",
            fileName = "Gemma-2B-instruct-Q4_K_M.gguf",
            parameters = "2B",
            quantization = "Q4_K_M",
            sizeGB = 1.4
        )
    )
    
    /**
     * Get model by ID
     */
    fun getModelById(id: String): ModelConfig? {
        return MODELS.find { it.id == id }
    }
    
    /**
     * Get model by filename
     */
    fun getModelByFilename(filename: String): ModelConfig? {
        return MODELS.find { it.fileName == filename }
    }
    
    /**
     * Get baseline model (Qwen 2.5 1.5B)
     */
    fun getBaselineModel(): ModelConfig {
        return MODELS.find { it.id == "qwen2.5-1.5b" }!!
    }
    
    /**
     * Get display names for spinner
     */
    fun getDisplayNames(): List<String> {
        return MODELS.map { "${it.displayName} (${it.parameters})" }
    }
}
