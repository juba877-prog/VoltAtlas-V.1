package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "sld_node_positions",
    primaryKeys = ["projectId", "nodeId"],
    indices = [Index(value = ["projectId"])]
)
data class SldNodePositionEntity(
    @ColumnInfo(defaultValue = "'DEFAULT_PROJECT'")
    val projectId: String = "DEFAULT_PROJECT",
    val nodeId: String,
    val x: Float,
    val y: Float
)
