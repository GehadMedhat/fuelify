package com.example.fuelify.onboarding.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.example.fuelify.R

class Step01NameFragment : BaseOnboardingFragment() {

    private lateinit var etName: EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?) =
        inflater.inflate(R.layout.fragment_step_01_name, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        etName = view.findViewById(R.id.etName)
        // Restore if navigated back
        if (vm.userData.name.isNotEmpty()) etName.setText(vm.userData.name)
    }

    override fun onContinueClicked() {
        val name = etName.text.toString().trim()
        if (name.isEmpty()) { toast("Please enter your name"); return }
        vm.setName(name)
        host.goNext()
    }
}
