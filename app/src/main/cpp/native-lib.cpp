#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <algorithm>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include "llama/llama.h"
#include "llama/ggml.h"
#include <chrono>

#define TAG "SLM_NATIVE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Global model and context
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static bool g_model_loaded = false;

// Helper function to create prompt - IMPROVED VERSION
std::string createAllergenPrompt(const std::string& ingredients) {
    std::stringstream ss;
    ss << "<|im_start|>system\n"
       << "You identify allergens. "
       << "Valid allergens: milk, egg, peanut, tree nut, wheat, soy, fish, shellfish, sesame.\n"
       << "Format: output ONLY allergen names (comma-separated) or 'none'.\n"
       << "Examples:\n"
       << "Input: sugar, water → Output: none\n"
       << "Input: milk, sugar → Output: milk\n"
       << "Input: egg, wheat, milk → Output: egg, wheat, milk\n"
       << "<|im_end|>\n"
       << "<|im_start|>user\n"
       << "Input: " << ingredients << " → Output:<|im_end|>\n"
       << "<|im_start|>assistant\n";

    return ss.str();
}

// Load model
extern "C"
JNIEXPORT jboolean JNICALL
Java_edu_utem_ftmk_slm_MainActivity_loadModel(
        JNIEnv* env,
        jobject thiz,
        jobject assetManager,
        jstring modelPath) {

    LOGI("=== Loading Model ===");

    if (g_model_loaded) {
        LOGI("Model already loaded");
        return JNI_TRUE;
    }

    const char* model_path_str = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Model path: %s", model_path_str);

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    LOGI("Loading model from: %s", model_path_str);
    g_model = llama_load_model_from_file(model_path_str, model_params);

    env->ReleaseStringUTFChars(modelPath, model_path_str);

    if (g_model == nullptr) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_batch = 512;
    ctx_params.n_threads = 4;

    LOGI("Creating context...");
    g_ctx = llama_new_context_with_model(g_model, ctx_params);

    if (g_ctx == nullptr) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    g_model_loaded = true;
    LOGI("Model loaded successfully!");

    return JNI_TRUE;
}

// Perform inference - WITH METRICS TRACKING
extern "C"
JNIEXPORT jstring JNICALL
Java_edu_utem_ftmk_slm_MainActivity_predictAllergens(
        JNIEnv* env,
        jobject thiz,
        jstring ingredients) {

    // ================= METRICS TRACKING VARIABLES =================
    auto t_start = std::chrono::high_resolution_clock::now();
    bool first_token_seen = false;

    long ttft_ms = -1;
    long itps = -1;
    long otps = -1;
    long oet_ms = -1;

    int generated_tokens = 0;
    int prompt_tokens = 0;
    // ==============================================================

    if (!g_model_loaded || g_model == nullptr || g_ctx == nullptr) {
        LOGE("Model not loaded!");
        return env->NewStringUTF("ERROR: Model not loaded");
    }

    const char* ingredients_str = env->GetStringUTFChars(ingredients, nullptr);
    LOGI("=== Predicting allergens for: %s ===", ingredients_str);

    std::string prompt = createAllergenPrompt(ingredients_str);
    env->ReleaseStringUTFChars(ingredients, ingredients_str);

    LOGI("Prompt created, length: %zu", prompt.length());

    // Get vocab from model
    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    // Tokenize using vocab (updated API)
    const int n_prompt_tokens = -llama_tokenize(vocab, prompt.c_str(), prompt.length(), nullptr, 0, true, false);
    std::vector<llama_token> tokens(n_prompt_tokens);

    int n_tokens = llama_tokenize(vocab, prompt.c_str(), prompt.length(), tokens.data(), tokens.size(), true, false);

    if (n_tokens < 0) {
        LOGE("Tokenization failed");
        return env->NewStringUTF("ERROR: Tokenization failed");
    }

    LOGI("Tokenized: %d tokens", n_tokens);

    // ================= TRACK PROMPT TOKENS =================
    prompt_tokens = n_tokens;
    // =======================================================

    // Create batch
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);

    // Decode the prompt (PREFILL)
    LOGI("Decoding input tokens...");
    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("Failed to decode");
        return env->NewStringUTF("ERROR: Decoding failed");
    }

    // ================= TRACK ITPS (Input Tokens Per Second) =================
    auto t_prefill_end = std::chrono::high_resolution_clock::now();
    long prefill_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
            t_prefill_end - t_start
    ).count();

    if (prefill_ms > 0) {
        itps = (prompt_tokens * 1000L) / prefill_ms;
    }
    LOGI("Prefill complete: %d tokens in %ld ms (ITPS: %ld tok/s)",
         prompt_tokens, prefill_ms, itps);
    // ========================================================================

    // Generate tokens with improved sampling
    std::string result;
    int max_tokens = 40;  // Reduced for shorter answers

    LOGI("Generating response...");

    auto n_vocab_size = llama_vocab_n_tokens(vocab);

    for (int i = 0; i < max_tokens; i++) {
        auto * logits = llama_get_logits_ith(g_ctx, -1);

        // Greedy sampling (best token)
        llama_token new_token_id = 0;
        float max_logit = logits[0];
        for (int id = 1; id < n_vocab_size; id++) {
            if (logits[id] > max_logit) {
                max_logit = logits[id];
                new_token_id = id;
            }
        }

        // Check for EOS
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            LOGI("EOS token generated");
            break;
        }

        // ================= TRACK TTFT (Time To First Token) =================
        if (!first_token_seen) {
            auto t_first = std::chrono::high_resolution_clock::now();
            ttft_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                    t_first - t_start
            ).count();
            first_token_seen = true;
            LOGI("TTFT: %ld ms", ttft_ms);
        }
        // ====================================================================

        // Decode token to text
        char buf[256];
        int n_chars = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, false);
        if (n_chars < 0) {
            LOGE("Failed to convert token to text");
            break;
        }

        std::string token_str(buf, n_chars);
        result += token_str;

        // ================= COUNT TOKENS =================
        generated_tokens++;
        // ================================================

        // Check for end marker
        if (result.find("<|im_end|>") != std::string::npos) {
            LOGI("End marker found");
            break;
        }

        // Early stop if we see unwanted patterns
        if (result.find("Analyze") != std::string::npos ||
            result.find("RULES") != std::string::npos) {
            LOGI("Unwanted pattern detected, stopping");
            result = "none";
            break;
        }

        // Prepare next batch with single token
        batch = llama_batch_get_one(&new_token_id, 1);

        // Decode next token
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("Failed to decode next token");
            break;
        }
    }

    // ================= TRACK OTPS & OET =================
    auto t_gen_end = std::chrono::high_resolution_clock::now();
    long gen_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
            t_gen_end - t_start
    ).count();

    oet_ms = gen_ms;

    if (gen_ms > 0 && generated_tokens > 0) {
        otps = (generated_tokens * 1000L) / gen_ms;
    }

    LOGI("Generation complete: %d tokens in %ld ms (OTPS: %ld tok/s)",
         generated_tokens, gen_ms, otps);
    // ====================================================

    // AGGRESSIVE CLEANING

    // Remove end markers
    size_t end_pos = result.find("<|im_end|>");
    if (end_pos != std::string::npos) {
        result = result.substr(0, end_pos);
    }

    // Remove start markers
    size_t start_pos = result.find("<|im_start|>");
    if (start_pos != std::string::npos) {
        result.erase(start_pos, 13);
    }

    // Remove "assistant" tag
    start_pos = result.find("assistant");
    if (start_pos != std::string::npos) {
        result.erase(start_pos, 9);
    }

    // Replace newlines with spaces
    std::replace(result.begin(), result.end(), '\n', ' ');
    std::replace(result.begin(), result.end(), '\r', ' ');
    std::replace(result.begin(), result.end(), '\t', ' ');

    // Remove multiple spaces
    std::string::iterator new_end = std::unique(result.begin(), result.end(),
                                                [](char a, char b) { return a == ' ' && b == ' '; });
    result.erase(new_end, result.end());

    // Trim whitespace
    result.erase(0, result.find_first_not_of(" \n\r\t"));
    result.erase(result.find_last_not_of(" \n\r\t.,;: ") + 1);

    // Check if result contains bad patterns
    if (result.empty() ||
        result.find("Analyze") != std::string::npos ||
        result.find("identify") != std::string::npos ||
        result.find("RULES") != std::string::npos ||
        result.find("list:") != std::string::npos) {
        result = "none";
    }

    // Convert to lowercase
    std::transform(result.begin(), result.end(), result.begin(), ::tolower);

    LOGI("Final response: %s", result.c_str());

    // ================= FORMAT RETURN WITH METRICS =================
    std::stringstream final_result;
    final_result << "TTFT_MS=" << ttft_ms
                 << ";ITPS=" << itps
                 << ";OTPS=" << otps
                 << ";OET_MS=" << oet_ms
                 << "|" << result;

    std::string result_str = final_result.str();
    LOGI("Returning with metrics: %s", result_str.c_str());

    return env->NewStringUTF(result_str.c_str());
    // ==============================================================
}

// Get model info
extern "C"
JNIEXPORT jstring JNICALL
Java_edu_utem_ftmk_slm_MainActivity_getModelInfo(
        JNIEnv* env,
        jobject thiz) {

    if (!g_model_loaded || g_model == nullptr) {
        return env->NewStringUTF("Model not loaded");
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    std::stringstream info;
    info << "Model loaded: Yes\n";
    info << "Context size: " << llama_n_ctx(g_ctx) << "\n";
    info << "Vocab size: " << llama_vocab_n_tokens(vocab) << "\n";

    return env->NewStringUTF(info.str().c_str());
}

// Unload model
extern "C"
JNIEXPORT void JNICALL
Java_edu_utem_ftmk_slm_MainActivity_unloadModel(
        JNIEnv* env,
        jobject thiz) {

    LOGI("Unloading model...");

    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    if (g_model != nullptr) {
        llama_free_model(g_model);
        g_model = nullptr;
    }

    llama_backend_free();

    g_model_loaded = false;
    LOGI("Model unloaded");
}

// Keep Phase 5 test function
extern "C"
JNIEXPORT jstring JNICALL
Java_edu_utem_ftmk_slm_MainActivity_echoFromNative(
        JNIEnv* env,
        jobject thiz,
        jstring input) {

    const char* inputStr = env->GetStringUTFChars(input, nullptr);
    LOGI("echoFromNative() called with: %s", inputStr);
    env->ReleaseStringUTFChars(input, inputStr);

    return env->NewStringUTF("hello from native C++");
}
