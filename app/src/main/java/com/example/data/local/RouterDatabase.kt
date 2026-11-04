package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
  @Query("SELECT * FROM connected_accounts")
  fun getAllAccounts(): Flow<List<AccountEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAccounts(accounts: List<AccountEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAccount(account: AccountEntity)

  @Query("DELETE FROM connected_accounts WHERE id = :id")
  suspend fun deleteAccount(id: String)
}

@Database(entities = [AccountEntity::class], version = 1, exportSchema = false)
abstract class RouterDatabase : RoomDatabase() {
  abstract fun accountDao(): AccountDao

  companion object {
    @Volatile private var INSTANCE: RouterDatabase? = null

    fun getDatabase(context: Context): RouterDatabase {
      return INSTANCE
        ?: synchronized(this) {
          val instance =
            Room.databaseBuilder(
                context.applicationContext,
                RouterDatabase::class.java,
                "router_database"
              )
              .fallbackToDestructiveMigration()
              .build()
          INSTANCE = instance
          instance
        }
    }
  }
}
