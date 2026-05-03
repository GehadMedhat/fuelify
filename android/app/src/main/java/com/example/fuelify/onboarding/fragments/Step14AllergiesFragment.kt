package com.example.fuelify.onboarding.fragments

import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import com.example.fuelify.R

class Step14AllergiesFragment : BaseOnboardingFragment() {

    private val chipIds = mapOf(
        R.id.chipDairy     to "Dairy",
        R.id.chipGluten    to "Gluten",
        R.id.chipNuts      to "Nuts",
        R.id.chipSoy       to "Soy",
        R.id.chipEggs      to "Eggs",
        R.id.chipShellfish to "Shellfish"
    )

    private val selected = mutableSetOf<String>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_step_14_allergies, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selected.addAll(vm.userData.allergies)

        chipIds.forEach { (id, label) ->
            val chip = view.findViewById<LinearLayout>(id)
            refreshChip(chip, label)
            chip.setOnClickListener {
                if (selected.contains(label)) selected.remove(label) else selected.add(label)
                refreshChip(chip, label)
            }
        }
    }

    private fun refreshChip(chip: LinearLayout, label: String) {
        chip.setBackgroundResource(
            if (selected.contains(label)) R.drawable.bg_card_red else R.drawable.bg_card_white
        )
    }

    override fun onContinueClicked() {
        // Allergies are optional — user can have none
        vm.setAllergies(selected.toList())
        host.goNext()
    }
}
