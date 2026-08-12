package com.example.sld
import com.example.data.*
import com.example.engine.*
import org.junit.Test
class DebugAtsTest {
    @Test
    fun debug() {
        val circuits = listOf(
            CircuitEntity(id = 10, projectId = "1", circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 20, projectId = "1", circuitName = "MDB-Emergency", fedFrom = "ATS-01", transformerId = "TX-01", panelType = "MDB")
        )
        val n = "circ_20"
        val fedFromAts = circuits.any { c -> "circ_${c.id}" == n && c.fedFrom.lowercase().contains("ats") }
        println("Is fed from ATS? $fedFromAts")
    }
}
