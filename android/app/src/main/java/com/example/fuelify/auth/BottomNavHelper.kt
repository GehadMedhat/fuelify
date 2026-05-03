package com.example.fuelify.auth

import android.content.Intent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity

enum class NavTab { HOME, WORKOUTS, DIET, STATISTICS, PROFILE }

object BottomNavHelper {

    fun setup(activity: AppCompatActivity, activeTab: NavTab) {
        // Use null-safe lookups — activities without a bottom nav just skip setup
        val navHome       = activity.findViewById<LinearLayout>(R.id.navHome)       ?: return
        val navWorkouts   = activity.findViewById<LinearLayout>(R.id.navWorkouts)   ?: return
        val navDiet       = activity.findViewById<LinearLayout>(R.id.navDiet)       ?: return
        val navStatistics = activity.findViewById<LinearLayout>(R.id.navStatistics) ?: return
        val navProfile    = activity.findViewById<LinearLayout>(R.id.navProfile)    ?: return

        setTab(activity, navHome,       R.id.iconHome,       R.id.labelHome,       activeTab == NavTab.HOME,       R.drawable.ic_home_selected,       R.drawable.ic_home)
        setTab(activity, navWorkouts,   R.id.iconWorkouts,   R.id.labelWorkouts,   activeTab == NavTab.WORKOUTS,   R.drawable.ic_workout_selected,    R.drawable.ic_workout)
        setTab(activity, navDiet,       R.id.iconDiet,       R.id.labelDiet,       activeTab == NavTab.DIET,       R.drawable.ic_diet_selected,       R.drawable.ic_diet)
        setTab(activity, navStatistics, R.id.iconStatistics, R.id.labelStatistics, activeTab == NavTab.STATISTICS, R.drawable.ic_statistics_selected, R.drawable.ic_statistics)
        setTab(activity, navProfile,    R.id.iconProfile,    R.id.labelProfile,    activeTab == NavTab.PROFILE,    R.drawable.ic_profile_selected,    R.drawable.ic_profile)

        if (activeTab != NavTab.PROFILE) {
            navProfile.setOnClickListener {
                activity.startActivity(Intent(activity, ProfileActivity::class.java))
                activity.finish()
            }
        }
    }

    private fun setTab(
        activity: AppCompatActivity,
        container: LinearLayout,
        iconId: Int,
        labelId: Int,
        isActive: Boolean,
        activeDrawable: Int,
        inactiveDrawable: Int
    ) {
        val icon  = activity.findViewById<ImageView>(iconId)   ?: return
        val label = activity.findViewById<TextView>(labelId)   ?: return

        if (isActive) {
            icon.setImageResource(activeDrawable)
            label.setTextColor(android.graphics.Color.parseColor("#A3E635"))
        } else {
            icon.setImageResource(inactiveDrawable)
            label.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
        }
    }
}