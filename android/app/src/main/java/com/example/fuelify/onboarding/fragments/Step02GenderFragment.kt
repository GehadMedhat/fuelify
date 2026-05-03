package com.example.fuelify.onboarding.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.example.fuelify.R

class Step02GenderFragment : BaseOnboardingFragment() {

    private lateinit var cardMale: LinearLayout
    private lateinit var cardFemale: LinearLayout
    private var selected = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?) =
        inflater.inflate(R.layout.fragment_step_02_gender, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cardMale = view.findViewById(R.id.cardMale)
        cardFemale = view.findViewById(R.id.cardFemale)

        // Restore previous selection
        selected = vm.userData.gender
        refreshUI()

        cardMale.setOnClickListener { selected = "Male"; refreshUI() }
        cardFemale.setOnClickListener { selected = "Female"; refreshUI() }
    }

    private fun refreshUI() {
        cardMale.setBackgroundResource(
            if (selected == "Male") R.drawable.bg_gender_selected else R.drawable.bg_gender_default
        )
        cardFemale.setBackgroundResource(
            if (selected == "Female") R.drawable.bg_gender_selected else R.drawable.bg_gender_default
        )
    }

    override fun onContinueClicked() {
        if (selected.isEmpty()) { toast("Please select your gender"); return }
        vm.setGender(selected)
        host.goNext()
    }
}
