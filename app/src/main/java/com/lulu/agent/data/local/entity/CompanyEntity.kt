package com.lulu.agent.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "companies",
    indices = [
        Index(value = ["company_name"], unique = true),
        Index(value = ["is_blacklisted"])
    ]
)
data class CompanyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "company_name")
    val companyName: String,

    @ColumnInfo(name = "is_blacklisted")
    val isBlacklisted: Boolean = false,

    @ColumnInfo(name = "is_outsourcing")
    val isOutsourcing: Boolean = false,

    @ColumnInfo(name = "financing_stage")
    val financingStage: String = "",

    @ColumnInfo(name = "scale")
    val scale: String = "",

    @ColumnInfo(name = "industry")
    val industry: String = "",

    @ColumnInfo(name = "blacklist_reason")
    val blacklistReason: String = "",

    @ColumnInfo(name = "notes")
    val notes: String = "",

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
