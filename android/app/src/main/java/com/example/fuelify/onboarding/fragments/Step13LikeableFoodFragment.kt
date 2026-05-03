package com.example.fuelify.onboarding.fragments

import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import com.example.fuelify.R

class Step13LikeableFoodFragment : BaseOnboardingFragment() {

    private val foodIds = mapOf(
        R.id.foodChicken to "Chicken",
        R.id.foodMeat    to "Meat",
        R.id.foodFish    to "Fish",
        R.id.foodEggs    to "Eggs",
        R.id.foodTuna    to "Tuna",
        R.id.foodSalmon  to "Salmon",
        R.id.foodYogurt  to "Greek Yogurt",
        R.id.foodOats    to "Oats",
        R.id.foodRice    to "Rice",
        R.id.foodPasta   to "Pasta",
        R.id.foodBread   to "Bread"
    )

    private val selected = mutableSetOf<String>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_step_13_likeable_food, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selected.addAll(vm.userData.likedFoods)

        foodIds.forEach { (id, label) ->
            val card = view.findViewById<LinearLayout>(id)
            refreshCard(card, label)
            card.setOnClickListener {
                if (selected.contains(label)) selected.remove(label) else selected.add(label)
                refreshCard(card, label)
            }
        }
    }

    private fun refreshCard(card: LinearLayout, label: String) {
        card.setBackgroundResource(
            if (selected.contains(label)) R.drawable.bg_card_green else R.drawable.bg_card_white
        )
    }

    override fun onContinueClicked() {
        if (selected.isEmpty()) { toast("Please select at least one food"); return }
        vm.setLikedFoods(selected.toList())
        host.goNext()
    }
}
