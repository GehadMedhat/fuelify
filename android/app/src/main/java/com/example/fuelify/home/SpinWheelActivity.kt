package com.example.fuelify.home

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.SpinWheelMeal
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*
import kotlin.math.*
import kotlin.random.Random

class SpinWheelActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1
    private var isSpinning = false
    private var currentMeal: SpinWheelMeal? = null
    private lateinit var wheelView: WheelView

    // Wheel segment labels — shown while spinning, meal revealed after
    private val segments = listOf(
        "High\nProtein", "Vegan", "Low\nCarb",
        "Balanced", "Quick\nMeal", "Eco\nPick",
        "Chef's\nChoice", "Surprise!"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spin_wheel)

        userId = UserPreferences.getUserId(this)
        findViewById<ImageButton>(R.id.btnSpinBack).setOnClickListener { finish() }

        // Build wheel
        wheelView = WheelView(this, segments)
        val wheelContainer = findViewById<FrameLayout>(R.id.wheelContainer)
        wheelContainer.addView(wheelView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Spin button
        findViewById<LinearLayout>(R.id.btnSpin).setOnClickListener {
            if (!isSpinning) spinWheel()
        }

        // Result card buttons (hidden until spin completes)
        findViewById<LinearLayout>(R.id.btnOrderCloudKitchen).setOnClickListener {
            currentMeal?.let { openCloudKitchen(it) }
        }
        findViewById<LinearLayout>(R.id.btnOrderTalabat).setOnClickListener {
            currentMeal?.let { openTalabat(it.mealName) }
        }
        findViewById<TextView>(R.id.btnCopyCode).setOnClickListener {
            currentMeal?.let { copyCode(it.discountCode) }
        }
        findViewById<TextView>(R.id.btnSpinAgain).setOnClickListener {
            resetUI()
        }
    }

    // ── Spin logic ────────────────────────────────────────────────────────────

    private fun spinWheel() {
        isSpinning = true
        hideResultCard()
        setSpinButtonEnabled(false)


        // Animate wheel: 5–8 full rotations + random stop
        val extraDegrees = (5 * 360 + kotlin.random.Random.nextInt(360)).toFloat()
        val totalRotation = wheelView.currentRotation + extraDegrees

        val animator = ValueAnimator.ofFloat(wheelView.currentRotation, totalRotation).apply {
            duration = 4000
            interpolator = DecelerateInterpolator(2.5f)
            addUpdateListener { anim ->
                wheelView.currentRotation = anim.animatedValue as Float
                wheelView.invalidate()
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(a: Animator) {}
                override fun onAnimationCancel(a: Animator) {}
                override fun onAnimationRepeat(a: Animator) {}
                override fun onAnimationEnd(a: Animator) {
                    // Wheel done — now call API
                    fetchSpinResult()
                }
            })
        }
        animator.start()
    }

    private fun fetchSpinResult() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.spinWheel(userId)
                }
                when {
                    resp.code() == 429 -> {
                        Toast.makeText(this@SpinWheelActivity,
                            "You've used both spins today! Come back tomorrow 🎰",
                            Toast.LENGTH_LONG).show()
                        resetUI()
                    }
                    resp.isSuccessful && resp.body()?.data != null -> {
                        val meal = resp.body()!!.data!!
                        currentMeal = meal
                        showResultCard(meal)
                    }
                    else -> {
                        Toast.makeText(this@SpinWheelActivity,
                            "Couldn't get a meal — try again!", Toast.LENGTH_SHORT).show()
                        resetUI()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@SpinWheelActivity,
                    "Network error", Toast.LENGTH_SHORT).show()
                resetUI()
            } finally {
                isSpinning = false
            }
        }
    }

    // ── Result card ───────────────────────────────────────────────────────────

    private fun showResultCard(meal: SpinWheelMeal) {
        val card = findViewById<LinearLayout>(R.id.layoutResultCard)


        // Meal image
        val img = findViewById<ImageView>(R.id.imgSpinMeal)
        if (meal.imageUrl.isNotEmpty()) {
            Glide.with(this).load(meal.imageUrl)
                .placeholder(R.drawable.bg_image_placeholder)
                .centerCrop().into(img)
        }

        // Meal info
        findViewById<TextView>(R.id.tvSpinMealName).text    = meal.mealName
        findViewById<TextView>(R.id.tvSpinMealMeta).text    =
            "${meal.mealTime} · ${meal.dietType} · ${meal.calories} cal"
        findViewById<TextView>(R.id.tvSpinMacros).text      =
            "P: ${meal.protein}g  C: ${meal.carbs}g  F: ${meal.fat}g"
        findViewById<TextView>(R.id.tvSpinReason).text      = meal.spinReason

        // Pricing
        findViewById<TextView>(R.id.tvSpinBasePrice).text   =
            "Original: ${String.format("%.2f", meal.basePrice)} EGP"
        findViewById<TextView>(R.id.tvSpinDiscount).text    =
            "−${meal.discountPct}% (${String.format("%.2f", meal.discountEgp)} EGP off)"
        findViewById<TextView>(R.id.tvSpinFinalPrice).text  =
            "${String.format("%.2f", meal.finalPrice)} EGP"

        // Discount code
        findViewById<TextView>(R.id.tvDiscountCode).text    = meal.discountCode
        findViewById<TextView>(R.id.tvCodeExpiry).text      = "Valid for 24 hours"

        // Animate card in
        card.visibility = View.VISIBLE
        card.alpha = 0f
        card.animate().alpha(1f).setDuration(400).start()

        setSpinButtonEnabled(false)
        findViewById<TextView>(R.id.btnSpinAgain).visibility = View.VISIBLE

        val spinsLeft = 2 - (currentMeal?.let { 1 } ?: 0)
        findViewById<TextView>(R.id.tvSpinsLeft)?.text =
            if (spinsLeft > 0) "$spinsLeft spin left today" else "No spins left today"
    }

    private fun hideResultCard() {
        findViewById<LinearLayout>(R.id.layoutResultCard).visibility = View.GONE
        findViewById<TextView>(R.id.btnSpinAgain).visibility = View.GONE
    }

    private fun resetUI() {
        hideResultCard()
        currentMeal = null
        setSpinButtonEnabled(true)
    }

    private fun setSpinButtonEnabled(enabled: Boolean) {
        val btn = findViewById<LinearLayout>(R.id.btnSpin)
        val tv  = btn.getChildAt(0) as? TextView
        btn.isEnabled = enabled
        btn.alpha = if (enabled) 1f else 0.5f
        tv?.text = if (enabled) "🎰  SPIN THE WHEEL" else "Spinning..."
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun copyCode(code: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Discount Code", code))
        Toast.makeText(this, "Code copied! 📋", Toast.LENGTH_SHORT).show()
    }

    private fun openCloudKitchen(meal: SpinWheelMeal) {
        val intent = Intent(this, CloudKitchenActivity::class.java).apply {
            putExtra("spin_meal_name",  meal.mealName)
            putExtra("spin_code",       meal.discountCode)
            putExtra("spin_discount",   meal.discountPct)
            putExtra("spin_final",      meal.finalPrice)
        }
        startActivity(intent)
    }

    private fun openTalabat(mealName: String) {
        val query = Uri.encode(mealName)
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.talabat.com/egypt/search?q=$query"))
        startActivity(intent)
        // Show code reminder
        currentMeal?.let {
            Toast.makeText(this,
                "Show code ${it.discountCode} on your next Cloud Kitchen order!",
                Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

// ─── Wheel Canvas View ────────────────────────────────────────────────────────

class WheelView(context: Context, private val segments: List<String>) : View(context) {

    var currentRotation = 0f

    // Fuelify green palette — alternating shades
    private val colors = listOf(
        Color.parseColor("#A8D832"),
        Color.parseColor("#C5E85A"),
        Color.parseColor("#8BC220"),
        Color.parseColor("#D4F07A"),
        Color.parseColor("#6FA814"),
        Color.parseColor("#B8DC48"),
        Color.parseColor("#4A6200"),
        Color.parseColor("#E2F59C")
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F97316")  // orange pointer
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) - 16f
        val sweepAngle = 360f / segments.size

        canvas.save()
        canvas.rotate(currentRotation, cx, cy)

        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        segments.forEachIndexed { i, label ->
            val startAngle = i * sweepAngle

            // Segment fill
            paint.color = colors[i % colors.size]
            paint.style = Paint.Style.FILL
            canvas.drawArc(oval, startAngle, sweepAngle, true, paint)

            // Segment border
            canvas.drawArc(oval, startAngle, sweepAngle, true, borderPaint)

            // Text
            val textAngle = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
            val textRadius = radius * 0.65f
            val tx = cx + (textRadius * cos(textAngle)).toFloat()
            val ty = cy + (textRadius * sin(textAngle)).toFloat()

            canvas.save()
            canvas.rotate((startAngle + sweepAngle / 2 + 90), tx, ty)
            textPaint.textSize = radius * 0.10f

            // Multi-line text
            val lines = label.split("\n")
            val lineH = textPaint.textSize * 1.3f
            val startY = ty - (lines.size - 1) * lineH / 2
            lines.forEachIndexed { li, line ->
                canvas.drawText(line, tx, startY + li * lineH - ty + ty, textPaint)
            }
            canvas.restore()
        }

        // Center circle
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius * 0.12f, paint)
        paint.color = Color.parseColor("#4A6200")
        canvas.drawCircle(cx, cy, radius * 0.08f, paint)

        canvas.restore()

        // Pointer triangle at top (outside wheel, doesn't rotate)
        val pSize = 32f
        val path = Path().apply {
            moveTo(cx, cy - radius - 4f)
            lineTo(cx - pSize / 2, cy - radius - pSize)
            lineTo(cx + pSize / 2, cy - radius - pSize)
            close()
        }
        canvas.drawPath(path, pointerPaint)
    }
}
