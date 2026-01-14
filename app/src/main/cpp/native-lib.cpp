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
#include <cmath>
#include <android/log.h>

#define TAG "SLM_NATIVE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static bool g_model_loaded = false;
static std::string g_current_model = "";

bool isGemmaModel() {
    return g_current_model.find("Gemma") != std::string::npos ||
           g_current_model.find("gemma") != std::string::npos ||
           g_current_model.find("Vikhr") != std::string::npos;
}

// ===============================================================
// PURE MINIMAL ZERO-SHOT PROMPT
// NO definitions, NO examples, SAME format for all models
// ===============================================================
std::string createAllergenPrompt(const std::string& ingredients) {
    std::stringstream ss;

    if (isGemmaModel()) {
        LOGI("Using Gemma pure zero-shot prompt");

        ss << "<start_of_turn>user\n"
           << "You are a food allergen detector.\n"
           << "\n"
           << "Your task: Analyze the ingredients and detect which allergens are present.\n"
           << "\n"
           << "Allergen categories to check: milk, egg, peanut, tree nut, wheat, soy, fish, shellfish, sesame\n"
           << "\n"
           << "Instructions:\n"
           << "- Only output allergens that are actually present in the ingredients\n"
           << "- Use lowercase letters\n"
           << "- Separate multiple allergens with commas\n"
           << "- If no allergens found, output: none\n"
           << "\n"
           << "Ingredients: " << ingredients << "\n"
           << "Allergens:<end_of_turn>\n"
           << "<start_of_turn>model\n";

        return ss.str();
    } else {
        LOGI("Using ChatML pure zero-shot prompt");

        ss << "<|im_start|>system\n"
           << "You are a food allergen detector.\n"
           << "\n"
           << "Your task: Analyze the ingredients and detect which allergens are present.\n"
           << "\n"
           << "Allergen categories to check: milk, egg, peanut, tree nut, wheat, soy, fish, shellfish, sesame\n"
           << "\n"
           << "Instructions:\n"
           << "- Only output allergens that are actually present in the ingredients\n"
           << "- Use lowercase letters\n"
           << "- Separate multiple allergens with commas\n"
           << "- If no allergens found, output: none\n"
           << "<|im_end|>\n"
           << "<|im_start|>user\n"
           << "Ingredients: " << ingredients << "\n"
           << "Allergens:<|im_end|>\n"
           << "<|im_start|>assistant\n";

        return ss.str();
    }
}

// ===============================================================
// LOAD MODEL
// ===============================================================
extern "C"
JNIEXPORT jboolean JNICALL
Java_edu_utem_ftmk_slm_MainActivity_loadModel(
        JNIEnv* env,
        jobject thiz,
        jobject assetManager,
        jstring modelPath) {

    LOGI("=== Loading Model (Pure Zero-Shot) ===");

    if (g_model_loaded) {
        LOGI("Model already loaded");
        return JNI_TRUE;
    }

    const char* model_path_str = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Model path: %s", model_path_str);

    g_current_model = std::string(model_path_str);

    if (isGemmaModel()) {
        LOGI("✓ Detected: GEMMA model");
    } else {
        LOGI("✓ Detected: Llama/Qwen/Phi model");
    }

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.use_mmap = true;
    model_params.use_mlock = false;

    g_model = llama_load_model_from_file(model_path_str, model_params);
    env->ReleaseStringUTFChars(modelPath, model_path_str);

    if (g_model == nullptr) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 4096;
    ctx_params.n_batch = 1024;
    ctx_params.n_threads = 6;

    g_ctx = llama_new_context_with_model(g_model, ctx_params);

    if (g_ctx == nullptr) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    g_model_loaded = true;
    LOGI("✓ Model loaded with pure zero-shot prompt!");

    return JNI_TRUE;
}

// ===============================================================
// PREDICT ALLERGENS
// ===============================================================
extern "C"
JNIEXPORT jstring JNICALL
Java_edu_utem_ftmk_slm_MainActivity_predictAllergens(
        JNIEnv* env,
        jobject thiz,
        jstring ingredients) {

    auto t_start = std::chrono::high_resolution_clock::now();
    bool first_token_seen = false;

    long ttft_ms = -1;
    long itps = -1;
    long otps = -1;
    long oet_ms = -1;
    int generated_tokens = 0;
    int prompt_tokens = 0;

    if (!g_model_loaded || g_model == nullptr || g_ctx == nullptr) {
        LOGE("Model not loaded!");
        return env->NewStringUTF("ERROR|Model not loaded");
    }

    const char* ingredients_str = env->GetStringUTFChars(ingredients, nullptr);
    LOGI("=== Predicting (Pure Zero-Shot) ===");
    LOGI("Ingredients: %s", ingredients_str);

    std::string prompt = createAllergenPrompt(ingredients_str);
    env->ReleaseStringUTFChars(ingredients, ingredients_str);

    LOGI("Prompt length: %zu chars", prompt.length());

    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    const int n_prompt_tokens = -llama_tokenize(vocab, prompt.c_str(), prompt.length(), nullptr, 0, true, false);
    std::vector<llama_token> tokens(n_prompt_tokens);

    int n_tokens = llama_tokenize(vocab, prompt.c_str(), prompt.length(), tokens.data(), tokens.size(), true, false);

    if (n_tokens < 0) {
        LOGE("Tokenization failed");
        return env->NewStringUTF("ERROR|Tokenization failed");
    }

    LOGI("Tokenized: %d tokens", n_tokens);
    prompt_tokens = n_tokens;

    int max_ctx = llama_n_ctx(g_ctx);
    if (n_tokens >= max_ctx - 100) {
        LOGE("Prompt too long!");
        return env->NewStringUTF("ERROR|Prompt too long");
    }

    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);

    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("Failed to decode");
        return env->NewStringUTF("ERROR|Decoding failed");
    }

    auto t_prefill_end = std::chrono::high_resolution_clock::now();
    long prefill_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
            t_prefill_end - t_start
    ).count();

    if (prefill_ms > 0) {
        itps = (prompt_tokens * 1000L) / prefill_ms;
    }
    LOGI("Prefill: %d tokens in %ld ms", prompt_tokens, prefill_ms);

    std::string result;
    int max_tokens = 40;
    float temperature = 0.0;

    LOGI("Generating...");
    auto n_vocab_size = llama_vocab_n_tokens(vocab);

    for (int i = 0; i < max_tokens; i++) {
        auto * logits = llama_get_logits_ith(g_ctx, -1);

        if (logits == nullptr) {
            LOGE("Failed to get logits");
            break;
        }

        llama_token new_token_id = 0;
        float max_logit = logits[0];

        for (int id = 1; id < n_vocab_size; id++) {
            if (logits[id] > max_logit) {
                max_logit = logits[id];
                new_token_id = id;
            }
        }

        if (llama_vocab_is_eog(vocab, new_token_id)) {
            LOGI("EOS at token %d", i);
            break;
        }

        if (!first_token_seen) {
            auto t_first = std::chrono::high_resolution_clock::now();
            ttft_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                    t_first - t_start
            ).count();
            first_token_seen = true;
            LOGI("TTFT: %ld ms", ttft_ms);
        }

        char buf[256];
        int n_chars = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, false);

        if (n_chars < 0) {
            LOGE("Failed to decode token");
            break;
        }

        std::string token_str(buf, n_chars);
        result += token_str;
        generated_tokens++;

        // Log first 5 tokens
        if (i < 5) {
            LOGI("Token %d: '%s'", i, token_str.c_str());
        }

        // Check for end markers
        if (isGemmaModel()) {
            if (result.find("<end_of_turn>") != std::string::npos) {
                LOGI("Gemma end at token %d", i);
                break;
            }
            if (result.find("<start_of_turn>") != std::string::npos) {
                LOGI("Gemma start marker at token %d", i);
                break;
            }
        } else {
            if (result.find("<|im_end|>") != std::string::npos) {
                LOGI("ChatML end at token %d", i);
                break;
            }
        }

        if (result.find('\n') != std::string::npos) {
            LOGI("Newline at token %d", i);
            break;
        }

        batch = llama_batch_get_one(&new_token_id, 1);

        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("Failed to decode next token");
            break;
        }
    }

    auto t_gen_end = std::chrono::high_resolution_clock::now();
    long gen_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
            t_gen_end - t_start
    ).count();

    oet_ms = gen_ms;

    if (gen_ms > 0 && generated_tokens > 0) {
        otps = (generated_tokens * 1000L) / gen_ms;
    }

    LOGI("Generated %d tokens", generated_tokens);
    LOGI("RAW: '%s'", result.c_str());

    // Clean output
    if (isGemmaModel()) {
        size_t end_pos = result.find("<end_of_turn>");
        if (end_pos != std::string::npos) {
            result = result.substr(0, end_pos);
        }
        end_pos = result.find("<start_of_turn>");
        if (end_pos != std::string::npos) {
            result = result.substr(0, end_pos);
        }
    } else {
        size_t end_pos = result.find("<|im_end|>");
        if (end_pos != std::string::npos) {
            result = result.substr(0, end_pos);
        }
    }

    result.erase(0, result.find_first_not_of(" \n\r\t"));
    result.erase(result.find_last_not_of(" \n\r\t") + 1);

    if (result.empty()) {
        result = "none";
    }

    LOGI("CLEANED: '%s'", result.c_str());

    std::stringstream final_result;
    final_result << "TTFT_MS=" << ttft_ms
                 << ";ITPS=" << itps
                 << ";OTPS=" << otps
                 << ";OET_MS=" << oet_ms
                 << "|" << result;

    return env->NewStringUTF(final_result.str().c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_edu_utem_ftmk_slm_MainActivity_clearContext(
        JNIEnv* env,
        jobject thiz) {
    __android_log_print(ANDROID_LOG_INFO, "SLM_NATIVE", "Context clear requested");
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_edu_utem_ftmk_slm_MainActivity_isModelHealthy(
        JNIEnv* env,
        jobject thiz) {
    if (g_ctx != nullptr && g_model != nullptr) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

// ===============================================================
// UTILITY FUNCTIONS
// ===============================================================
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
    info << "Prompting: Pure Zero-Shot (No Examples)\n";
    info << "Context size: " << llama_n_ctx(g_ctx) << "\n";

    return env->NewStringUTF(info.str().c_str());
}

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
    g_current_model = "";
    LOGI("Model unloaded");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_edu_utem_ftmk_slm_MainActivity_echoFromNative(
        JNIEnv* env,
        jobject thiz,
        jstring input) {

    const char* inputStr = env->GetStringUTFChars(input, nullptr);
    LOGI("echoFromNative() called");
    env->ReleaseStringUTFChars(input, inputStr);

    return env->NewStringUTF("hello from native C++");
}
