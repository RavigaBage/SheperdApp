package com.example.notes.data

import androidx.room.*
import com.example.data.local.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "illustrations",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("categoryId")]
)
data class IllustrationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val bodyText: String,
    val categoryId: String?,
    val scriptureReference: String?,
    val source: String?,
    val imageUrl: String?,
    val isUserCreated: Boolean,
    val isHidden: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Dao
interface IllustrationDao {
    @Query("SELECT * FROM illustrations WHERE isHidden = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<IllustrationEntity>>

    @Query("""
        SELECT * FROM illustrations 
        WHERE isHidden = 0
        AND (:categoryId IS NULL OR categoryId = :categoryId)
        AND (title LIKE '%' || :query || '%' OR bodyText LIKE '%' || :query || '%')
        ORDER BY updatedAt DESC
    """)
    fun searchWithFilter(query: String, categoryId: String?): Flow<List<IllustrationEntity>>

    @Query("SELECT * FROM illustrations WHERE id = :id")
    suspend fun getById(id: String): IllustrationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(illustration: IllustrationEntity)

    @Delete
    suspend fun delete(illustration: IllustrationEntity)
}
