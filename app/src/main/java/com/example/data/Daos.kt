package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access for [BusTieEntity] — Main-Tie-Main bus couplers between two
 * switchboards (Roadmap Phase 1 §1.1.4). All queries are strictly
 * project-scoped; board linkage is by explicit `CircuitEntity.id` values.
 */
@Dao
interface BusTieDao {

    @Query("SELECT * FROM bus_ties WHERE projectId = :projectId ORDER BY busTieId ASC")
    fun getBusTiesForProject(projectId: String): Flow<List<BusTieEntity>>

    @Query("SELECT * FROM bus_ties WHERE projectId = :projectId ORDER BY busTieId ASC")
    suspend fun getBusTiesForProjectSync(projectId: String): List<BusTieEntity>

    @Query("SELECT * FROM bus_ties ORDER BY busTieId ASC")
    suspend fun getAllBusTiesSync(): List<BusTieEntity>

    @Query("SELECT * FROM bus_ties WHERE projectId = :projectId AND busTieId = :busTieId LIMIT 1")
    suspend fun getBusTieById(projectId: String, busTieId: String): BusTieEntity?

    @Query("SELECT * FROM bus_ties WHERE projectId = :projectId AND (sourceBoardId = :boardId OR targetBoardId = :boardId)")
    suspend fun getBusTiesForBoard(projectId: String, boardId: Long): List<BusTieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBusTie(busTie: BusTieEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBusTies(busTies: List<BusTieEntity>)

    @Update
    suspend fun updateBusTie(busTie: BusTieEntity): Int

    @Delete
    suspend fun deleteBusTie(busTie: BusTieEntity)

    @Query("DELETE FROM bus_ties WHERE projectId = :projectId")
    suspend fun deleteBusTiesForProject(projectId: String)

    @Query("DELETE FROM bus_ties WHERE projectId = :projectId AND sourceBoardId = :deletedBoardId")
    suspend fun clearSourceSideForBoard(projectId: String, deletedBoardId: Long)

    @Query("DELETE FROM bus_ties WHERE projectId = :projectId AND targetBoardId = :deletedBoardId")
    suspend fun clearTargetSideForBoard(projectId: String, deletedBoardId: Long)

    /** Removes every tie touching [deletedBoardId] in one atomic transaction. */
    @Transaction
    suspend fun deleteBusTiesTouchingBoard(projectId: String, deletedBoardId: Long) {
        clearSourceSideForBoard(projectId, deletedBoardId)
        clearTargetSideForBoard(projectId, deletedBoardId)
    }
}
