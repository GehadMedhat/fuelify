package com.example.fuelify.home

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.*
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*

class FamilyDashboardActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family_dashboard)
        userId = UserPreferences.getUserId(this)

        findViewById<ImageButton>(R.id.btnFamilyDashBack).setOnClickListener { finish() }
        findViewById<LinearLayout>(R.id.btnManageFamily).setOnClickListener {
            startActivity(Intent(this, FamilyDietActivity::class.java))
        }
        loadDashboard()
    }

    override fun onResume() { super.onResume(); loadDashboard() }

    private fun loadDashboard() {
        val loading = findViewById<ProgressBar>(R.id.progressFamilyDash)
        val content = findViewById<View>(R.id.layoutFamilyDashContent)
        val tvEmpty = findViewById<TextView>(R.id.tvFamilyDashEmpty)
        loading.visibility = View.VISIBLE
        content.visibility = View.GONE
        tvEmpty.visibility = View.GONE

        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getFamilyDashboard(userId)
                }
                loading.visibility = View.GONE
                if (resp.isSuccessful && resp.body()?.data != null) {
                    content.visibility = View.VISIBLE
                    bind(resp.body()!!.data!!)
                } else {
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = resp.body()?.message ?: "You're not in a family group yet.\nAsk someone to invite you!"
                }
            } catch (e: Exception) {
                loading.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "Network error. Pull down to refresh."
            }
        }
    }

    private fun bind(data: FamilyDashboardData) {
        // Header
        findViewById<TextView>(R.id.tvFamilyGroupName).text    = data.groupName
        findViewById<TextView>(R.id.tvFamilyWeekLabel).text    = data.weekLabel
        findViewById<TextView>(R.id.tvFamilyMemberCount).text  =
            "${data.members.size} members"

//        // Group summary stats
//        findViewById<TextView>(R.id.tvGroupTotalCal).text   = "${data.totalCaloriesBurnedWeek}"
//        findViewById<TextView>(R.id.tvGroupTotalMeals).text = "${data.totalMealsEatenWeek}"
//        findViewById<TextView>(R.id.tvGroupAvgStreak).text  = "${data.groupStreakAvg}"

        // Leaderboard
        bindLeaderboard(data.leaderboard)

        // Member cards
        val container = findViewById<LinearLayout>(R.id.containerFamilyMembers)
        container.removeAllViews()
        data.members.forEach { member ->
            container.addView(buildMemberCard(member, data.members.size))
        }
    }

    private fun bindLeaderboard(entries: List<FamilyLeaderboardEntry>) {
        val container = findViewById<LinearLayout>(R.id.containerLeaderboard)
        container.removeAllViews()

        entries.forEach { entry ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_leaderboard_entry, container, false)

            row.findViewById<TextView>(R.id.tvLbRank).text   =
                entry.medal.ifBlank { "#${entry.rank}" }
            row.findViewById<TextView>(R.id.tvLbName).text   = entry.name
            row.findViewById<TextView>(R.id.tvLbStreak).text =
                "🔥 ${entry.streakDays} day streak"
            row.findViewById<TextView>(R.id.tvLbPoints).text = "${entry.points} pts"

            // Highlight the current user
            if (entry.userId == userId) {
                row.setBackgroundColor(0xFFFFF7ED.toInt())
                row.findViewById<TextView>(R.id.tvLbName).apply {
                    text = "${entry.name} (You)"
                    setTextColor(0xFFF97316.toInt())
                }
            }
            container.addView(row)
        }
    }

    private fun buildMemberCard(member: FamilyMemberDashboard, totalMembers: Int): View {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_family_dashboard_member, null, false)

        // Avatar + online dot
        val tvAvatar = card.findViewById<TextView>(R.id.tvMemberAvatar)
        tvAvatar.text = member.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        tvAvatar.setBackgroundColor(avatarColor(member.userId))

        val dotOnline = card.findViewById<View>(R.id.viewOnlineDot)
        dotOnline.setBackgroundColor(
            if (member.isOnline) 0xFF22C55E.toInt() else 0xFFD1D5DB.toInt()
        )

        // Name + role + goal
        card.findViewById<TextView>(R.id.tvMemberName).text = member.name +
            if (member.userId == userId) " (You)" else ""
        card.findViewById<TextView>(R.id.tvMemberRole).text = when (member.role) {
            "admin" -> "👑 Admin"
            else    -> "👤 Member"
        }
        card.findViewById<TextView>(R.id.tvMemberGoal).text = member.goal.ifBlank { "No goal set" }

        // Streak
        val tvStreak = card.findViewById<TextView>(R.id.tvMemberStreak)
        tvStreak.text = "🔥 ${member.streakDays} day streak"
        tvStreak.setTextColor(when {
            member.streakDays >= 30 -> 0xFFEF4444.toInt()
            member.streakDays >= 7  -> 0xFFF97316.toInt()
            member.streakDays >= 1  -> 0xFFF59E0B.toInt()
            else                    -> 0xFF9CA3AF.toInt()
        })

        // Calorie progress bar
        card.findViewById<TextView>(R.id.tvMemberCalories).text =
            "${member.caloriesEaten} / ${member.caloriesGoal} kcal today"
        val calBar = card.findViewById<ProgressBar>(R.id.progressMemberCal)
        calBar.progress = member.caloriePct

        // Water
        card.findViewById<TextView>(R.id.tvMemberWater).text =
            "💧 ${member.waterGlasses}/8 glasses"

        // Week stats row
        card.findViewById<TextView>(R.id.tvMemberWeekMeals).text =
            "${member.mealsEatenWeek}/${member.mealsTotalWeek} meals"
        card.findViewById<TextView>(R.id.tvMemberWeekWorkouts).text =
            "${member.workoutsDoneWeek}/${member.workoutsGoalWeek} workouts"
        card.findViewById<TextView>(R.id.tvMemberWeekCalBurned).text =
            "${member.caloriesBurnedWeek} kcal burned"

        // Week workout dots (Mon–Sun)
        val dotContainer = card.findViewById<LinearLayout>(R.id.containerWeekDots)
        dotContainer.removeAllViews()
        val today = java.time.LocalDate.now()
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        // Use Calendar for better compatibility across Android versions
        val calendar = java.util.Calendar.getInstance()
        // Calendar days: Sun=1, Mon=2, Tue=3... Sat=7.
        // We convert it so Mon=0, Tue=1 ... Sun=6
        val currentDayIdx = (calendar.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7

        val dayLabels = listOf("M","T","W","T","F","S","S")

        member.weekDots.forEachIndexed { idx, done ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val isToday = idx == currentDayIdx

            val dot = View(this).apply {
                val size = (10 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    bottomMargin = (4 * resources.displayMetrics.density).toInt()
                }

                // Set the circular background first
                setBackgroundResource(R.drawable.bg_recommended_card)

                // Apply color filter to the background so we keep the shape
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    when {
                        done && isToday -> 0xFFF97316.toInt() // Active Today
                        done            -> 0xFF22C55E.toInt() // Done
                        else            -> 0xFFE5E7EB.toInt() // Empty
                    }
                )
            }

            val label = TextView(this).apply {
                text = dayLabels[idx]
                textSize = 9f
                // Highlight today's text label
                setTextColor(if (isToday) 0xFFF97316.toInt() else 0xFF9CA3AF.toInt())
                gravity = android.view.Gravity.CENTER
            }

            col.addView(dot)
            col.addView(label)
            dotContainer.addView(col)
        }

        return card
    }

    // Consistent color per userId
    private fun avatarColor(uid: Int): Int {
        val colors = listOf(
            0xFFF97316.toInt(), 0xFF22C55E.toInt(), 0xFF3B82F6.toInt(),
            0xFFEC4899.toInt(), 0xFFA855F7.toInt(), 0xFF14B8A6.toInt()
        )
        return colors[uid % colors.size]
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
