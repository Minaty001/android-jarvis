package com.jarvis.camera

import java.util.Locale

enum class LensReaderMode {
    DOCUMENT,
    PRESCRIPTION,
    NUTRITION,
    BARCODE,
    GENERAL
}

object CameraLensReader {

    fun resolveMode(query: String?, modeParam: String?): LensReaderMode {
        val raw = (modeParam ?: "").lowercase(Locale.ROOT).trim()
        if (raw.isNotBlank()) {
            return when {
                raw.contains("prescription") || raw.contains("medicine") || raw.contains("pill") || raw.contains("dawa") -> LensReaderMode.PRESCRIPTION
                raw.contains("document") || raw.contains("page") || raw.contains("paper") || raw.contains("book") -> LensReaderMode.DOCUMENT
                raw.contains("nutrition") || raw.contains("calorie") || raw.contains("food") -> LensReaderMode.NUTRITION
                raw.contains("barcode") || raw.contains("qr") || raw.contains("code") || raw.contains("product") -> LensReaderMode.BARCODE
                else -> LensReaderMode.GENERAL
            }
        }

        val q = (query ?: "").lowercase(Locale.ROOT).trim()
        return when {
            q.contains("prescription") || q.contains("medicine") || q.contains("pill") || q.contains("dawa") -> LensReaderMode.PRESCRIPTION
            q.contains("document") || q.contains("page") || q.contains("paper") || q.contains("book") || q.contains("letter") -> LensReaderMode.DOCUMENT
            q.contains("nutrition") || q.contains("calorie") || q.contains("sugar") || q.contains("fat") || q.contains("protein") -> LensReaderMode.NUTRITION
            q.contains("barcode") || q.contains("qr") || q.contains("upc") || q.contains("product label") -> LensReaderMode.BARCODE
            else -> LensReaderMode.GENERAL
        }
    }

    fun buildSystemPrompt(
        mode: LensReaderMode,
        query: String,
        facing: String,
        sceneSummary: String,
        lighting: String
    ): String {
        return when (mode) {
            LensReaderMode.PRESCRIPTION -> """
                You are JARVIS Medical & Prescription Optical Copilot.
                The user has pointed their device camera ($facing lens) at a medicine bottle, blister pack, or prescription slip.
                
                User Query: "$query"
                Sensor Cues: $sceneSummary
                Lighting: $lighting
                
                Instructions:
                1. Identify the medicine or drug name, active ingredients, dosage strength (e.g. 500mg, 10ml), and administration instructions (e.g. after meals, twice daily).
                2. Highlight any visible expiration dates or critical warnings.
                3. Include a concise medical disclaimer: "Always consult your physician or pharmacist before taking medication."
                4. Keep the entire response under 3 sentences, clear and natural for speech synthesis.
            """.trimIndent()

            LensReaderMode.DOCUMENT -> """
                You are JARVIS Optical Document Reader.
                The user has pointed their device camera ($facing lens) at a physical document, printed page, letter, or book.
                
                User Query: "$query"
                Sensor Cues: $sceneSummary
                Lighting: $lighting
                
                Instructions:
                1. Transcribe the title, heading, and main message of the visible page.
                2. Summarize key names, dates, or required actions.
                3. Keep the response concise (2 to 3 sentences max) tailored for voice speech synthesis.
            """.trimIndent()

            LensReaderMode.NUTRITION -> """
                You are JARVIS Nutrition & Food Lens Assistant.
                The user has pointed their device camera ($facing lens) at a food item or nutrition facts panel.
                
                User Query: "$query"
                Sensor Cues: $sceneSummary
                Lighting: $lighting
                
                Instructions:
                1. Read the nutrition facts: serving size, calories, total carbs, sugar, protein, and sodium.
                2. Mention notable dietary warnings (e.g. contains nuts, gluten, high sugar).
                3. Deliver a concise 2-sentence summary ready for voice read-aloud.
            """.trimIndent()

            LensReaderMode.BARCODE -> """
                You are JARVIS Product & Barcode Reader.
                The user has pointed their device camera ($facing lens) at a product barcode, packaging, or QR label.
                
                User Query: "$query"
                Sensor Cues: $sceneSummary
                Lighting: $lighting
                
                Instructions:
                1. Identify the brand name, product name, and any readable barcode/UPC numbers or SKU codes.
                2. Note any manufacturing or expiry dates visible.
                3. Keep it to 1-2 direct sentences.
            """.trimIndent()

            LensReaderMode.GENERAL -> """
                You are JARVIS Physical World Optical Perception Assistant.
                The user has pointed their device camera ($facing lens) at an object, document, or scene.
                
                User Query: "$query"
                Sensor Cues: $sceneSummary
                Lighting: $lighting
                
                Instructions:
                - Directly identify the focal object or answer the user's question.
                - If reading text (signs, book, label, screen), clearly transcribe and summarize the key information.
                - Keep the response concise, clear, and direct (2 to 3 sentences max) tailored for voice speech synthesis.
                - Do not mention technical camera parameters unless poor lighting affects visibility.
            """.trimIndent()
        }
    }

    fun analyzeOffline(mode: LensReaderMode, scene: SceneAnalysis): String {
        val detectedItems = if (scene.detections.isNotEmpty()) {
            "Detected: ${scene.detections.joinToString { it.label }}"
        } else {
            "Visual markers: ${scene.primaryCategory.lowercase(Locale.ROOT)}"
        }

        return when (mode) {
            LensReaderMode.PRESCRIPTION ->
                "Prescription reading (offline): Found ${scene.primaryCategory.lowercase(Locale.ROOT)} ($detectedItems) in ${scene.lightingCondition}. For safety, check physical label for dosage or connect online for full OCR. Consult your doctor."
            LensReaderMode.DOCUMENT ->
                "Document reader (offline): Detected printed text scene (${scene.lightingCondition}). $detectedItems."
            LensReaderMode.NUTRITION ->
                "Nutrition reader (offline): Detected food or packaging (${scene.lightingCondition}). $detectedItems."
            LensReaderMode.BARCODE ->
                "Barcode scanner (offline): Detected packaging in view (${scene.lightingCondition}). $detectedItems."
            LensReaderMode.GENERAL ->
                "I see ${scene.primaryCategory.lowercase(Locale.ROOT)} in front of the camera (${scene.lightingCondition}). $detectedItems."
        }
    }
}
