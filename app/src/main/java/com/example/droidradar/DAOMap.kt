package com.example.droidradar

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DAOMap {

    @Insert
    fun saveMap(map: Map)

    @Query("SELECT * FROM map")
    fun listMaps(): List<Map>

}