package com.example.fuelify.onboarding.fragments

import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import com.example.fuelify.R
import com.example.fuelify.ui.DrumRollPicker

class Step10WorkoutDaysFragment : BaseOnboardingFragment() {
    private lateinit var picker: DrumRollPicker
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_step_10_workout_days, c, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        picker = view.findViewById(R.id.workoutDaysPicker)
        picker.unit = "days"
        picker.minValue = 1
        picker.maxValue = 7
        picker.value = vm.userData.exerciseDays.takeIf { it in 1..7 } ?: 4
    }
    override fun onContinueClicked() { vm.setExerciseDays(picker.value); host.goNext() }
}

class Step11TrainingPlaceFragment : BaseOnboardingFragment() {
    private val options = mapOf(
        R.id.cardGym    to "GYM",
        R.id.cardHome   to "Home",
        R.id.cardHybrid to "Hybrid"
    )
    private var selected = ""
    private val cards = mutableMapOf<Int, LinearLayout>()
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_step_11_training_place, c, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selected = vm.userData.trainingPlace
        options.forEach { (id, label) ->
            val card = view.findViewById<LinearLayout>(id)
            cards[id] = card
            card.setOnClickListener { selected = label; refreshUI() }
        }
        refreshUI()
    }
    private fun refreshUI() {
        options.forEach { (id, label) ->
            cards[id]?.setBackgroundResource(
                if (selected == label) R.drawable.bg_card_green else R.drawable.bg_card_white
            )
        }
    }
    override fun onContinueClicked() {
        if (selected.isEmpty()) { toast("Please choose where you train"); return }
        vm.setTrainingPlace(selected); host.goNext()
    }
}

class Step12MealsNumFragment : BaseOnboardingFragment() {
    private lateinit var picker: DrumRollPicker
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_step_12_meals_num, c, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        picker = view.findViewById(R.id.mealsPicker)
        picker.unit = "meals"
        picker.minValue = 1
        picker.maxValue = 6
        picker.value = vm.userData.mealsPerDay.takeIf { it in 1..6 } ?: 3
    }
    override fun onContinueClicked() { vm.setMealsPerDay(picker.value); host.goNext() }
}