package com.ustagozu.app

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "records")
data class ServiceRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val customer: String,
    val device: String,
    val fault: String,
    val solution: String,
    val status: Int, // 0 çözüldü, 1 kısmen, 2 çözülmedi
    val date: Long = System.currentTimeMillis(),
    val shared: Boolean = false
)

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE :q = '' OR customer LIKE '%' || :q || '%' OR device LIKE '%' || :q || '%' OR fault LIKE '%' || :q || '%' OR solution LIKE '%' || :q || '%' ORDER BY date DESC")
    fun search(q: String): Flow<List<ServiceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: ServiceRecord)

    @Delete
    suspend fun delete(r: ServiceRecord)
}

@Database(entities = [ServiceRecord::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): RecordDao

    companion object {
        @Volatile private var inst: Db? = null
        fun get(c: Context): Db = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, Db::class.java, "ustagozu.db").build().also { inst = it }
        }
    }
}
