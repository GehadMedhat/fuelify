package com.example.fuelify.home

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.*
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*

class FamilyDietActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1
    private var currentGroup: FamilyGroup? = null
    private var isAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family_diet)

        userId = UserPreferences.getUserId(this)

        findViewById<ImageButton>(R.id.btnFamilyBack).setOnClickListener { finish() }
        setupBottomNav()

        // Add member button — always open sheet, backend enforces admin check
        findViewById<LinearLayout>(R.id.btnAddMember).setOnClickListener {
            showInviteSheet()
        }

        // Add grocery button
        findViewById<LinearLayout>(R.id.btnAddFamilyGrocery).setOnClickListener {
            showAddGrocerySheet()
        }

        // Clear checked
        findViewById<TextView>(R.id.btnClearFamilyChecked).setOnClickListener {
            clearChecked()
        }

        // Rename family
        findViewById<TextView>(R.id.tvFamilyGroupName).setOnClickListener {
            if (isAdmin) showRenameDialog()
        }

        loadFamily()
    }

    override fun onResume() {
        super.onResume()
        loadFamily()
    }

    // ── Load all data ─────────────────────────────────────────────────────────

    private fun loadFamily() {
        showLoading(true)
        scope.launch {
            showLoading(true)
            // Load family group
            try {
                val familyResp = withContext(Dispatchers.IO) { RetrofitClient.api.getFamily(userId) }
                if (familyResp.isSuccessful && familyResp.body()?.success == true) {
                    currentGroup = familyResp.body()!!.data
                    isAdmin = currentGroup?.createdBy == userId
                    bindFamilyGroup(currentGroup!!)
                }
            } catch (e: Exception) {
                Toast.makeText(this@FamilyDietActivity, "Family error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            // Load grocery independently
            try {
                val groceryResp = withContext(Dispatchers.IO) { RetrofitClient.api.getFamilyGrocery(userId) }
                if (groceryResp.isSuccessful && groceryResp.body()?.success == true) {
                    bindGroceryList(groceryResp.body()!!.data ?: emptyList())
                } else {
                    android.util.Log.e("FAMILY", "Grocery load failed: ${groceryResp.code()} ${groceryResp.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("FAMILY", "Grocery exception: ${e.message}")
            }
            showLoading(false)
        }
    }

    private fun showLoading(show: Boolean) {
        findViewById<View>(R.id.pbFamily)?.visibility = if (show) View.VISIBLE else View.GONE
    }

    // Refresh only the grocery list without reloading the whole screen
    private fun refreshGrocery() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getFamilyGrocery(userId)
                }
                if (resp.isSuccessful && resp.body()?.success == true) {
                    bindGroceryList(resp.body()!!.data ?: emptyList())
                } else {
                    Toast.makeText(this@FamilyDietActivity,
                        "Could not load grocery list: ${resp.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@FamilyDietActivity,
                    "Grocery error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Bind family group ─────────────────────────────────────────────────────

    private fun bindFamilyGroup(group: FamilyGroup) {
        // Group name (tap to rename if admin)
        val tvName = findViewById<TextView>(R.id.tvFamilyGroupName)
        tvName.text = group.name
        if (isAdmin) {
            tvName.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_edit_pencil, 0)
        }

        // Stats
        findViewById<TextView>(R.id.tvTotalMeals).text = "${group.totalMealsPlanned} meals planned"

        // Week strip
        bindWeekStrip(group.weekDays)

        // Members
        val container = findViewById<LinearLayout>(R.id.containerFamilyMembers)
        container.removeAllViews()
        group.members.forEach { member ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_family_member, container, false)

            // Avatar initial
            card.findViewById<TextView>(R.id.tvMemberAvatar).text =
                member.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

            card.findViewById<TextView>(R.id.tvMemberName).text = member.name
            card.findViewById<TextView>(R.id.tvMemberGoal).text = member.goal.replaceFirstChar { it.uppercase() }
            card.findViewById<TextView>(R.id.tvMemberCalories).text = "${member.caloriesGoal}"

            // Calories eaten progress
            val eaten     = member.caloriesEatenToday
            val goal      = member.caloriesGoal.takeIf { it > 0 } ?: 2000
            val remaining = (goal - eaten).coerceAtLeast(0)
            val progress  = ((eaten.toFloat() / goal) * 100).toInt().coerceIn(0, 100)

            card.findViewById<TextView>(R.id.tvCaloriesEaten).text     = "$eaten kcal eaten"
            card.findViewById<TextView>(R.id.tvCaloriesRemaining).text = "$remaining remaining"

            // Set progress bar width as percentage of parent
            val progressBar = card.findViewById<View>(R.id.viewCaloriesProgress)
            progressBar.post {
                val parentWidth = (progressBar.parent as android.view.View).width
                val params = progressBar.layoutParams
                params.width = (parentWidth * progress / 100)
                progressBar.layoutParams = params
            }

            // Streak
            val tvStreak = card.findViewById<TextView>(R.id.tvMemberStreak)
            if (member.streakDays > 0) {
                tvStreak.visibility = View.VISIBLE
                tvStreak.text = "🔥 ${member.streakDays} day streak"
            } else {
                tvStreak.visibility = View.GONE
            }

            // Role badge
            val tvRole = card.findViewById<TextView>(R.id.tvMemberRole)
            if (member.role == "admin") {
                tvRole.visibility = View.VISIBLE
            } else {
                tvRole.visibility = View.GONE
            }

            // Remove button (admin only, not self)
            val btnRemove = card.findViewById<TextView>(R.id.btnRemoveMember)
            if (isAdmin && member.userId != userId) {
                btnRemove.visibility = View.VISIBLE
                btnRemove.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Remove Member")
                        .setMessage("Remove ${member.name} from the family?")
                        .setPositiveButton("Remove") { _, _ -> removeMember(member.memberId) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } else {
                btnRemove.visibility = View.GONE
            }

            container.addView(card)
        }
    }

    // ── Week strip ────────────────────────────────────────────────────────────

    private fun bindWeekStrip(days: List<WeekDay>) {
        val container = findViewById<LinearLayout>(R.id.containerWeekStrip)
        container.removeAllViews()
        days.forEach { day ->
            val cell = LayoutInflater.from(this)
                .inflate(R.layout.item_week_day_cell, container, false)

            cell.findViewById<TextView>(R.id.tvWeekDayLabel).text = day.dayLabel
            cell.findViewById<TextView>(R.id.tvWeekDayNumber).text = day.dayNumber.toString()
            cell.findViewById<TextView>(R.id.tvWeekDayMonth).text = day.monthLabel

            val circle = cell.findViewById<LinearLayout>(R.id.layoutDayCircle)
            when {
                day.isToday -> {
                    circle.setBackgroundResource(R.drawable.bg_day_today)
                    cell.findViewById<TextView>(R.id.tvWeekDayLabel).setTextColor(0xFFF97316.toInt())
                }
                day.hasPlans -> {
                    circle.setBackgroundResource(R.drawable.bg_badge_completed)
                }
                else -> {
                    circle.setBackgroundResource(R.drawable.bg_recommended_card)
                    cell.findViewById<TextView>(R.id.tvWeekDayNumber)
                        .setTextColor(0xFF9CA3AF.toInt())
                }
            }
            container.addView(cell)
        }
    }

    // ── Grocery list ──────────────────────────────────────────────────────────

    private fun bindGroceryList(items: List<FamilyGroceryItem>) {
        val container = findViewById<LinearLayout>(R.id.containerFamilyGrocery)
        container.removeAllViews()

        if (items.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "No items yet. Tap + Add to add shared groceries."
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 32, 0, 32)
                setTextColor(0xFF737373.toInt())
            })
            return
        }

        items.forEach { item ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_family_grocery, container, false)

            val cb     = row.findViewById<CheckBox>(R.id.cbFamilyGrocery)
            val tvName = row.findViewById<TextView>(R.id.tvFamilyGroceryName)
            val tvQty  = row.findViewById<TextView>(R.id.tvFamilyGroceryQty)
            val tvBy   = row.findViewById<TextView>(R.id.tvFamilyGroceryAddedBy)

            cb.isChecked = item.isChecked
            tvName.text  = item.itemName
            tvQty.text   = item.quantity
            tvBy.text    = "Added by ${item.addedByName}" +
                (if (item.checkedByName != null) " · ✓ ${item.checkedByName}" else "")

            fun applyStyle(checked: Boolean) {
                if (checked) {
                    tvName.paintFlags = tvName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    tvName.setTextColor(0xFF9CA3AF.toInt())
                    row.alpha = 0.6f
                } else {
                    tvName.paintFlags = tvName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    tvName.setTextColor(0xFF171717.toInt())
                    row.alpha = 1f
                }
            }
            applyStyle(item.isChecked)

            cb.setOnCheckedChangeListener { _, checked ->
                applyStyle(checked)
                scope.launch {
                    withContext(Dispatchers.IO) {
                        RetrofitClient.api.checkFamilyGroceryItem(
                            userId, item.itemId,
                            CheckFamilyGroceryRequest(checked)
                        )
                    }
                    // Refresh to show checkedByName
                    val resp = withContext(Dispatchers.IO) { RetrofitClient.api.getFamilyGrocery(userId) }
                    if (resp.isSuccessful) bindGroceryList(resp.body()?.data ?: emptyList())
                }
            }

            // Long press → delete (added by me, or admin)
            row.setOnLongClickListener {
                if (item.addedBy == userId || isAdmin) {
                    AlertDialog.Builder(this)
                        .setTitle("Remove item")
                        .setMessage("Remove \"${item.itemName}\" from the list?")
                        .setPositiveButton("Remove") { _, _ ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    RetrofitClient.api.deleteFamilyGroceryItem(userId, item.itemId)
                                }
                                loadFamily()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                true
            }
            container.addView(row)
        }
    }

    // ── Invite sheet ──────────────────────────────────────────────────────────

    private fun showInviteSheet() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.bottom_sheet_invite_member)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            attributes?.windowAnimations = android.R.style.Animation_InputMethod
        }

        val etEmail       = dialog.findViewById<EditText>(R.id.etInviteEmail)
        val btnSearch     = dialog.findViewById<LinearLayout>(R.id.btnSearchEmail)
        val btnSend       = dialog.findViewById<LinearLayout>(R.id.btnSendInvite)
        val tvError       = dialog.findViewById<TextView>(R.id.tvInviteError)
        val layoutPreview = dialog.findViewById<LinearLayout>(R.id.layoutMemberPreview)
        val tvAvatar      = dialog.findViewById<TextView>(R.id.tvPreviewAvatar)
        val tvName        = dialog.findViewById<TextView>(R.id.tvPreviewName)
        val tvGoal        = dialog.findViewById<TextView>(R.id.tvPreviewGoal)

        var foundUserId: Int? = null

        fun showError(msg: String) {
            tvError.text = msg
            tvError.visibility = View.VISIBLE
            layoutPreview.visibility = View.GONE
            btnSend.visibility = View.GONE
        }

        fun showPreview(name: String, goal: String, uid: Int, alreadyMember: Boolean) {
            tvError.visibility = View.GONE
            tvAvatar.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            tvName.text   = name
            tvGoal.text   = if (alreadyMember) "⚠️ Already in your family" else goal
            layoutPreview.visibility = View.VISIBLE
            if (alreadyMember) {
                btnSend.visibility = View.GONE
                tvGoal.setTextColor(0xFFF97316.toInt())
            } else {
                foundUserId = uid
                btnSend.visibility = View.VISIBLE
                tvGoal.setTextColor(0xFF737373.toInt())
            }
        }

        // Search button — lookup user by email
        btnSearch.setOnClickListener {
            val email = etEmail.text.toString().trim()
            when {
                email.isEmpty() -> showError("Please enter an email address first.")
                !email.contains("@") -> showError("That doesn't look like a valid email address.")
                else -> {
                    tvError.visibility = View.GONE
                    scope.launch {
                        try {
                            val resp = withContext(Dispatchers.IO) {
                                RetrofitClient.api.lookupFamilyMember(userId, email)
                            }
                            if (resp.isSuccessful && resp.body()?.success == true) {
                                val preview = resp.body()!!.data!!
                                showPreview(preview.name, preview.goal, preview.userId, preview.alreadyMember)
                            } else {
                                // For error responses, message is in errorBody not body
                                val msg = try {
                                    val errorJson = resp.errorBody()?.string() ?: ""
                                    val jsonObj = org.json.JSONObject(errorJson)
                                    jsonObj.optString("message", "").ifEmpty { null }
                                } catch (ex: Exception) { null }
                                    ?: resp.body()?.message
                                    ?: when (resp.code()) {
                                        404 -> "No Fuelify account found for this email."
                                        409 -> "This person is already in your family."
                                        403 -> "Only the family admin can add members."
                                        400 -> "Please enter a valid email address."
                                        else -> "Something went wrong (${resp.code()}). Try again."
                                    }
                                showError(msg)
                            }
                        } catch (e: Exception) {
                            showError("Network error — check your connection.")
                        }
                    }
                }
            }
        }

        // Also trigger search on keyboard "Search" action
        etEmail.setOnEditorActionListener { _, _, _ ->
            btnSearch.performClick(); true
        }

        // Add button — only visible after a valid non-member is found
        btnSend.setOnClickListener {
            val uid = foundUserId ?: return@setOnClickListener
            val email = etEmail.text.toString().trim()
            scope.launch {
                try {
                    val resp = withContext(Dispatchers.IO) {
                        RetrofitClient.api.inviteFamilyMember(userId, InviteMemberRequest(email))
                    }
                    if (resp.isSuccessful && resp.body()?.success == true) {
                        dialog.dismiss()
                        Toast.makeText(this@FamilyDietActivity, "✓ ${tvName.text} added to the family!", Toast.LENGTH_SHORT).show()
                        loadFamily()
                    } else {
                        val msg = when (resp.code()) {
                            403  -> "Only the family admin can add members."
                            409  -> "${tvName.text} is already in your family."
                            404  -> "Account not found — email may have changed."
                            else -> resp.body()?.message ?: "Failed to add member, please try again."
                        }
                        showError(msg)
                    }
                } catch (e: Exception) {
                    showError("Network error — check your connection.")
                }
            }
        }

        dialog.show()
    }

    // ── Add grocery sheet ─────────────────────────────────────────────────────

    private fun showAddGrocerySheet() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.bottom_sheet_family_grocery)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            attributes?.windowAnimations = android.R.style.Animation_InputMethod
        }

        val etName = dialog.findViewById<EditText>(R.id.etFamilyGroceryName)
        val etQty  = dialog.findViewById<EditText>(R.id.etFamilyGroceryQty)
        val btnAdd = dialog.findViewById<LinearLayout>(R.id.btnConfirmFamilyGrocery)

        // Quick item chips
        val quickItems = listOf("🍗 Chicken", "🥦 Broccoli", "🥚 Eggs", "🍌 Bananas",
            "🥛 Milk", "🍚 Rice", "🫒 Olive Oil", "🥑 Avocado")
        val chipContainer = dialog.findViewById<LinearLayout>(R.id.containerQuickChips)
        quickItems.forEach { item ->
            val chip = TextView(this).apply {
                text = item
                textSize = 12f
                setPadding(24, 16, 24, 16)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = 8 }
                setBackgroundResource(R.drawable.bg_recommended_card)
                setTextColor(0xFF374151.toInt())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    etName.setText(item.drop(2).trim())  // remove emoji
                }
            }
            chipContainer.addView(chip)
        }

        btnAdd.setOnClickListener {
            val name = etName.text.toString().trim()
            val qty  = etQty.text.toString().trim().ifEmpty { "1" }
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter an item name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            scope.launch {
                try {
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.api.addFamilyGroceryItem(
                            userId, AddFamilyGroceryRequest(name, qty)
                        )
                    }
                    if (response.isSuccessful) {
                        dialog.dismiss()
                        refreshGrocery()
                    } else {
                        Toast.makeText(this@FamilyDietActivity,
                            "Failed to add: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@FamilyDietActivity,
                        "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    // ── Remove member ─────────────────────────────────────────────────────────

    private fun removeMember(memberId: Int) {
        scope.launch {
            withContext(Dispatchers.IO) {
                RetrofitClient.api.removeFamilyMember(userId, memberId)
            }
            loadFamily()
        }
    }

    // ── Clear checked ─────────────────────────────────────────────────────────

    private fun clearChecked() {
        scope.launch {
            withContext(Dispatchers.IO) {
                RetrofitClient.api.clearCheckedFamilyItems(userId)
            }
            loadFamily()
        }
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────

    private fun showRenameDialog() {
        val et = EditText(this).apply {
            setText(currentGroup?.name ?: "My Family")
            setPadding(40, 20, 40, 20)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename Family Group")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                val newName = et.text.toString().trim().ifEmpty { "My Family" }
                scope.launch {
                    withContext(Dispatchers.IO) {
                        RetrofitClient.api.renameFamilyGroup(userId, RenameFamilyRequest(newName))
                    }
                    loadFamily()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navDiet).setOnClickListener {
            startActivity(Intent(this, DietActivity::class.java)); finish()
        }
        listOf(R.id.navWorkouts, R.id.navStats, R.id.navProfile).forEach { id ->
            findViewById<LinearLayout>(id).setOnClickListener {
                Toast.makeText(this, "Coming soon!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
