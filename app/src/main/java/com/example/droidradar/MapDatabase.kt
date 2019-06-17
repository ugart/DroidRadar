package com.example.droidradar

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

@Database(entities = [Map::class], version = 2)
abstract class MapDatabase : RoomDatabase() {

    abstract fun daoMap(): DAOMap

    companion object {
        @Volatile
        private var instance: MapDatabase? = null //@Volatile dá acesso dessa property instance à todas as threads

        @Synchronized
        fun getDatabaseInstance(context: Context): MapDatabase = instance ?: buildDatabase(context).also { instance = it }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context.applicationContext, MapDatabase::class.java, "2mapdatabase")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)

                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                    }
                })
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()

        //método para popular o banco de dados com os dados vindos do meu arquivo csv
        fun databasePopulating(context: Context, database: MapDatabase) {
            var mapFile: InputStream = context.resources.openRawResource(R.raw.maparadar)
            var arrayMapa: ArrayList<Map> = ArrayList()
            var maximumSpeed = ""

            //Como o InputStream não é um reader, ele precisa ser transformado em um
            val inputStreamReader = InputStreamReader(mapFile, Charsets.UTF_8)

            //Para garantir que o bufferedReader será fechado corretamente, funcionando como o try-with-resources funcionava no java, usa-se o "use"
            //evita o bloco finally
            BufferedReader(inputStreamReader).use { bufferedReader ->
                bufferedReader.lineSequence().forEach {

                    val breakCoordinates = it.split(",")

                    val longitude = breakCoordinates[0].trim()
                    val latitude = breakCoordinates[1].trim()

                    val breakSpeedAndRadarType = breakCoordinates[2].split("-")

                    val radarType = breakSpeedAndRadarType[0].trim()

                    val breakSpeed = breakSpeedAndRadarType[1].split("@")

                    maximumSpeed = breakSpeed[1]
//                    when {
//                        breakSpeed[1] != "0" -> maximumSpeed = breakSpeed[0].trim()
//                    }

                    val map = Map()
                    map.latitude = latitude
                    map.longitude = longitude
                    map.radarType = radarType
                    map.speed = maximumSpeed

                    arrayMapa.add(map)

                }
            }

            database.daoMap().saveMap(arrayMapa)


        }

    }

}