package com.example.fuelify.auth.network

import android.content.Context
import android.content.SharedPreferences

object SessionManager {

    private const val PREF_NAME           = "FuelifySession"
    private const val KEY_TOKEN           = "access_token"
    private const val KEY_USER_ID         = "user_id"
    private const val KEY_EMAIL           = "email"
    private const val KEY_FIRST_NAME      = "first_name"
    private const val KEY_LAST_NAME       = "last_name"
    private const val KEY_USERNAME        = "username"
    private const val KEY_PROFILE_PICTURE = "profile_picture"
    private const val KEY_VISIBILITY      = "visibility"
    private const val KEY_IS_ADMIN        = "is_admin"

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private fun getPrefs(): SharedPreferences? =
        appContext?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveToken(token: String) = getPrefs()?.edit()?.putString(KEY_TOKEN, token)?.apply()
    fun getToken(): String?      = getPrefs()?.getString(KEY_TOKEN, null)
    fun getBearerToken(): String = "Bearer ${getToken()}"

    fun saveUser(user: UserResponse) {
        getPrefs()?.edit()
            ?.putInt(KEY_USER_ID, user.id)
            ?.putString(KEY_EMAIL, user.email)
            ?.putString(KEY_FIRST_NAME, user.firstName)
            ?.putString(KEY_LAST_NAME, user.lastName)
            ?.putString(KEY_USERNAME, user.username)
            ?.putString(KEY_PROFILE_PICTURE, user.profilePicture)
            ?.putString(KEY_VISIBILITY, user.visibility)
            ?.putBoolean(KEY_IS_ADMIN, user.isAdmin ?: false)
            ?.apply()
    }

    // ── Explicit admin flag save — used after JWT decode in LoginActivity ─────
    fun saveAdminStatus(isAdmin: Boolean) =
        getPrefs()?.edit()?.putBoolean(KEY_IS_ADMIN, isAdmin)?.apply()

    fun getUserId(): Int        = getPrefs()?.getInt(KEY_USER_ID, -1) ?: -1
    fun getEmail(): String?     = getPrefs()?.getString(KEY_EMAIL, null)
    fun getFirstName(): String? = getPrefs()?.getString(KEY_FIRST_NAME, null)
    fun getLastName(): String?  = getPrefs()?.getString(KEY_LAST_NAME, null)
    fun getUsername(): String?  = getPrefs()?.getString(KEY_USERNAME, null)
    fun getProfilePicture(): String? = getPrefs()?.getString(KEY_PROFILE_PICTURE, null)
    fun getVisibility(): String?     = getPrefs()?.getString(KEY_VISIBILITY, "PRIVATE")

    fun isAdmin(): Boolean    = getPrefs()?.getBoolean(KEY_IS_ADMIN, false) ?: false
    fun isLoggedIn(): Boolean = getToken() != null

    fun clear() = getPrefs()?.edit()?.clear()?.apply()
}