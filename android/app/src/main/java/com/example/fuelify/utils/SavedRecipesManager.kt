package com.example.fuelify.utils

import android.content.Context
import com.example.fuelify.data.api.models.SearchMealItem
import org.json.JSONArray
import org.json.JSONObject

object SavedRecipesManager {

    private const val PREF_NAME = "fuelify_saved_recipes"
    private const val KEY_LIST  = "saved_recipes"

    fun getAll(context: Context): List<SearchMealItem> {
        val json = prefs(context).getString(KEY_LIST, "[]") ?: "[]"
        return deserialize(json)
    }

    fun isSaved(context: Context, mealId: Int): Boolean =
        getAll(context).any { it.mealId == mealId }

    fun save(context: Context, meal: SearchMealItem) {
        val current = getAll(context).toMutableList()
        if (current.none { it.mealId == meal.mealId }) {
            current.add(0, meal)
            persist(context, current)
        }
    }

    fun remove(context: Context, mealId: Int) {
        val updated = getAll(context).filter { it.mealId != mealId }
        persist(context, updated)
    }

    fun clearAll(context: Context) {
        prefs(context).edit().remove(KEY_LIST).apply()
    }

    private fun persist(context: Context, meals: List<SearchMealItem>) {
        prefs(context).edit()
            .putString(KEY_LIST, serialize(meals))
            .apply()
    }

    private fun serialize(meals: List<SearchMealItem>): String {
        val arr = JSONArray()
        meals.forEach { m ->
            arr.put(JSONObject().apply {
                put("mealId",            m.mealId)
                put("mealName",          m.mealName)
                put("imageUrl",          m.imageUrl)
                put("calories",          m.calories)
                put("prepTimeMinutes",   m.prepTimeMinutes)
                put("difficulty",        m.difficulty)
                put("isSuitable",        m.isSuitable)
                put("suitabilityReason", m.suitabilityReason)
                put("mealTime",          m.mealTime)
                put("dietType",          m.dietType)
                put("ecoScore",          m.ecoScore)
            })
        }
        return arr.toString()
    }

    private fun deserialize(json: String): List<SearchMealItem> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SearchMealItem(
                    mealId            = o.getInt("mealId"),
                    mealName          = o.getString("mealName"),
                    imageUrl          = o.optString("imageUrl", ""),
                    calories          = o.getInt("calories"),
                    prepTimeMinutes   = o.getInt("prepTimeMinutes"),
                    difficulty        = o.getString("difficulty"),
                    isSuitable        = o.getBoolean("isSuitable"),
                    suitabilityReason = o.optString("suitabilityReason", ""),
                    mealTime          = o.optString("mealTime", ""),
                    dietType          = o.optString("dietType", ""),
                    ecoScore          = o.optDouble("ecoScore", 0.0)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}