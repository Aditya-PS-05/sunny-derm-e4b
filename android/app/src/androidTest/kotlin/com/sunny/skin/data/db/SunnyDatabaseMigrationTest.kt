package com.sunny.skin.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SunnyDatabaseMigrationTest {
    @Test
    fun migrationOneToTwoAddsEmptyNotesColumn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE scans (id TEXT NOT NULL PRIMARY KEY)")
                    db.execSQL("INSERT INTO scans (id) VALUES ('existing')")
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        try {
            val db = helper.writableDatabase
            SunnyDatabase.MIGRATION_1_2.migrate(db)
            db.query("SELECT notes FROM scans WHERE id = 'existing'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("", cursor.getString(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(DB_NAME)
        }
    }

    @Test
    fun migrationTwoToThreeAddsOptionalCaptureMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE observations (id TEXT NOT NULL PRIMARY KEY)")
                    db.execSQL("INSERT INTO observations (id) VALUES ('existing')")
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        try {
            val db = helper.writableDatabase
            SunnyDatabase.MIGRATION_2_3.migrate(db)
            db.query(
                "SELECT approximateSizeMm, sizeReferenceMm, alignmentScore, " +
                    "alignmentRotationDegrees FROM observations WHERE id = 'existing'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
                assertEquals(true, cursor.isNull(1))
                assertEquals(true, cursor.isNull(2))
                assertEquals(true, cursor.isNull(3))
            }
        } finally {
            helper.close()
            context.deleteDatabase(DB_NAME)
        }
    }

    private companion object { const val DB_NAME = "sunny-migration-test.db" }
}
