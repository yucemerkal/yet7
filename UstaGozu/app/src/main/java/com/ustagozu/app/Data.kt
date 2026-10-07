package com.ustagozu.app

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "records")
data class ServiceRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val customer: String,
    val address: String = "",
    val device: String,
    val fault: String,
    val solution: String,
    val price: String = "",
    val status: Int, // 0 çözüldü, 1 kısmen, 2 çözülmedi
    val date: Long = System.currentTimeMillis(),
    val shared: Boolean = false,
    val media: String = "" // dosya adları, "|" ile ayrılmış
) {
    fun mediaList(): List<String> = if (media.isEmpty()) emptyList() else media.split("|")
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE :q = '' OR customer LIKE '%' || :q || '%' OR address LIKE '%' || :q || '%' OR device LIKE '%' || :q || '%' OR fault LIKE '%' || :q || '%' OR solution LIKE '%' || :q || '%' ORDER BY date DESC")
    fun search(q: String): Flow<List<ServiceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: ServiceRecord)

    @Delete
    suspend fun delete(r: ServiceRecord)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE records ADD COLUMN address TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE records ADD COLUMN price TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE records ADD COLUMN media TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [ServiceRecord::class], version = 2, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): RecordDao

    companion object {
        @Volatile private var inst: Db? = null
        fun get(c: Context): Db = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, Db::class.java, "ustagozu.db")
                .addMigrations(MIGRATION_1_2).build().also { inst = it }
        }
    }
}
