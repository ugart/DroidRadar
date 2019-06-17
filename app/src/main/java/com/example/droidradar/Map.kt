package com.example.droidradar

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class Map(
    @PrimaryKey var id: Long? = null,
    @ColumnInfo(name = "latitude")
    var latitude: String? = null,
    @ColumnInfo(name = "longitude")
    var longitude: String? = null,
    @ColumnInfo(name = "objeto")
    var radarType: String? = null,
    @ColumnInfo(name = "velocidade")
    var speed: String? = null
)