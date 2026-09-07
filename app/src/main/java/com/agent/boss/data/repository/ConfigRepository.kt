package com.agent.boss.data.repository

import com.agent.boss.data.pref.AppSettings
import com.agent.boss.data.pref.EncryptedDataStore

class ConfigRepository(
    private val encryptedDataStore: EncryptedDataStore,
    private val appSettings: AppSettings
) {
    fun getDeepSeekApiKey(): String = encryptedDataStore.getDeepSeekApiKey()
    fun setDeepSeekApiKey(key: String) = encryptedDataStore.setDeepSeekApiKey(key)
    fun hasValidApiKey(): Boolean = encryptedDataStore.hasValidApiKey()

    fun getResumeMarkdown(): String = encryptedDataStore.getResumeMarkdown()
    fun setResumeMarkdown(markdown: String) = encryptedDataStore.setResumeMarkdown(markdown)

    fun getSpeedFactor(): Float = appSettings.getSpeedFactor()
    fun getAdjustedDelay(originDelay: Long): Long = appSettings.getAdjustedDelay(originDelay)

    fun getMaxDailyGreetings(): Int = appSettings.getMaxDailyGreetings()
    fun setMaxDailyGreetings(count: Int) = appSettings.setMaxDailyGreetings(count)

    fun getMinSalaryFilterK(): Int = appSettings.getMinSalaryFilterK()
    fun setMinSalaryFilterK(k: Int) = appSettings.setMinSalaryFilterK(k)

    fun isFilterOutsourcing(): Boolean = appSettings.isFilterOutsourcing()
    fun setFilterOutsourcing(filter: Boolean) = appSettings.setFilterOutsourcing(filter)

    fun isPaused(): Boolean = appSettings.isPaused()
    fun setPaused(paused: Boolean) = appSettings.setPaused(paused)
}
