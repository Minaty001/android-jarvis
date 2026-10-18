# Hands-Free Full Control (Phase 5) Implementation Plan: Screen OCR & Multimodal Vision

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Phase 5 Hands-Free Screen OCR & Multimodal Vision features (offline structured data extraction, instant screen translation, camera lens reader for prescriptions/documents/nutrition/barcodes, and conversational visual Q&A) optimized for Samsung Galaxy A51 (Exynos 9611) and LG K42 (MediaTek Helio P22, 3GB RAM, Android 10 API 29).

**Architecture:** A resilient dual-tier vision pipeline combining zero-latency on-device static regex heuristics and UI accessibility node analysis (optimized for low-RAM Helio P22 / Exynos 9611) with fallback to multimodal cloud vision LLMs when online. Strict bitmap lifecycle management (downsampling to 1024px and recycling) prevents OOM on 3GB devices, while Android 10 (API 29) fallback guarantees flawless execution even without hardware screenshot APIs.

**Tech Stack:** Kotlin 1.9, Android Accessibility APIs, OkHttp3 (Google Translate API), CameraPerceptionEngine, TFLite Vision Detector, JUnit 4, Coroutines.

**Spec:** [hands-free-full-control-plan.md](file:///home/shanu/.gemini/antigravity-cli/brain/75db0be4-9494-4f7b-b933-ebb93f65ea26/hands-free-full-control-plan.md)

---

## Global Constraints

- **Platform Floor:** Android 10 (API 29) backward compatibility required for LG K42; when `takeScreenshot()` is unavailable, fallback smoothly to UI accessibility tree extraction (`dumpInteractiveTree()`) without throwing unsupported API exceptions.
- **RAM & GC Constraints (LG K42 - 3GB):** Bitmap downsampling strictly capped at `MAX_DIMENSION = 1024`; all intermediate bitmaps recycled in `finally` blocks; translation text payload capped at 800 characters to prevent GC spikes.
- **CPU Constraints (Samsung A51 Exynos 9611 & LG K42 Helio P22):** All extraction and Q&A regex patterns pre-compiled as static constants; linear regular expressions without nested quantifiers to eliminate catastrophic backtracking.
- **NotificationStore Contract:** `NotificationStore.MAX_CAPACITY` must strictly remain `30`.
- **JDK Target:** JDK 17 (`JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11`).

---

## Review Focus

1. **API 29 LG K42 Missing Screenshot API:** `takeScreenshotAsync()` is API 30+; `ScreenVisionManager` and `ScreenVisionTool` must handle null/missing bitmap without crashing and operate entirely on `uiTextTree`.
2. **Huge Screen UI Trees Overwhelming Memory or Translation:** Heavy scroll views or webviews with 100+ nodes could exhaust translation payloads or heap memory; must cap extracted lines at top 10 relevant content items (800 chars).
3. **Regex Catastrophic Backtracking on Malformed Text:** OCR and data extraction regexes must be linear and avoid nested quantifiers (e.g. `(a+)+`) so MediaTek Helio P22 cores never hang.
4. **Prescription/Medicine Label Ambiguity Without Cloud LLM:** When offline, `CameraLensReader` must provide clear disclaimer and detected edge items without hallucinating medicine dosages.
5. **Payment Status False Positives:** A screen mentioning "Payment failed - try again with UPI" must not be classified as `SUCCESS` just because the word "Payment" is present. `VisualQnaEngine` must prioritize failure markers over general payment keywords.

---

## File Structure

- **`app/src/main/java/com/jarvis/vision/ScreenOcrParser.kt`**: [NEW] High-performance offline extractor for receipt totals, prices, order IDs, verification codes (OTPs), tracking numbers, phone numbers, and emails using static pre-compiled regexes.
- **`app/src/main/java/com/jarvis/vision/VisualQnaEngine.kt`**: [NEW] On-device visual status analyzer for payment verification, security/link safety checks, button locator, and error diagnostics.
- **`app/src/main/java/com/jarvis/camera/CameraLensReader.kt`**: [NEW] Specialized camera lens reader for physical documents, medicine/prescription labels, nutritional facts, and barcodes.
- **`app/src/main/java/com/jarvis/tools/ScreenVisionTool.kt`**: [MODIFIED] Enhanced with structured extraction (`mode="extract"`), screen translation (`mode="translate"`), and conversational Q&A (`mode="payment_check"`, `"link_check"`, `"find_button"`).
- **`app/src/main/java/com/jarvis/tools/CameraVisionTool.kt`**: [MODIFIED] Enhanced with lens reader modes (`mode="document"`, `"prescription"`, `"nutrition"`, `"barcode"`).
- **`app/src/main/java/com/jarvis/ai/IntentModel.kt`**: [MODIFIED] Added static pre-compiled regexes and fast-path resolvers for screen translation, visual Q&A, and lens reader.

---

## Tasks

### Task 1: Offline Screen OCR & Structured Data Extraction

**Files:**
- Create: `app/src/main/java/com/jarvis/vision/ScreenOcrParser.kt`
- Modify: `app/src/main/java/com/jarvis/tools/ScreenVisionTool.kt`
- Test: `app/src/test/java/com/jarvis/tools/ScreenOcrExtractionTest.kt`

**Interfaces:**
- Produces:
  - `ScreenOcrParser.parse(text: String): ExtractedData`
  - `ScreenOcrParser.extractTarget(text: String, target: String): String?`
  - `ScreenOcrParser.ExtractedData(totals, prices, verificationCodes, orderIds, trackingNumbers, phoneNumbers, emails, rawLines)`
- Consumes:
  - `ScreenCaptureResult.uiTextTree`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/jarvis/tools/ScreenOcrExtractionTest.kt` with tests for:
1. Parsing receipt totals and prices (`Total: $49.99`, `Grand Total: ₹1,250.00`, `Rs. 450`).
2. Parsing verification codes / OTPs (4 to 8 digits, e.g. "OTP: 839201").
3. Parsing order IDs and tracking numbers (`#ORD-99214`, `TRACK-88219`, `Order #112-9847192-1827401`).
4. Parsing phone numbers and email addresses.
5. `ScreenVisionTool` execution in `mode = "extract"` using `ScreenOcrParser` offline fallback without LLM.

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.ScreenOcrExtractionTest`
Expected: FAIL with "Unresolved reference: ScreenOcrParser"

- [ ] **Step 3: Implement `ScreenOcrParser` and update `ScreenVisionTool`**

1. Implement `ScreenOcrParser.kt` in package `com.jarvis.vision` with static pre-compiled Regex constants:
   - `PRICE_REGEX = Regex("(?:[\\$₹€£]|Rs\\.?|INR)\\s*\\d+(?:,\\d{3})*(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE)`
   - `TOTAL_REGEX = Regex("(?:total|grand total|amount due|subtotal|balance due|payable)[:\\s]+(?:[\\$₹€£]|Rs\\.?|INR)?\\s*\\d+(?:,\\d{3})*(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE)`
   - `OTP_REGEX = Regex("(?:otp|code|verification code|pin|passcode)[:\\s]*([0-9]{4,8})\\b|\\b([0-9]{4,8})\\s+is your", RegexOption.IGNORE_CASE)`
   - `ORDER_ID_REGEX = Regex("(?:order|order id|order #|tracking|track #|tracking id)[:\\s]*#?([a-z0-9_-]{5,30})", RegexOption.IGNORE_CASE)`
   - `PHONE_REGEX = Regex("(?:\\+?\\d{1,3}[-.\s]?)?\\(?\\d{3}\\)?[-.\s]?\\d{3}[-.\s]?\\d{4}")`
   - `EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")`
2. Update `ScreenVisionTool.kt` to delegate `mode == "extract"` to `ScreenOcrParser.parse(captureResult.uiTextTree)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.ScreenOcrExtractionTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jarvis/vision/ScreenOcrParser.kt app/src/main/java/com/jarvis/tools/ScreenVisionTool.kt app/src/test/java/com/jarvis/tools/ScreenOcrExtractionTest.kt
git commit -m "feat(vision): add offline screen OCR and structured data extraction"
```

---

### Task 2: Instant Hands-Free Screen Translation

**Files:**
- Modify: `app/src/main/java/com/jarvis/tools/ScreenVisionTool.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Test: `app/src/test/java/com/jarvis/tools/ScreenTranslationTest.kt`

**Interfaces:**
- Produces:
  - `ScreenVisionTool.execute(params: Map("mode" to "translate", "target_lang" to "hi"))`
  - Spoken output containing translated content: `"Screen translation in Hindi: '<text>'"`
- Consumes:
  - `TranslatorTool(context).execute(mapOf("text" to screenText, "target_lang" to targetLang))`
  - `ScreenCaptureResult.uiTextTree`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/jarvis/tools/ScreenTranslationTest.kt` with tests for:
1. `ScreenVisionTool` execution with `mode = "translate"` translating extracted screen content into Hindi, English, Spanish.
2. Truncating large screen text tree to top 10 content lines (capped at 800 chars) before calling translator to respect 3GB RAM / network limits.
3. `IntentResolver.resolve("translate screen to hindi")` routing to `AssistantIntent.SCREEN_VISION` with `mode = "translate"` and `target_lang = "hi"`.
4. Multilingual triggers: "translate this page to english", "screen translate karo", "what does this screen say in hindi".

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.ScreenTranslationTest`
Expected: FAIL with assertion error on missing translation mode / intent routing.

- [ ] **Step 3: Implement screen translation in `ScreenVisionTool` and `IntentModel`**

1. In `ScreenVisionTool.kt`:
   - Inject or construct `TranslatorTool` when `context` is provided (or accept translator instance for testing).
   - When `mode == "translate"`: extract top 10 unique non-empty text lines from `uiTextTree` (max 800 chars combined).
   - Translate using `TranslatorTool` into `target_lang` (defaulting to `"en"` or `"hi"`).
   - Return `ToolResult.Success("Screen translation in $langName: \"$translatedText\"")` with data map `["mode": "translate", "target_lang": targetLang, "original": originalText, "translation": translatedText]`.
2. In `IntentModel.kt`:
   - Add static pre-compiled regex:
     `SCREEN_TRANSLATE_REGEX = Regex("^(?:translate\\s+(?:this\\s+)?(?:screen|page)|screen\\s+translate)\\s*(?:to|in|into)?\\s*([a-zA-Z]+)?$", RegexOption.IGNORE_CASE)`
   - Add Hinglish aliases: `"screen translate karo"`, `"is screen ko translate karo"`, `"screen ka anuvad karo"`.
   - In `IntentResolver.resolveSingle`, match translation requests and route to `AssistantIntent.SCREEN_VISION` with `params = mapOf("query" to text, "mode" to "translate", "target_lang" to langCode)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.ScreenTranslationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jarvis/tools/ScreenVisionTool.kt app/src/main/java/com/jarvis/ai/IntentModel.kt app/src/test/java/com/jarvis/tools/ScreenTranslationTest.kt
git commit -m "feat(vision): add instant hands-free screen translation"
```

---

### Task 3: Hands-Free Camera Lens Reader (Documents, Prescriptions, Nutrition, Barcodes)

**Files:**
- Create: `app/src/main/java/com/jarvis/camera/CameraLensReader.kt`
- Modify: `app/src/main/java/com/jarvis/tools/CameraVisionTool.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Test: `app/src/test/java/com/jarvis/tools/CameraLensReaderTest.kt`

**Interfaces:**
- Produces:
  - `CameraLensReader.analyzeOffline(mode: LensReaderMode, scene: VisionAnalysis): String`
  - `CameraLensReader.buildPrompt(mode: LensReaderMode, query: String, scene: VisionAnalysis): String`
  - `enum class LensReaderMode { DOCUMENT, PRESCRIPTION, NUTRITION, BARCODE, GENERAL }`
- Consumes:
  - `CameraCaptureResult`, `TfLiteVisionDetector.VisionAnalysis`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/jarvis/tools/CameraLensReaderTest.kt` with tests for:
1. `CameraLensReader` prompt construction and offline fallback for:
   - `DOCUMENT`: Reading physical pages/documents.
   - `PRESCRIPTION`: Medicine name, dosage, frequency, warnings.
   - `NUTRITION`: Calories, carbs, sugar, protein facts.
   - `BARCODE`: Product identifier, barcode digits, expiry cues.
2. `CameraVisionTool` handling `mode = "prescription"` with Mock LLM and offline fallback.
3. `IntentResolver` routing voice commands:
   - "read this document" -> `CAMERA_VISION` (`mode = "document"`)
   - "read prescription" / "dawa padho" -> `CAMERA_VISION` (`mode = "prescription"`)
   - "read nutrition label" / "check calories" -> `CAMERA_VISION` (`mode = "nutrition"`)
   - "scan barcode" / "scan label" -> `CAMERA_VISION` (`mode = "barcode"`)

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.CameraLensReaderTest`
Expected: FAIL with "Unresolved reference: CameraLensReader"

- [ ] **Step 3: Implement `CameraLensReader` and update `CameraVisionTool` & `IntentModel`**

1. Create `app/src/main/java/com/jarvis/camera/CameraLensReader.kt` with:
   - `enum class LensReaderMode { DOCUMENT, PRESCRIPTION, NUTRITION, BARCODE, GENERAL }`
   - Mode resolution from raw string query / mode parameter.
   - Specialized prompts for each mode ensuring concise voice synthesis answers (<= 3 sentences).
   - Safe offline fallback responses with safety disclaimers for medical prescriptions.
2. Update `CameraVisionTool.kt` to use `CameraLensReader` for mode-specific multimodal prompts and edge fallbacks.
3. In `IntentModel.kt`:
   - Add static pre-compiled regexes:
     - `LENS_PRESCRIPTION_REGEX = Regex("^(?:read|check|scan)\\s+(?:the\\s+)?(?:prescription|medicine\\s+label|medicine|pill\\s+bottle)|^(?:dawa|medicine)\\s+(?:padho|check\\s+karo)$", RegexOption.IGNORE_CASE)`
     - `LENS_DOCUMENT_REGEX = Regex("^(?:read|scan)\\s+(?:this\\s+)?(?:document|page|letter|paper|book)$", RegexOption.IGNORE_CASE)`
     - `LENS_NUTRITION_REGEX = Regex("^(?:read|check|scan)\\s+(?:the\\s+)?(?:nutrition\\s+label|nutritional\\s+facts|calories|nutrition)$", RegexOption.IGNORE_CASE)`
     - `LENS_BARCODE_REGEX = Regex("^(?:scan|read)\\s+(?:the\\s+)?(?:barcode|qr\\s+code|product\\s+label|barcode\\s+label)$", RegexOption.IGNORE_CASE)`
   - Route to `AssistantIntent.CAMERA_VISION` with corresponding `mode` parameter.

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.CameraLensReaderTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jarvis/camera/CameraLensReader.kt app/src/main/java/com/jarvis/tools/CameraVisionTool.kt app/src/main/java/com/jarvis/ai/IntentModel.kt app/src/test/java/com/jarvis/tools/CameraLensReaderTest.kt
git commit -m "feat(camera): add hands-free camera lens reader for prescriptions, documents, and nutrition"
```

---

### Task 4: Conversational Visual Q&A & Status Checks

**Files:**
- Create: `app/src/main/java/com/jarvis/vision/VisualQnaEngine.kt`
- Modify: `app/src/main/java/com/jarvis/tools/ScreenVisionTool.kt`
- Modify: `app/src/main/java/com/jarvis/ai/IntentModel.kt`
- Test: `app/src/test/java/com/jarvis/tools/VisualQnaTest.kt`

**Interfaces:**
- Produces:
  - `VisualQnaEngine.checkPaymentStatus(textLines: List<String>): PaymentStatusResult`
  - `VisualQnaEngine.checkLinkSafety(textLines: List<String>): LinkSafetyResult`
  - `VisualQnaEngine.findButton(target: String, elements: List<ScreenElement>): ScreenElement?`
  - `VisualQnaEngine.diagnoseError(textLines: List<String>): String`
- Consumes:
  - `ScreenCaptureResult.elements`, `ScreenCaptureResult.uiTextTree`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/jarvis/tools/VisualQnaTest.kt` with tests for:
1. `checkPaymentStatus`:
   - Detecting successful payment (`"Payment Successful"`, `"Transaction ID: TXN981240"`, `"Order Placed"`) -> `SUCCESS`.
   - Detecting failed payment (`"Payment Failed"`, `"Transaction Declined"`, `"Insufficient Funds"`) -> `FAILED`.
   - Detecting processing / pending (`"Processing payment"`, `"Please wait"`) -> `PENDING`.
2. `checkLinkSafety`:
   - Detecting unencrypted `http://` or `"Not Secure"` warnings -> `UNSAFE`.
   - Detecting clean `https://` secure domains -> `SECURE`.
3. `findButton`:
   - Finding `"Checkout"`, `"Submit"`, `"Pay Now"`, `"Next"` button among screen elements by text or contentDescription, returning bounds and element index.
4. `ScreenVisionTool` executing Q&A queries offline and via LLM.
5. `IntentResolver` routing voice queries:
   - "did the payment go through" / "payment ho gaya kya" -> `SCREEN_VISION` (`mode = "payment_check"`)
   - "is this link safe" / "check link safety" -> `SCREEN_VISION` (`mode = "link_check"`)
   - "find the checkout button" / "where is the submit button" -> `SCREEN_VISION` (`mode = "find_button"`, `target = "checkout"`)

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.VisualQnaTest`
Expected: FAIL with "Unresolved reference: VisualQnaEngine"

- [ ] **Step 3: Implement `VisualQnaEngine` and integrate with `ScreenVisionTool` & `IntentModel`**

1. Create `app/src/main/java/com/jarvis/vision/VisualQnaEngine.kt`:
   - Implement `checkPaymentStatus` with prioritized failure check first (to prevent false positives on "Payment failed").
   - Implement `checkLinkSafety` analyzing URL protocols and browser warning badges.
   - Implement `findButton` searching `ScreenElement` list for matching text, contentDescription, or viewId with normalized case matching.
   - Implement `diagnoseError` identifying error dialog messages and recommending actions.
2. In `ScreenVisionTool.kt`:
   - Route `mode == "payment_check"`, `"link_check"`, `"find_button"`, and `"error_check"` through `VisualQnaEngine` for instantaneous offline answers.
3. In `IntentModel.kt`:
   - Add static pre-compiled regexes:
     - `PAYMENT_CHECK_REGEX = Regex("^(?:did\\s+(?:the\\s+)?payment\\s+go\\s+through|is\\s+(?:the\\s+)?payment\\s+successful|check\\s+payment\\s+status|payment\\s+(?:ho\\s+gaya|hua\\s+kya))$", RegexOption.IGNORE_CASE)`
     - `LINK_SAFETY_REGEX = Regex("^(?:is\\s+this\\s+link\\s+safe|is\\s+this\\s+page\\s+secure|check\\s+(?:link\\s+safety|if\\s+link\\s+is\\s+safe))$", RegexOption.IGNORE_CASE)`
     - `FIND_BUTTON_REGEX = Regex("^(?:find|where\\s+is)\\s+(?:the\\s+)?(.+?)\\s+button$", RegexOption.IGNORE_CASE)`
   - Route to `AssistantIntent.SCREEN_VISION` with corresponding `mode` and `target`.

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest --tests com.jarvis.tools.VisualQnaTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jarvis/vision/VisualQnaEngine.kt app/src/main/java/com/jarvis/tools/ScreenVisionTool.kt app/src/main/java/com/jarvis/ai/IntentModel.kt app/src/test/java/com/jarvis/tools/VisualQnaTest.kt
git commit -m "feat(vision): add conversational visual Q&A, payment status checks, and button finder"
```

---

### Task 5: End-to-End Regression Verification & Push

**Files:**
- Test: `app/src/test/java/com/jarvis/Phase5RegressionTest.kt`
- Modify: `walkthrough.md`

- [ ] **Step 1: Write Phase 5 Integration & Regression Test**

Create `app/src/test/java/com/jarvis/Phase5RegressionTest.kt` verifying:
1. End-to-end integration across OCR extraction, screen translation, camera lens reader, and visual Q&A.
2. Memory safety: no unhandled exceptions when bitmap is null (API 29 LG K42 mode).
3. Intent resolution accuracy across English and Hindi voice inputs for all Phase 5 capabilities.

- [ ] **Step 2: Run full unit test suite**

Run: `JAVA_HOME=/home/shanu/jdk/jdk-17.0.13+11 ./gradlew testDebugUnitTest`
Expected: 100% PASS across 635+ unit tests with 0 failures and 0 regressions.

- [ ] **Step 3: Update walkthrough artifact**

Update `walkthrough.md` with:
- Phase 5 architecture diagram and feature inventory.
- Samsung Galaxy A51 & LG K42 specific benchmark results.
- Spoken voice commands and sample responses.

- [ ] **Step 4: Commit and push**

```bash
git add app/src/test/java/com/jarvis/Phase5RegressionTest.kt docs/superpowers/plans/2026-10-19-hands-free-phase5-vision-ocr.md
git commit -m "feat(phase5): complete hands-free screen OCR, translation, camera lens reader, and visual Q&A"
git push origin main
```
