package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk=[36])
class RabbitryMigrationTest {
    @Test fun actualVersion37UpgradesWithoutInventingOrLosingOccupants()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="rabbitry-migration-${UUID.randomUUID()}.db"
        val schema=sequenceOf(File("../core/database/schemas/com.farmos.core.database.FarmOsDatabase/37.json"),File("core/database/schemas/com.farmos.core.database.FarmOsDatabase/37.json")).first{it.isFile}
        val database=JSONObject(schema.readText()).getJSONObject("database")
        val entities=database.getJSONArray("entities")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object:SupportSQLiteOpenHelper.Callback(37) {
            override fun onCreate(db:SupportSQLiteDatabase) {
                for(i in 0 until entities.length()) {
                    val e=entities.getJSONObject(i);val table=e.getString("tableName")
                    db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                    val indices=e.optJSONArray("indices")
                    if(indices!=null) for(j in 0 until indices.length())db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
                }
                val views=database.optJSONArray("views")
                if(views!=null) for(i in 0 until views.length())db.execSQL(views.getJSONObject(i).getString("createSql"))
                val setup=database.getJSONArray("setupQueries")
                for(i in 0 until setup.length())db.execSQL(setup.getString(i))
                db.execSQL("INSERT INTO rabbit_cages (id,farmId,code,doeCapacity) VALUES ('old-cage','farm','A',4)")
                db.execSQL("INSERT INTO rabbit_nest_boxes (id,farmId,cageId,code,status) VALUES ('old-box','farm','old-cage','A1','dirty')")
            }
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Only the actual Room migration may upgrade this fixture")
        }).build())
        helper.writableDatabase.close();helper.close()
        val room=Room.databaseBuilder(context,FarmOsDatabase::class.java,name).addMigrations(*FarmOsDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            assertEquals(38,room.openHelper.writableDatabase.version)
            assertEquals("A",room.rabbitry().cage("farm","old-cage")!!.code)
            assertEquals("dirty",room.rabbitry().box("farm","old-box")!!.status)
            assertTrue(room.rabbitry().housing("farm").isEmpty())
            assertTrue(room.rabbitry().services("farm").isEmpty())
            assertTrue(room.rabbitry().matings("farm").isEmpty())
            assertTrue(room.rabbitry().assignments("farm").isEmpty())
            val cursor=room.openHelper.writableDatabase.query("PRAGMA foreign_key_check")
            cursor.use{assertFalse(it.moveToFirst())}
        } finally {room.close();context.deleteDatabase(name)}
    }
}
