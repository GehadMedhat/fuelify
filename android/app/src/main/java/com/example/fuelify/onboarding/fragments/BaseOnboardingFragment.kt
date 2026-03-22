package com.example.fuelify.onboarding.fragments

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.fuelify.onboarding.OnboardingActivity
import com.example.fuelify.onboarding.OnboardingViewModel

abstract class BaseOnboardingFragment : Fragment() {

    protected val vm: OnboardingViewModel by activityViewModels()
    protected val host: OnboardingActivity get() = requireActivity() as OnboardingActivity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Wire progress dots
        val dotsContainer = view.findViewById<LinearLayout>(com.example.fuelify.R.id.dotsContainer)
        dotsContainer?.let { host.setupDots(it) }

        // Wire back button (common to all layouts)
        view.findViewById<LinearLayout>(com.example.fuelify.R.id.btnBack)
            ?.setOnClickListener { host.goBack() }

        // Wire continue button
        view.findViewById<LinearLayout>(com.example.fuelify.R.id.btnContinue)
            ?.setOnClickListener { onContinueClicked() }
    }

    /** Each fragment overrides this to validate input, save to ViewModel, then call host.goNext() */
    protected abstract fun onContinueClicked()

    protected fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
