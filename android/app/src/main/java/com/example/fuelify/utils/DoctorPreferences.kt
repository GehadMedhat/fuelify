package com.example.fuelify.utils

import android.content.Context
import com.example.fuelify.data.api.models.DoctorProfile
import com.google.gson.Gson

object DoctorPreferences {

    private const val PREF_NAME  = "doctor_prefs"
    private const val KEY_ID      = "doctor_id"
    private const val KEY_PROFILE = "doctor_profile"

    fun saveDoctor(context: Context, profile: DoctorProfile) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_ID, profile.doctorId)
            .putString(KEY_PROFILE, Gson().toJson(profile))
            .apply()
    }

    fun getDoctorId(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_ID, -1)
    }

    fun getProfile(context: Context): DoctorProfile? {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROFILE, null) ?: return null
        return try { Gson().fromJson(json, DoctorProfile::class.java) } catch (e: Exception) { null }
    }

    /** Returns true if a doctor session is saved (i.e. they previously logged in). */
    fun isLoggedIn(context: Context): Boolean {
        return getDoctorId(context) != -1
    }

    /** Wipes all saved doctor session data — call on logout. */
    fun clear(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
