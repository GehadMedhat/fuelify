package com.example.fuelify.onboarding.fragments

import android.os.Bundle
import android.view.*
import com.example.fuelify.R
import com.example.fuelify.ui.DrumRollPicker

class Step03AgeFragment : BaseOnboardingFragment() {
    private lateinit var picker: DrumRollPicker
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_step_03_age, c, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        picker = view.findViewById(R.id.agePicker)
        picker.unit = "years"
        picker.minValue = 10
        picker.maxValue = 100
        picker.value = vm.userData.age.takeIf { it in 10..100 } ?: 18
    }
    override fun onContinueClicked() { vm.setAge(picker.value); host.goNext() }
}

class Step04HeightFragment : BaseOnboardingFragment() {
    private lateinit var picker: DrumRollPicker
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_step_04_height, c, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        picker = view.findViewById(R.id.heightPicker)
        picker.unit = "cm"
        picker.minValue = 100
        picker.maxValue = 230
        picker.value = vm.userData.heightCm.takeIf { it in 100..230 } ?: 170
    }
    override fun onContinueClicked() { vm.setHeight(picker.value); host.goNext() }
}

class Step05WeightFragment : BaseOnboardingFragment() {
    private lateinit var picker: DrumRollPicker
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_step_05_weight, c, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        picker = view.findViewById(R.id.weightPicker)
        picker.unit = "kg"
        picker.minValue = 30
        picker.maxValue = 200
        picker.value = vm.userData.weightKg.takeIf { it in 30..200 } ?: 70
    }
    override fun onContinueClicked() { vm.setWeight(picker.value); host.goNext() }
}