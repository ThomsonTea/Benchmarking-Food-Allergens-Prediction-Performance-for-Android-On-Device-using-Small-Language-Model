# Benchmarking-Food-Allergens-Prediction-Performance-for-Android-On-Device-using-Small-Language-Model

# 🍔 Food Allergen Prediction App - User Guide

## 📱 **Android On-Device SLM Benchmarking Application**

Complete guide for testing and evaluating 7 Small Language Models for food allergen detection on Android devices.

---

## 📋 **Table of Contents**

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Installation Setup](#installation-setup)
4. [First-Time Setup](#first-time-setup)
5. [Running Predictions](#running-predictions)
6. [Viewing Results](#viewing-results)
7. [Exporting Data](#exporting-data)
8. [Testing All Models](#testing-all-models)
9. [Troubleshooting](#troubleshooting)
10. [Expected Timeline](#expected-timeline)

---

## 🎯 **Overview**

This application evaluates **7 Small Language Models** for food allergen prediction using on-device inference. It measures:
- **Prediction Quality** (Precision, Recall, F1, EMR, etc.)
- **Safety Metrics** (Hallucination rate, Over-prediction, Abstention)
- **Efficiency Metrics** (Latency, TTFT, ITPS, OTPS, Memory usage)

**Models Tested:**
1. Llama 3.2 1B (Fastest)
2. Llama 3.2 3B
3. Qwen 2.5 1.5B ⭐ (Baseline)
4. Qwen 2.5 3B
5. Phi-3 Mini 3.8B
6. Phi-3.5 Mini 3.8B
7. Gemma 2B

---

## 📦 **Prerequisites**

### **Required Hardware:**
- Android phone (Android 6.0+)
- At least **16GB free storage** (for all 7 models)
- USB cable for file transfer
- Computer with ADB installed

### **Required Software:**
- Android Studio (for building)
- ADB (Android Debug Bridge)
- All 7 model files (`.gguf` format)

### **Model Files:**
Download from provided links:
- Llama-3.2-1B-Instruct-Q4_K_M.gguf (~800MB)
- Llama-3.2-3B-Instruct-Q4_K_M.gguf (~2GB)
- qwen2.5-1.5b-instruct-q4_k_m.gguf (~1GB)
- qwen2.5-3b-instruct-q4_k_m.gguf (~2GB)
- Phi-3-mini-4k-instruct-q4.gguf (~2.4GB)
- Phi-3.5-mini-instruct-Q4_K_M.gguf (~2.4GB)
- Gemma-2B-instruct-Q4_K_M.gguf (~1.4GB)

**Total size:** ~12GB

---

## 🚀 **Installation Setup**

### **Step 1: Build the App**

```bash
# In Android Studio
1. Open project
2. Build → Clean Project
3. Build → Rebuild Project
4. Run → Run 'app'
```

The app will install on your connected phone (~50MB).

---

### **Step 2: Transfer Models to Phone**

**Option A: Using ADB (Recommended)**

```bash
# 1. Enable USB Debugging on phone
Settings → About Phone → Tap "Build Number" 7 times
Settings → Developer Options → Enable "USB Debugging"

# 2. Connect phone via USB

# 3. Create directory on phone
adb shell "mkdir -p /storage/emulated/0/Documents/SLM_Models"

# 4. Transfer models (run from folder containing .gguf files)
adb push "Llama-3.2-1B-Instruct-Q4_K_M.gguf" "/storage/emulated/0/Documents/SLM_Models/"
adb push "Llama-3.2-3B-Instruct-Q4_K_M.gguf" "/storage/emulated/0/Documents/SLM_Models/"
adb push "qwen2.5-1.5b-instruct-q4_k_m.gguf" "/storage/emulated/0/Documents/SLM_Models/"
adb push "qwen2.5-3b-instruct-q4_k_m.gguf" "/storage/emulated/0/Documents/SLM_Models/"
adb push "Phi-3-mini-4k-instruct-q4.gguf" "/storage/emulated/0/Documents/SLM_Models/"
adb push "Phi-3.5-mini-instruct-Q4_K_M.gguf" "/storage/emulated/0/Documents/SLM_Models/"
adb push "Gemma-2B-instruct-Q4_K_M.gguf" "/storage/emulated/0/Documents/SLM_Models/"

# 5. Verify transfer
adb shell "ls -lh /storage/emulated/0/Documents/SLM_Models/"
```

**Transfer time:** 10-45 minutes (depends on USB speed)

**Option B: Manual Transfer**

```
1. Connect phone via USB
2. Select "File Transfer" mode
3. Navigate to Internal Storage/Documents/
4. Create folder: SLM_Models
5. Copy all 7 .gguf files into this folder
```

---

### **Step 3: Grant Permissions**

```
1. Open the app
2. When prompted, grant "Storage Permission"
3. For Android 11+: Settings will open
   → Enable "All files access" or "Manage all files"
4. Press back to return to app
```

---

## 🎬 **First-Time Setup**

### **1. Launch App**

Open the Food Allergen Prediction app on your phone.

**Main Screen Elements:**
- Model Selector (dropdown)
- Load Model button
- Dataset Selector
- Run Predictions button
- Results area
- Dashboard button

---

### **2. Load Your First Model**

```
1. Tap "Model Selector" dropdown
2. Select "Qwen 2.5 1.5B (Baseline)" ⭐
3. Tap "Load Model" button
4. Wait 2-5 seconds
5. Toast message: "✓ Model loaded in XXXXms"
```

**If you see "Model not found":**
- Check models are in `/storage/emulated/0/Documents/SLM_Models/`
- Verify storage permission granted
- Check Logcat for exact path it's searching

---

## 🧪 **Running Predictions**

### **Step 1: Select Dataset**

```
1. Tap "Dataset Selector" dropdown
2. Choose a set (e.g., "Set 1: Items 0-9")
```

**Available datasets:**
- Set 1: Items 0-9 (10 items)
- Set 2: Items 10-19 (10 items)
- Set 3: Items 20-29 (10 items)
- ... etc.

---

### **Step 2: Start Predictions**

```
1. Tap "Run Predictions" button
2. App starts processing items
3. Progress bar shows current item
4. Cards appear as predictions complete
```

**What to expect:**

| Model | Time per Item | 10 Items Total |
|-------|---------------|----------------|
| Llama 1B | ~40s | ~7 min |
| Qwen 1.5B | ~60s | ~10 min |
| Gemma 2B | ~70s | ~12 min |
| Llama 3B | ~150s | ~25 min |
| Qwen 3B | ~150s | ~25 min |
| Phi-3 Mini | ~200s | ~35 min |
| Phi-3.5 Mini | ~200s | ~35 min |

---

### **Step 3: Monitor Progress**

**On Phone Screen:**
```
┌─────────────────────────────────────┐
│ Processing: Nutella                 │
│ 3/10 items                          │
│ [████████░░░░░░░] 60%               │
└─────────────────────────────────────┘
```

**In Android Studio Logcat:**
```
I/SLM_MAIN: Processing 3/10: Nutella
I/SLM_NATIVE: === Predicting allergens ===
I/SLM_NATIVE: TTFT: 54500ms
I/SLM_METRICS: Latency: 65000ms
I/SLM_MAIN: ✓ Predicted: milk, tree nut
I/SLM_MAIN: Firebase: Saved as ABC123XYZ
```

---

### **Step 4: View Results**

As predictions complete, cards appear showing:

```
┌─────────────────────────────────────┐
│ Nutella                             │
│ Ground Truth: milk, tree nut        │
│ Predicted: milk, tree nut           │
│ Latency: 65.0s                      │
│ Precision: 1.00 | Recall: 1.00      │
│ F1: 1.00 | Exact Match: Yes         │
└─────────────────────────────────────┘
```

---

## 📊 **Viewing Results**

### **Individual Results**

Scroll through the RecyclerView to see each food item:

**Each card shows:**
- Food name
- Ingredients
- Ground truth allergens
- Predicted allergens
- Key metrics (Precision, Recall, F1, Latency)
- Match status (✓ or ✗)

---

### **Dashboard View**

```
1. Tap "View Dashboard" button
2. See aggregated metrics for all models tested
```

**Dashboard displays:**

**Per Model Card:**
```
┌─────────────────────────────────────┐
│ Qwen 2.5 1.5B                       │
│ Items: 10                           │
│                                     │
│ Quality Metrics:                    │
│ • Precision: 0.92                   │
│ • Recall: 0.88                      │
│ • F1 (Micro): 0.90                  │
│ • F1 (Macro): 0.89                  │
│ • Exact Match: 80%                  │
│                                     │
│ Safety Metrics:                     │
│ • Hallucination Rate: 5%            │
│ • Over-prediction: 10%              │
│                                     │
│ Efficiency:                         │
│ • Avg Latency: 62.5s                │
│ • TTFT: 54.2s                       │
│ • Memory (PSS): 1.2GB               │
└─────────────────────────────────────┘
```

**Summary Comparison:**
- Best F1 Score
- Fastest Model
- Lowest Hallucination Rate
- Most Efficient

---

## 📁 **Exporting Data**

### **Export to Excel**

```
1. Open Dashboard
2. Tap "Export to Excel" button
3. Toast: "Exporting to Excel..."
4. Wait 5-10 seconds
5. Toast: "✓ Excel exported successfully"
```

**Excel file location:**
```
/storage/emulated/0/Download/allergen_predictions_[timestamp].xlsx
```

**File structure:**
```
📊 allergen_predictions_20260112_143052.xlsx
├─ Sheet: Qwen_2.5_1.5B
│  ├─ Headers: Name, Ingredients, Ground Truth, Predicted, ...
│  └─ 10 rows of data
├─ Sheet: Llama_3.2_1B
│  └─ 10 rows of data
├─ ... (one sheet per model)
└─ Sheet: Summary
   └─ Comparison table of all models
```

**Columns included:**
- Food name, Ingredients
- Ground truth, Predicted allergens
- TP, FP, FN, TN
- Precision, Recall, F1, EMR
- Hallucination, Over-prediction
- Latency, TTFT, ITPS, OTPS
- Memory usage
- Device info

---

### **Access Excel File**

**On Phone:**
```
1. Open "Files" app
2. Navigate to "Downloads"
3. Find: allergen_predictions_[timestamp].xlsx
4. Open with Excel/Sheets
```

**Transfer to Computer:**
```bash
# Using ADB
adb pull /storage/emulated/0/Download/allergen_predictions_[timestamp].xlsx .

# Or via USB File Transfer
Connect phone → Downloads folder → Copy file
```

---

## 🧪 **Testing All Models**

### **Complete Testing Workflow**

For each model, follow this process:

#### **Model 1: Qwen 2.5 1.5B (Baseline)**

```
1. Select "Qwen 2.5 1.5B" from model dropdown
2. Tap "Load Model" (wait 2-3 seconds)
3. Select "Set 1: Items 0-9"
4. Tap "Run Predictions"
5. Wait ~10 minutes
6. Verify 10 cards appear
7. Check Firebase has 10 new documents
```

**Time:** ~12 minutes

---

#### **Model 2: Llama 3.2 1B (Fastest)**

```
1. Select "Llama 3.2 1B" from dropdown
2. Tap "Load Model" (wait 2 seconds)
3. Select "Set 1: Items 0-9" (same dataset!)
4. Tap "Run Predictions"
5. Wait ~7 minutes (faster!)
6. Verify results
```

**Time:** ~9 minutes

---

#### **Model 3-7: Remaining Models**

Repeat for:
- Gemma 2B (~12 min)
- Llama 3.2 3B (~25 min)
- Qwen 2.5 3B (~25 min)
- Phi-3 Mini (~35 min)
- Phi-3.5 Mini (~35 min)

**Important:**
- Use **SAME dataset** (Set 1) for all models
- Ensures fair comparison
- Load model FIRST, then run predictions
- Don't close app during predictions

---

### **Batch Testing Strategy**

**Day 1: Fast Models (2-3 hours)**
```
Morning:
✓ Qwen 2.5 1.5B (10 items, 12 min)
✓ Llama 3.2 1B (10 items, 9 min)
✓ Gemma 2B (10 items, 12 min)

Total: ~35 minutes + buffer = 1 hour
```

**Day 2: Medium Models (1-2 hours)**
```
Morning:
✓ Llama 3.2 3B (10 items, 25 min)
✓ Qwen 2.5 3B (10 items, 25 min)

Total: ~50 minutes + buffer = 1.5 hours
```

**Day 3: Large Models (2 hours)**
```
Morning:
✓ Phi-3 Mini (10 items, 35 min)
✓ Phi-3.5 Mini (10 items, 35 min)

Total: ~70 minutes + buffer = 2 hours
```

**Day 4: Export & Analysis**
```
✓ Open Dashboard
✓ Verify all 7 models have data
✓ Export to Excel
✓ Take screenshots
✓ Prepare report
```

---

## 🎯 **Expected Timeline**

### **Complete Project Timeline**

```
Day 1 (3 hours):
├─ Setup (30 min)
│  ├─ Build app
│  ├─ Transfer models
│  └─ Grant permissions
├─ Test fast models (1 hour)
│  ├─ Qwen 1.5B
│  ├─ Llama 1B
│  └─ Gemma 2B
└─ Verify results (30 min)

Day 2 (2 hours):
├─ Test medium models (1.5 hours)
│  ├─ Llama 3B
│  └─ Qwen 3B
└─ Check Firebase (30 min)

Day 3 (2.5 hours):
├─ Test large models (2 hours)
│  ├─ Phi-3 Mini
│  └─ Phi-3.5 Mini
└─ Verify all data (30 min)

Day 4 (2 hours):
├─ Dashboard review (30 min)
├─ Export Excel (10 min)
├─ Screenshots (20 min)
└─ Report writing (1 hour)

Total: ~9-10 hours
```

---

## 📸 **Taking Screenshots**

For submission, capture these screens:

### **1. Main Screen**
```
✓ Show model selector
✓ Show loaded model
✓ Show dataset selector
✓ Show prediction results cards
```

### **2. Individual Predictions**
```
✓ Show 2-3 prediction cards
✓ Include metrics (Precision, Recall, F1)
✓ Show exact matches
```

### **3. Dashboard**
```
✓ Show multiple model cards
✓ Include aggregate metrics
✓ Show summary comparison
```

### **4. Excel File**
```
✓ Open in Excel/Sheets
✓ Show different tabs (one per model)
✓ Show summary sheet
```

**How to screenshot on Android:**
```
Power button + Volume Down
OR
Swipe down → Screenshot button
```

---

## ⚠️ **Troubleshooting**

### **Problem: Model Not Found**

**Symptoms:**
```
Toast: "Model not found! Place models in: /storage/..."
```

**Solutions:**
```
1. Check Logcat for exact path searched
2. Verify files in: /storage/emulated/0/Documents/SLM_Models/
3. Run: adb shell "ls -lh /storage/emulated/0/Documents/SLM_Models/"
4. Ensure filenames match exactly (case-sensitive!)
5. Re-grant storage permission
```

---

### **Problem: App Crashes During Prediction**

**Symptoms:**
```
App closes suddenly
Logcat: "SIGSEGV" or "crash_dump"
```

**Solutions:**
```
1. Check available RAM (need ~2-3GB free)
2. Close other apps
3. Restart phone
4. Try smaller model first (Llama 1B)
5. Check native-lib.cpp settings:
   - n_ctx should be 2048 or 4096
   - n_threads should be 4-6
```

---

### **Problem: Predictions Very Slow**

**Symptoms:**
```
Each item takes 3-5 minutes (should be 1-2 min for small models)
```

**Solutions:**
```
1. Check phone isn't in power saving mode
2. Ensure phone is charging
3. Close background apps
4. Check which model loaded (3B models are naturally slower)
5. Verify n_ctx isn't too large (check native-lib.cpp)
```

---

### **Problem: Firebase Not Saving**

**Symptoms:**
```
Predictions complete but Firebase empty
Logcat: "Firebase: Failed to save"
```

**Solutions:**
```
1. Check internet connection
2. Verify Firebase configuration in google-services.json
3. Check Firestore rules allow writes
4. Look for authentication errors in Logcat
5. Try manual Firebase write test
```

---

### **Problem: Excel Export Fails**

**Symptoms:**
```
Toast: "Export failed" or "No data to export"
```

**Solutions:**
```
1. Check Firebase has data for models
2. Verify storage permission granted
3. Check available storage space (need ~50MB)
4. Try exporting individual model first
5. Check Logcat for specific error
```

---

### **Problem: Hallucination Rate High**

**Symptoms:**
```
Model predicts allergens not in ingredients
Dashboard shows >20% hallucination rate
```

**Solutions:**
```
1. Check if ingredient validation is enabled in MainActivity.kt
2. Verify prompt in native-lib.cpp is correct
3. Try different model (Qwen 3B more accurate)
4. Check if model file corrupted (re-download)
```

---

## 📊 **Data Verification**

### **Check Firebase Console**

```
1. Open Firebase Console in browser
2. Navigate to Firestore Database
3. Look for "predictions" collection
4. Should have documents grouped by model
```

**Expected structure:**
```
predictions/
├─ ABC123 (Nutella - Qwen 1.5B)
├─ DEF456 (Cookies - Qwen 1.5B)
├─ GHI789 (Nutella - Llama 1B)
...
└─ XYZ999 (Milk - Phi-3.5)

Expected: 7 models × 10 items = 70 documents minimum
```

---

### **Verify Excel Data**

Open Excel file and check:

```
✓ 7 sheets (one per model) + 1 summary
✓ Each sheet has 10 rows (for 10 items)
✓ All columns filled (no empty cells in key metrics)
✓ Summary sheet compares all models
✓ Formulas calculate correctly
```

---

## 🎓 **For Your Report**

### **Include These Elements:**

**1. Device Specification**
```
Example:
- Device: Samsung Galaxy S21
- Processor: Snapdragon 888 (8 cores, 2.84 GHz)
- RAM: 8GB LPDDR5
- Storage: 128GB
- Android Version: 13
- App Version: 1.0.0
```

**2. Screenshots**
```
✓ Main screen with model loaded
✓ Prediction results (2-3 cards)
✓ Dashboard view
✓ Excel file (multiple tabs)
```

**3. Metrics Table**
```
Copy from Excel Summary sheet:

Model       | F1    | Precision | Recall | Latency | Hallucination
------------|-------|-----------|--------|---------|---------------
Qwen 1.5B   | 0.90  | 0.92      | 0.88   | 62s     | 5%
Llama 1B    | 0.75  | 0.78      | 0.72   | 42s     | 12%
...
```

**4. Analysis**
```
- Best accuracy: Qwen 2.5 3B (F1: 0.92)
- Fastest: Llama 1B (42s avg)
- Best balance: Qwen 1.5B (F1: 0.90, 62s)
- Safest: Qwen 3B (3% hallucination)
```

---

## 📝 **Quick Reference**

### **Common Commands**

```bash
# Check models on phone
adb shell "ls -lh /storage/emulated/0/Documents/SLM_Models/"

# Pull Excel file
adb pull /storage/emulated/0/Download/allergen_predictions_*.xlsx .

# Check app logs
adb logcat | grep SLM

# Clear app data (reset)
adb shell pm clear edu.utem.ftmk.slm

# Push new model
adb push "model.gguf" "/storage/emulated/0/Documents/SLM_Models/"
```

---

### **Testing Checklist**

```
Setup:
☐ App installed
☐ All 7 models transferred
☐ Storage permission granted
☐ Firebase accessible

Per Model:
☐ Select model
☐ Load model successfully
☐ Select same dataset (Set 1)
☐ Run 10 predictions
☐ Verify cards appear
☐ Check Firebase saved

After All Models:
☐ Open Dashboard
☐ Verify 7 model cards
☐ Export to Excel
☐ Verify Excel has 8 sheets
☐ Take screenshots
☐ Backup data

Submission:
☐ Screenshots captured
☐ Excel file downloaded
☐ Device specs documented
☐ Report written
☐ Code artifacts ready
```

---

## 🆘 **Need Help?**

### **Common Questions**

**Q: How long does the entire testing take?**
A: 8-10 hours total (can split across multiple days)

**Q: Can I test with fewer items?**
A: Yes, but use minimum 10 items per model for statistical validity

**Q: Do I need to test all 7 models?**
A: Yes, project requirements specify all 7 models

**Q: Can I use different datasets for different models?**
A: No, use SAME dataset for fair comparison

**Q: What if one model crashes?**
A: Try smaller model first, check RAM, restart phone

**Q: Can I run predictions overnight?**
A: Not recommended - phone might lock/sleep

---

## ✅ **Final Checklist**

Before demo day (Jan 12-13, 2026):

```
☐ App builds successfully
☐ All 7 models on phone
☐ Tested all 7 models (10 items each)
☐ Dashboard shows 7 model cards
☐ Excel exported successfully
☐ Screenshots taken
☐ Device specs documented
☐ Report completed
☐ Code ready for submission
☐ Demo rehearsed
```

---

## 🎉 **You're Ready!**

Follow this guide step-by-step and you'll successfully complete the entire project!

**Good luck with your demo!** 🚀

---

**Project:** BITP 3453 Mobile Application Development  
**Semester:** 1 2025/2026  
**Institution:** FTMK, UTeM  

---

*Last updated: January 11, 2026*
