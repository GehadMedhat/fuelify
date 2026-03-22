package com.example.fuelify.onboarding.fragments

import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import com.example.fuelify.R

class Step06GoalFragment : BaseOnboardingFragment() {

    private val options = mapOf(
        R.id.cardLoseWeight     to "lose weight",
        R.id.cardMaintainWeight to "maintain weight",
        R.id.cardGainWeight     to "gain weight",
        R.id.cardBuildMuscle    to "build muscle"
    )

    private var selected = ""
    private val cards = mutableMapOf<Int, LinearLayout>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_step_06_goal, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selected = vm.userData.goal

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
        if (selected.isEmpty()) { toast("Please select your goal"); return }
        vm.setGoal(selected)
        host.goNext()
    }
}
