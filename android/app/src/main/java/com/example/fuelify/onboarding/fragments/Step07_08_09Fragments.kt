package com.example.fuelify.onboarding.fragments

import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import com.example.fuelify.R

// ─── Step 7: Activity Level ───────────────────────────────────────────────────

class Step07ActivityFragment : BaseOnboardingFragment() {

    private val options = mapOf(
        R.id.cardSedentary      to "sedentary",
        R.id.cardLightlyActive  to "lightly active",
        R.id.cardModeratelyActive to "moderately active",
        R.id.cardVeryActive     to "very active",
        R.id.cardAthlete        to "athlete"
    )
    private var selected = ""
    private val cards = mutableMapOf<Int, LinearLayout>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_step_07_activity, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selected = vm.userData.activityLevel
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
        if (selected.isEmpty()) { toast("Please select your activity level"); return }
        vm.setActivityLevel(selected)
        host.goNext()
    }
}

// ─── Step 8: Motivation ───────────────────────────────────────────────────────

class Step08MotivationFragment : BaseOnboardingFragment() {

    private val options = mapOf(
        R.id.cardHealthWellness to "health & wellness",
        R.id.cardStrength       to "strength & endurance",
        R.id.cardConfidence     to "confidence boost",
        R.id.cardWeightLoss     to "weight loss"
    )
    private var selected = ""
    private val cards = mutableMapOf<Int, LinearLayout>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_step_08_motivation, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selected = vm.userData.motivation
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
        if (selected.isEmpty()) { toast("Please select your motivation"); return }
        vm.setMotivation(selected)
        host.goNext()
    }
}

// ─── Step 9: Push-ups ─────────────────────────────────────────────────────────

class Step09PushupsFragment : BaseOnboardingFragment() {

    private val options = mapOf(
        R.id.cardBeginner     to "Beginner",
        R.id.cardIntermediate to "Intermediate",
        R.id.cardAdvanced     to "Advanced",
        R.id.cardAthletePushups to "Athlete"
    )
    private var selected = ""
    private val cards = mutableMapOf<Int, LinearLayout>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_step_09_pushups, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selected = vm.userData.fitnessLevel
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
        if (selected.isEmpty()) { toast("Please select your fitness level"); return }
        vm.setFitnessLevel(selected)
        host.goNext()
    }
}
