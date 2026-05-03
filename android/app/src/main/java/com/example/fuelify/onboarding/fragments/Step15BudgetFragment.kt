package com.example.fuelify.onboarding.fragments

import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import com.example.fuelify.R

class Step15BudgetFragment : BaseOnboardingFragment() {

    private val options = mapOf(
        R.id.cardBudgetFriendly to "budget friendly",
        R.id.cardStandard       to "standard",
        R.id.cardPremium        to "premium"
    )
    private var selected = ""
    private val cards = mutableMapOf<Int, LinearLayout>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_step_15_budget, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selected = vm.userData.budget
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
        if (selected.isEmpty()) { toast("Please select a budget option"); return }
        vm.setBudget(selected)
        // This is the last step — host.goNext() will call viewModel.submitOnboarding()
        host.goNext()
    }
}
