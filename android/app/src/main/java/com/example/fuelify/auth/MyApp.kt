package com.example.fuelify.auth

import android.app.Application
import com.example.fuelify.auth.network.SessionManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionManager.init(this)
    }
}