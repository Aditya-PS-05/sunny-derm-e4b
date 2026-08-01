package com.sunny.skin.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.sunny.skin.data.crypto.CryptoManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [ScanEntity::class, ObservationEntity::class],
    version = 3,
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
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                        .build()
                        .also { instance = it }
                }
            }

        /** Enable overwrite-on-delete before a bulk wipe. */
        fun enableSecureDelete(context: Context) {
            get(context).openHelper.writableDatabase.execSQL("PRAGMA secure_delete = ON")
        }

        /** Remove free pages and truncate the WAL after the rows have been deleted. */
        fun purgeDeletedPages(context: Context) {
            val db = get(context).openHelper.writableDatabase
            db.query("PRAGMA wal_checkpoint(FULL)").use { while (it.moveToNext()) Unit }
            db.execSQL("VACUUM")
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { while (it.moveToNext()) Unit }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scans ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE observations ADD COLUMN approximateSizeMm REAL")
                db.execSQL("ALTER TABLE observations ADD COLUMN sizeReferenceMm REAL")
                db.execSQL("ALTER TABLE observations ADD COLUMN sizeReferenceSpan REAL")
                db.execSQL("ALTER TABLE observations ADD COLUMN sizeTargetSpan REAL")
                db.execSQL("ALTER TABLE observations ADD COLUMN alignmentScore REAL")
                db.execSQL("ALTER TABLE observations ADD COLUMN alignmentTranslationX REAL")
                db.execSQL("ALTER TABLE observations ADD COLUMN alignmentTranslationY REAL")
                db.execSQL("ALTER TABLE observations ADD COLUMN alignmentScale REAL")
                db.execSQL("ALTER TABLE observations ADD COLUMN alignmentRotationDegrees REAL")
            }
        }
    }
}
