package com.example.myspendings

import android.content.Context

object SessionManager {
    private const val PREFS_NAME = "MySpendings_Prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_MONTHLY_BUDGET = "monthly_budget"

    fun saveUser(context: Context, userId: Int, username: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    fun getUserId(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_USER_ID, -1)

    fun getUsername(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USERNAME, null)

    fun saveMonthlyBudget(context: Context, amount: Double) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_MONTHLY_BUDGET, amount.toFloat()).apply()
    }

    fun getMonthlyBudget(context: Context): Double =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_MONTHLY_BUDGET, 0f).toDouble()

    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    fun isLoggedIn(context: Context) = getUserId(context) != -1
}
