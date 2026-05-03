package com.example.fuelify.home

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.utils.UserPreferences
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ProductScanActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var cameraExecutor: ExecutorService
    private var isProcessing  = false
    private var flashEnabled  = false
    private var camera: Camera? = null
    private var userId = -1

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_product)

        userId = UserPreferences.getUserId(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<ImageButton>(R.id.btnScanBack).setOnClickListener { finish() }

        findViewById<LinearLayout>(R.id.btnToggleFlash).setOnClickListener {
            flashEnabled = !flashEnabled
            camera?.cameraControl?.enableTorch(flashEnabled)
            findViewById<TextView>(R.id.tvFlashIcon).text = if (flashEnabled) "💡" else "⚡"
        }

        if (hasCameraPermission()) startCamera()
        else requestCameraPermission()
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required to scan", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startCamera() {
        val previewView = findViewById<PreviewView>(R.id.cameraPreview)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                        if (!isProcessing) {
                            isProcessing = true
                            runOnUiThread {
                                val tv = findViewById<TextView>(R.id.tvScanStatus)
                                tv.visibility = View.VISIBLE
                                tv.text = "🔍  Scanning..."
                            }
                            lookupProduct(barcode)
                        }
                    })
                }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ── Barcode Analyzer ──────────────────────────────────────────────────────

    private inner class BarcodeAnalyzer(
        private val onBarcodeDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        private val scanner = BarcodeScanning.getClient()

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    barcodes.firstOrNull { it.valueType == Barcode.TYPE_PRODUCT || it.rawValue != null }
                        ?.rawValue?.let { onBarcodeDetected(it) }
                }
                .addOnCompleteListener { imageProxy.close() }
        }
    }

    // ── Product Lookup ────────────────────────────────────────────────────────

    private fun lookupProduct(barcode: String) {
        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    val url = "https://world.openfoodfacts.org/api/v0/product/$barcode.json"
                    URL(url).readText()
                }

                val root    = JSONObject(json)
                val status  = root.optInt("status", 0)

                if (status == 0) {
                    // Product not found
                    isProcessing = false
                    runOnUiThread {
                        findViewById<TextView>(R.id.tvScanStatus).text = "❌  Product not found, try again"
                        scope.launch {
                            delay(2000)
                            findViewById<TextView>(R.id.tvScanStatus).visibility = View.GONE
                        }
                    }
                    return@launch
                }

                val product = root.getJSONObject("product")
                showResultSheet(barcode, product)

            } catch (e: Exception) {
                isProcessing = false
                runOnUiThread {
                    Toast.makeText(this@ProductScanActivity,
                        "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                    findViewById<TextView>(R.id.tvScanStatus).visibility = View.GONE
                }
            }
        }
    }

    // ── Parse & Show Result ───────────────────────────────────────────────────

    private fun showResultSheet(barcode: String, product: JSONObject) {
        // Extract fields
        val name     = product.optString("product_name", "").ifEmpty {
            product.optString("product_name_en", "Unknown Product") }
        val brand    = product.optString("brands", "")
        val nutriments = product.optJSONObject("nutriments")

        // Per 100g values
        val calories = nutriments?.optDouble("energy-kcal_100g", 0.0) ?: 0.0
        val protein  = nutriments?.optDouble("proteins_100g", 0.0) ?: 0.0
        val carbs    = nutriments?.optDouble("carbohydrates_100g", 0.0) ?: 0.0
        val fat      = nutriments?.optDouble("fat_100g", 0.0) ?: 0.0

        // Nutri-score
        val nutriScore = product.optString("nutriscore_grade", "")
            .uppercase()
            .trim()
            .let { if (it.isEmpty() || it.length > 1) "?" else it }

        // Allergens
        val allergensRaw = product.optString("allergens_tags", "")
        val allergens = if (allergensRaw.isBlank()) emptyList() else
            allergensRaw.split(",")
                .map { it.trim()
                    .removePrefix("en:")
                    .removePrefix("fr:")
                    .replace("-", " ")
                    .replaceFirstChar { c -> c.uppercase() }
                }
                .filter { it.isNotEmpty() && it.length > 1 }

        // Ingredients text
        val ingredients = product.optString("ingredients_text_en", "")
            .ifEmpty { product.optString("ingredients_text", "No ingredients listed") }

        // Check suitability against user allergies
        val userAllergiesRaw = getUserAllergies()
        val hasAllergyConflict = userAllergiesRaw.any { userAllergen ->
            allergens.any { productAllergen ->
                productAllergen.contains(userAllergen, ignoreCase = true) ||
                userAllergen.contains(productAllergen, ignoreCase = true)
            }
        }

        // Check if suitable for user's goal based on nutri-score
        val goal = getUserGoal()
        val suitableForGoal = when {
            hasAllergyConflict -> false
            goal.contains("lose", true) || goal.contains("weight", true) ->
                calories < 300 && (nutriScore in listOf("A", "B", "C") || nutriScore == "?")
            goal.contains("muscle", true) || goal.contains("gain", true) || goal.contains("build", true) ->
                protein > 5 || calories > 100
            goal.contains("maintain", true) ->
                nutriScore !in listOf("E") // only block E-score products
            else -> true // unknown goal = show as suitable
        }

        val goalLabel = goal.replaceFirstChar { it.uppercase() }.ifEmpty { "your" }
        val noData = calories == 0.0 && protein == 0.0 && carbs == 0.0
        val suitabilityText = when {
            hasAllergyConflict -> "Contains allergens from your profile!"
            noData             -> "No nutrition data available for this product"
            suitableForGoal    -> "Suitable for your $goalLabel goal"
            else               -> "Not ideal for your $goalLabel goal"
        }

        runOnUiThread {
            showBottomSheet(
                name, brand, nutriScore, calories, protein, carbs, fat,
                allergens, ingredients, suitabilityText, hasAllergyConflict,
                suitableForGoal, barcode
            )
        }
    }

    private fun showBottomSheet(
        name: String, brand: String, nutriScore: String,
        calories: Double, protein: Double, carbs: Double, fat: Double,
        allergens: List<String>, ingredients: String,
        suitabilityText: String, hasConflict: Boolean, suitable: Boolean,
        barcode: String
    ) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.bottom_sheet_scan_result)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            attributes?.windowAnimations = android.R.style.Animation_InputMethod
        }

        // Product name + brand
        dialog.findViewById<TextView>(R.id.tvProductName).text = name
        dialog.findViewById<TextView>(R.id.tvProductBrand).text = brand

        // Nutri-score badge
        val tvNutri = dialog.findViewById<TextView>(R.id.tvNutriScore)
        val layoutNutri = dialog.findViewById<LinearLayout>(R.id.layoutNutriScore)
        tvNutri.text = nutriScore
        val nutriBg = when (nutriScore) {
            "A" -> 0xFF22C55E.toInt()
            "B" -> 0xFF86EFAC.toInt()
            "C" -> 0xFFFACC15.toInt()
            "D" -> 0xFFF97316.toInt()
            "E" -> 0xFFEF4444.toInt()
            else -> 0xFF9CA3AF.toInt()
        }
        layoutNutri.setBackgroundColor(nutriBg)

        // Macros per 100g
        dialog.findViewById<TextView>(R.id.tvScanCalories).text = "${calories.toInt()}"
        dialog.findViewById<TextView>(R.id.tvScanProtein).text  = "${String.format("%.1f", protein)}g"
        dialog.findViewById<TextView>(R.id.tvScanCarbs).text    = "${String.format("%.1f", carbs)}g"
        dialog.findViewById<TextView>(R.id.tvScanFat).text      = "${String.format("%.1f", fat)}g"

        // Suitability
        val suitLayout = dialog.findViewById<LinearLayout>(R.id.layoutSuitability)
        val suitText   = dialog.findViewById<TextView>(R.id.tvSuitabilityText)
        val suitIcon   = dialog.findViewById<TextView>(R.id.tvSuitabilityIcon)
        suitText.text = suitabilityText
        when {
            hasConflict -> {
                suitLayout.setBackgroundColor(0xFFFEE2E2.toInt())
                suitText.setTextColor(0xFFDC2626.toInt())
                suitIcon.text = "⚠️"
            }
            suitable -> {
                suitLayout.setBackgroundColor(0xFFDCFCE7.toInt())
                suitText.setTextColor(0xFF166534.toInt())
                suitIcon.text = "✓"
            }
            else -> {
                suitLayout.setBackgroundColor(0xFFFFF7ED.toInt())
                suitText.setTextColor(0xFF9A3412.toInt())
                suitIcon.text = "✗"
            }
        }

        // Allergens
        if (allergens.isNotEmpty()) {
            dialog.findViewById<LinearLayout>(R.id.layoutAllergens).visibility = View.VISIBLE
            dialog.findViewById<TextView>(R.id.tvAllergens).text = allergens.joinToString(" · ")
        }

        // Ingredients
        dialog.findViewById<TextView>(R.id.tvIngredients).text =
            ingredients.take(300).let { if (ingredients.length > 300) "$it..." else it }

        // Add to Grocery List
        dialog.findViewById<LinearLayout>(R.id.btnAddToGrocery).setOnClickListener {
            addToGroceryList(name, barcode)
            dialog.dismiss()
        }

        // Add to Pantry — save macros for use in pantry dialog
        lastCalories   = calories
        lastProtein    = protein
        lastCarbs      = carbs
        lastFat        = fat
        lastNutriScore = nutriScore
        lastBarcode    = barcode
        dialog.findViewById<LinearLayout>(R.id.btnAddToPantry).setOnClickListener {
            dialog.dismiss()
            showAddToPantryDialog(name)
        }

        // Scan Again
        dialog.findViewById<LinearLayout>(R.id.btnScanAgain).setOnClickListener {
            dialog.dismiss()
            isProcessing = false
            findViewById<TextView>(R.id.tvScanStatus).visibility = View.GONE
        }

        dialog.setOnDismissListener {
            // Only reset if user dismissed without tapping scan again
        }

        dialog.show()
    }

    // ── Grocery List ──────────────────────────────────────────────────────────

    private fun addToGroceryList(productName: String, barcode: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val url = java.net.URL("http://192.168.1.3:8080/api/users/$userId/grocery")
                    val body = """{"itemName":"$productName","category":"Scanned","quantity":1.0,"unit":"piece","price":0.0,"ingredientId":null,"isRecommended":false}"""
                    with(url.openConnection() as java.net.HttpURLConnection) {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        outputStream.write(body.toByteArray())
                        responseCode // trigger request
                    }
                }
                Toast.makeText(this@ProductScanActivity,
                    "✓ $productName added to grocery list!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ProductScanActivity,
                    "Could not add to list", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Pantry ────────────────────────────────────────────────────────────────

    // Store last scanned product macros for pantry use
    private var lastCalories = 0.0
    private var lastProtein  = 0.0
    private var lastCarbs    = 0.0
    private var lastFat      = 0.0
    private var lastNutriScore = "?"
    private var lastBarcode  = ""

    private fun showAddToPantryDialog(productName: String) {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.bottom_sheet_add_to_pantry)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            attributes?.windowAnimations = android.R.style.Animation_InputMethod
        }

        dialog.findViewById<TextView>(R.id.tvPantryDialogName).text = productName

        var qty = 1.0
        var selectedDays = 7
        val tvQty  = dialog.findViewById<TextView>(R.id.tvQtyValue)
        val etDays = dialog.findViewById<android.widget.EditText>(R.id.etPantryDays)

        dialog.findViewById<TextView>(R.id.btnQtyMinus).setOnClickListener {
            if (qty > 1) { qty -= 1; tvQty.text = qty.toInt().toString() }
        }
        dialog.findViewById<TextView>(R.id.btnQtyPlus).setOnClickListener {
            qty += 1; tvQty.text = qty.toInt().toString()
        }

        fun selectChip(days: Int) {
            selectedDays = days
            etDays.setText("")
            listOf(
                dialog.findViewById<TextView>(R.id.chip3)  to 3,
                dialog.findViewById<TextView>(R.id.chip7)  to 7,
                dialog.findViewById<TextView>(R.id.chip14) to 14,
                dialog.findViewById<TextView>(R.id.chip30) to 30
            ).forEach { (chip, chipDays) ->
                if (chipDays == days) {
                    chip.setBackgroundResource(R.drawable.light_orange_rectangle)
                    chip.setTextColor(0xFF4A6200.toInt())
                } else {
                    chip.setBackgroundResource(R.drawable.bg_recommended_card)
                    chip.setTextColor(0xFF374151.toInt())
                }
            }
        }
        selectChip(7)

        dialog.findViewById<TextView>(R.id.chip3).setOnClickListener  { selectChip(3)  }
        dialog.findViewById<TextView>(R.id.chip7).setOnClickListener  { selectChip(7)  }
        dialog.findViewById<TextView>(R.id.chip14).setOnClickListener { selectChip(14) }
        dialog.findViewById<TextView>(R.id.chip30).setOnClickListener { selectChip(30) }

        dialog.findViewById<LinearLayout>(R.id.btnConfirmAddPantry).setOnClickListener {
            val customDays = etDays.text.toString().toIntOrNull()
            val finalDays  = customDays ?: selectedDays
            val expiry = java.time.LocalDate.now().plusDays(finalDays.toLong())
                .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            dialog.dismiss()
            addToScannedPantry(productName, qty, expiry)
        }

        dialog.show()
    }

    private fun addToScannedPantry(productName: String, quantity: Double, expiryDate: String) {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.addScannedPantryItem(
                        userId,
                        com.example.fuelify.data.api.models.AddScannedPantryRequest(
                            productName = productName,
                            barcode     = lastBarcode,
                            quantity    = quantity,
                            unit        = "g",
                            expiryDate  = expiryDate,
                            calories    = lastCalories,
                            protein     = lastProtein,
                            carbs       = lastCarbs,
                            fat         = lastFat,
                            nutriScore  = lastNutriScore
                        )
                    )
                }
                if (response.isSuccessful) {
                    Toast.makeText(this@ProductScanActivity,
                        "✓ $productName added to pantry!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ProductScanActivity,
                        "Failed to add to pantry", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProductScanActivity,
                    "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── User Profile Helpers ──────────────────────────────────────────────────

    private fun getUserAllergies(): List<String> {
        return try {
            val prefs = getSharedPreferences("fuelify_prefs", MODE_PRIVATE)
            val raw = prefs.getString("user_allergies", "[]") ?: "[]"
            org.json.JSONArray(raw).let { arr ->
                (0 until arr.length()).map { arr.getString(it).lowercase() }
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun getUserGoal(): String {
        return try {
            val prefs = getSharedPreferences("fuelify_prefs", MODE_PRIVATE)
            prefs.getString("user_goal", "maintain") ?: "maintain"
        } catch (e: Exception) { "maintain" }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        cameraExecutor.shutdown()
    }
}
