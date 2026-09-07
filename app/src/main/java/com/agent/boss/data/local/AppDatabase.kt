package com.agent.boss.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.agent.boss.data.local.dao.CompanyDao
import com.agent.boss.data.local.dao.JobDao
import com.agent.boss.data.local.dao.LLMAuditDao
import com.agent.boss.data.local.entity.CompanyEntity
import com.agent.boss.data.local.entity.JobEntity
import com.agent.boss.data.local.entity.LLMAuditLogEntity

@Database(
    entities = [
        JobEntity::class,
        CompanyEntity::class,
        LLMAuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun jobDao(): JobDao
    abstract fun companyDao(): CompanyDao
    abstract fun llmAuditDao(): LLMAuditDao

    companion object {
        private const val DB_NAME = "boss_agent.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
