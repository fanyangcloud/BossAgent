package com.agent.boss.data.pref

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * 获取速度倍率因子 (移植并适配 BaseComplexTask 逻辑)
     * 快速 -> 延时变短 (x0.75)
     * 正常 -> 延时不变 (x1.0)
     * 慢速 -> 延时变长 (x1.5)
     */
    fun getSpeedFactor(): Float {
        return when (getSlideRate()) {
            "快速", "较快" -> 0.75f
            "慢速", "较慢", "极慢" -> 1.5f
            "正常" -> 1.0f
            else -> 1.0f
        }
    }

    fun getAdjustedDelay(originDelay: Long): Long {
        return (originDelay * getSpeedFactor()).toLong()
    }

    fun getSlideRate(): String = prefs.getString(KEY_SLIDE_RATE, "正常") ?: "正常"
    fun setSlideRate(rate: String) = prefs.edit().putString(KEY_SLIDE_RATE, rate).apply()

    fun getMaxDailyGreetings(): Int = prefs.getInt(KEY_MAX_DAILY_GREETINGS, 45)
    fun setMaxDailyGreetings(count: Int) = prefs.edit().putInt(KEY_MAX_DAILY_GREETINGS, count).apply()

    fun getMinSalaryFilterK(): Int = prefs.getInt(KEY_MIN_SALARY_K, 0)
    fun setMinSalaryFilterK(salaryK: Int) = prefs.edit().putInt(KEY_MIN_SALARY_K, salaryK).apply()

    fun isFilterOutsourcing(): Boolean = prefs.getBoolean(KEY_FILTER_OUTSOURCING, true)
    fun setFilterOutsourcing(filter: Boolean) = prefs.edit().putBoolean(KEY_FILTER_OUTSOURCING, filter).apply()

    fun isPaused(): Boolean = prefs.getBoolean(KEY_IS_PAUSED, false)
    fun setPaused(paused: Boolean) = prefs.edit().putBoolean(KEY_IS_PAUSED, paused).apply()

    companion object {
        private const val PREFS_NAME = "boss_agent_settings"
        private const val KEY_SLIDE_RATE = "slideRate"
        private const val KEY_MAX_DAILY_GREETINGS = "max_daily_greetings"
        private const val KEY_MIN_SALARY_K = "min_salary_k"
        private const val KEY_FILTER_OUTSOURCING = "filter_outsourcing"
        private const val KEY_IS_PAUSED = "isPause"

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
        }
    }
}
