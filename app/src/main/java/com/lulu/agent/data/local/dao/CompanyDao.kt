package com.lulu.agent.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lulu.agent.data.local.entity.CompanyEntity

@Dao
interface CompanyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(company: CompanyEntity): Long

    @Query("SELECT * FROM companies WHERE company_name = :name LIMIT 1")
    suspend fun findByName(name: String): CompanyEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM companies WHERE company_name = :name AND is_blacklisted = 1)")
    suspend fun isBlacklisted(name: String): Boolean

    @Query("SELECT * FROM companies WHERE is_blacklisted = 1 ORDER BY updated_at DESC")
    suspend fun getAllBlacklist(): List<CompanyEntity>

    @Query("UPDATE companies SET is_blacklisted = :isBlacklisted, blacklist_reason = :reason, updated_at = :updatedAt WHERE company_name = :name")
    suspend fun setBlacklistStatus(
        name: String,
        isBlacklisted: Boolean,
        reason: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM companies WHERE company_name = :name")
    suspend fun deleteByName(name: String)
}
