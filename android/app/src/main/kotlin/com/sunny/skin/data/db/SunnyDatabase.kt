package com.sunny.skin.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sunny.skin.data.crypto.CryptoManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [ScanEntity::class, ObservationEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class SunnyDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        @Volatile private var instance: SunnyDatabase? = null

        fun get(context: Context): SunnyDatabase =
            instance ?: synchronized(this) {
                instance ?: run {
                    val app = context.applicationContext
                    // SQLCipher-encrypted at rest with a Keystore-wrapped passphrase.
                    System.loadLibrary("sqlcipher")
                    Room.databaseBuilder(app, SunnyDatabase::class.java, "sunny.db")
                        .openHelperFactory(SupportOpenHelperFactory(CryptoManager.dbPassphrase(app)))
                        .build()
                        .also { instance = it }
                }
            }
    }
}
